package com.prof18.feedflow.android.ui.home.components.position

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.prof18.feedflow.core.model.FeedFilter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the reader was in a feed: the row (or page) at the top, by its stable key, plus the stories
 * on it, so the place can be found again after new stories arrive or read ones fold away.
 */
internal data class SavedFeedPosition(
    val key: String,
    val itemIds: List<String>,
    val offset: Int = 0,
)

/**
 * The reading position of each tab or feed, kept while the app runs. Each feed style lays stories
 * out differently, so positions are kept per style.
 */
internal object FeedPositionStore {
    private val positions = mutableMapOf<String, SavedFeedPosition>()

    fun get(filter: FeedFilter, style: String): SavedFeedPosition? = positions[storeKey(filter, style)]

    fun save(filter: FeedFilter, style: String, position: SavedFeedPosition) {
        positions[storeKey(filter, style)] = position
    }

    /** Forgets the place in [filter] for every style, so it opens at the top next time. */
    fun forget(filter: FeedFilter) {
        val prefix = "${filter.positionKey()}|"
        positions.keys.removeAll { it.startsWith(prefix) }
    }

    fun clear() = positions.clear()

    private fun storeKey(filter: FeedFilter, style: String) = "${filter.positionKey()}|$style"
}

/**
 * Which stories each feed shows folded as read. Decided once per story and kept until the reader
 * reloads that feed, so returning to a tab (or from an article) shows the same layout and the
 * saved place is still there.
 */
internal object ReadGroupingStore {
    private val decisions = mutableMapOf<String, HashMap<String, Boolean>>()

    fun decisionsFor(filter: FeedFilter): HashMap<String, Boolean> =
        decisions.getOrPut(filter.positionKey()) { HashMap() }

    fun forget(filter: FeedFilter) {
        decisions.remove(filter.positionKey())
    }
}

internal fun FeedFilter.positionKey(): String = when (this) {
    FeedFilter.Timeline -> "timeline"
    FeedFilter.Read -> "read"
    FeedFilter.Bookmarks -> "bookmarks"
    FeedFilter.Uncategorized -> "uncategorized"
    is FeedFilter.Category -> "category:${feedCategory.id}"
    is FeedFilter.Source -> "source:${feedSource.id}"
}

/**
 * Index of the row (or page) to return to: the saved one by key, else the first that still shows
 * one of its stories (rows regroup as stories are read), else null.
 */
internal fun <T> List<T>.indexOfSaved(
    saved: SavedFeedPosition,
    keyOf: (T) -> String,
    itemIdsOf: (T) -> List<String>,
): Int? {
    val byKey = indexOfFirst { keyOf(it) == saved.key }
    if (byKey >= 0) return byKey
    val byStory = indexOfFirst { row -> itemIdsOf(row).any { it in saved.itemIds } }
    return byStory.takeIf { it >= 0 }
}

/**
 * Keeps the reading position of the open feed: it is saved as the reader scrolls, and when the
 * feed opens again (another tab, or this screen coming back after reading an article) the saved
 * place is restored once that feed's stories are shown. Without a saved place the feed opens at
 * the top. A reload ([reloadGeneration] changing, with the place forgotten) starts from the top.
 *
 * A preview (a neighbouring tab drawn while sliding toward it, with [savesPosition] off) restores
 * the place but never saves one: it may show old or no stories, and its place would be wrong for
 * the real feed.
 *
 * Returns whether a place is being restored. The jump to it is not reading, so callers that mark
 * stories read as they pass ignore what scrolls by meanwhile.
 */
@Composable
internal fun rememberFeedPosition(
    filter: FeedFilter,
    style: String,
    reloadGeneration: Int,
    stories: Any,
    current: () -> SavedFeedPosition?,
    restore: suspend (SavedFeedPosition) -> Boolean,
    scrollToTop: suspend () -> Unit,
    savesPosition: Boolean = true,
): State<Boolean> {
    val latestSavesPosition by rememberUpdatedState(savesPosition)
    val isRestoring = remember { mutableStateOf(true) }
    val latestStories by rememberUpdatedState(stories)
    val latestCurrent by rememberUpdatedState(current)
    val latestRestore by rememberUpdatedState(restore)
    val latestScrollToTop by rememberUpdatedState(scrollToTop)
    val shownFilter = remember { arrayOfNulls<FeedFilter>(1) }
    LaunchedEffect(filter, style, reloadGeneration) {
        isRestoring.value = true
        // After a switch the list still shows the previous feed until the new stories arrive: wait
        // for them before restoring, and save nothing meanwhile. When the screen comes back (after
        // reading an article) its stories are already the right ones.
        val isSwitch = shownFilter[0] != null && shownFilter[0] != filter
        shownFilter[0] = filter
        if (isSwitch) {
            val storiesAtSwitch = latestStories
            withTimeoutOrNull(STORIES_WAIT_MILLIS) {
                snapshotFlow { latestStories }.first { it !== storiesAtSwitch }
            }
        }
        val saved = FeedPositionStore.get(filter, style)
        if (saved == null || !latestRestore(saved)) latestScrollToTop()
        isRestoring.value = false

        snapshotFlow { latestCurrent() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { position -> if (latestSavesPosition) FeedPositionStore.save(filter, style, position) }
    }
    return isRestoring
}

private const val STORIES_WAIT_MILLIS = 1_500L

/**
 * Reloading the open feed on purpose (tapping its tab, pulling to refresh): it forgets the saved
 * place and the folding of read stories, goes back to the top and refreshes. [generation] counts
 * reloads so the feed styles can start over.
 *
 * Tapping the open tab takes two steps: away from the top it first goes back there, and once at
 * the top it reloads. Paged styles report their own start through [isPagedFeed] and
 * [isPagedAtStart], and follow [scrollToTopRequests].
 */
@Stable
internal class FeedReloader(
    private val onScrollToTop: () -> Unit,
    private val onRefresh: () -> Unit,
) {
    var generation by mutableIntStateOf(0)
        private set

    var isListAtTop: Boolean = true
    var isPagedFeed: Boolean = false
    var isPagedAtStart: Boolean = true

    private val topRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopRequests: SharedFlow<Unit> = topRequests

    fun tapOpenTab(filter: FeedFilter) {
        val isAtTop = if (isPagedFeed) isPagedAtStart else isListAtTop
        if (isAtTop) {
            reload(filter)
        } else {
            onScrollToTop()
            topRequests.tryEmit(Unit)
        }
    }

    fun reload(filter: FeedFilter) {
        FeedPositionStore.forget(filter)
        ReadGroupingStore.forget(filter)
        generation++
        onScrollToTop()
        onRefresh()
    }
}

@Composable
internal fun rememberFeedReloader(onScrollToTop: () -> Unit, onRefresh: () -> Unit): FeedReloader {
    val latestScrollToTop by rememberUpdatedState(onScrollToTop)
    val latestRefresh by rememberUpdatedState(onRefresh)
    return remember { FeedReloader(onScrollToTop = { latestScrollToTop() }, onRefresh = { latestRefresh() }) }
}
