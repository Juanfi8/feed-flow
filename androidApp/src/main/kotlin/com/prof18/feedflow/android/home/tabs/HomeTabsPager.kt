package com.prof18.feedflow.android.home.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.prof18.feedflow.android.ui.home.components.position.positionKey
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The feed area as pages side by side, one per tab, that follow the finger: dragging the feed (or
 * tapping a tab) slides to the neighbouring tab, which opens when the slide settles.
 *
 * Only the open tab shows live stories. A neighbour shows the stories it showed last time, at its
 * saved place, until it is open and its own stories have loaded, so the slide never shows an empty
 * page or the previous tab's stories. Without tabs, or for filters outside them (Read, Bookmarks),
 * the feed is shown on its own.
 */
@Composable
internal fun HomeTabsPager(
    controller: HomeTabsController,
    currentFeedFilter: FeedFilter,
    feedItems: ImmutableList<FeedItem>,
    liveListState: LazyListState,
    liveGridState: LazyStaggeredGridState,
    onFilterSelected: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable (filter: FeedFilter, stories: ImmutableList<FeedItem>, scope: FeedPageScope) -> Unit,
) {
    val liveScope = FeedPageScope(isLive = true, listState = liveListState, gridState = liveGridState)
    if (!controller.showTabs || controller.selectedIndex < 0) {
        Box(modifier = modifier) { page(currentFeedFilter, feedItems, liveScope) }
    } else {
        TabPages(
            controller = controller,
            currentFeedFilter = currentFeedFilter,
            feedItems = feedItems,
            liveScope = liveScope,
            onFilterSelected = onFilterSelected,
            page = page,
            modifier = modifier,
        )
    }
}

// A page is redrawn from scratch when it turns live or back to a preview (see key(isLive)), so
// reusing the page slot across the two branches is intended: each needs its own state anyway.
@Suppress("ContentSlotReused")
@Composable
private fun TabPages(
    controller: HomeTabsController,
    currentFeedFilter: FeedFilter,
    feedItems: ImmutableList<FeedItem>,
    liveScope: FeedPageScope,
    onFilterSelected: (FeedFilter) -> Unit,
    page: @Composable (filter: FeedFilter, stories: ImmutableList<FeedItem>, scope: FeedPageScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val liveFilter = rememberLiveFilter(currentFeedFilter, feedItems)
    SideEffect(liveFilter, feedItems) {
        if (liveFilter == currentFeedFilter) TabStoriesCache.put(currentFeedFilter, feedItems)
    }
    SyncPagerWithSelectedTab(controller, onFilterSelected)

    HorizontalPager(
        modifier = modifier,
        state = controller.pagerState,
        key = { index -> controller.tabs.getOrNull(index)?.filter?.positionKey() ?: index },
    ) { index ->
        val tab = controller.tabs.getOrNull(index) ?: return@HorizontalPager
        val isLive = controller.tabs.selectedTabIndex(liveFilter) == index
        // A page turning live (or back to a preview) starts over, so it restores its saved place
        // with the right list state.
        key(isLive) {
            if (isLive) {
                page(currentFeedFilter, feedItems, liveScope)
            } else {
                val previewScope = FeedPageScope(
                    isLive = false,
                    listState = rememberLazyListState(),
                    gridState = rememberLazyStaggeredGridState(),
                )
                page(tab.filter, TabStoriesCache.get(tab.filter) ?: persistentListOf(), previewScope)
            }
        }
    }
}

/** The list state a page draws with, and whether it is the open tab (only that one is live). */
@Immutable
internal data class FeedPageScope(
    val isLive: Boolean,
    val listState: LazyListState,
    val gridState: LazyStaggeredGridState,
) {
    /** [live] on the open tab; [preview] on a neighbour shown while sliding. */
    fun <T> liveOr(live: T, preview: T): T = if (isLive) live else preview
}

/**
 * The filter whose stories [feedItems] currently holds: after a switch, the previous one until the
 * new stories arrive (or a short wait passes).
 */
@Composable
private fun rememberLiveFilter(currentFeedFilter: FeedFilter, feedItems: ImmutableList<FeedItem>): FeedFilter {
    var liveFilter by remember { mutableStateOf(currentFeedFilter) }
    val latestItems by rememberUpdatedState(feedItems)
    LaunchedEffect(currentFeedFilter) {
        if (liveFilter == currentFeedFilter) return@LaunchedEffect
        val itemsAtSwitch = latestItems
        withTimeoutOrNull(STORIES_WAIT_MILLIS) {
            snapshotFlow { latestItems }.first { it !== itemsAtSwitch }
        }
        liveFilter = currentFeedFilter
    }
    return liveFilter
}

/**
 * A settled slide opens its tab; a tab opened elsewhere (tap, strip drag, drawer) slides the pager
 * there. When the pager first appears it jumps straight to the open tab.
 */
@Composable
private fun SyncPagerWithSelectedTab(controller: HomeTabsController, onFilterSelected: (FeedFilter) -> Unit) {
    val latestController by rememberUpdatedState(controller)
    val latestOnFilterSelected by rememberUpdatedState(onFilterSelected)
    val hasSynced = remember { booleanArrayOf(false) }
    LaunchedEffect(controller.pagerState) {
        snapshotFlow { controller.pagerState.settledPage }.drop(1).collect { settled ->
            val tabs = latestController.tabs
            if (settled != latestController.selectedIndex && settled in tabs.indices) {
                latestOnFilterSelected(tabs[settled].filter)
            }
        }
    }
    LaunchedEffect(controller.selectedIndex) {
        val target = controller.selectedIndex
        if (target >= 0 && controller.pagerState.currentPage != target) {
            if (hasSynced[0]) {
                controller.pagerState.animateScrollToPage(target)
            } else {
                controller.pagerState.scrollToPage(target)
            }
        }
        hasSynced[0] = true
    }
}

/** The stories each tab showed last, to draw it while sliding toward it. Kept while the app runs. */
internal object TabStoriesCache {
    private val stories = mutableMapOf<String, ImmutableList<FeedItem>>()

    fun get(filter: FeedFilter): ImmutableList<FeedItem>? = stories[filter.positionKey()]

    fun put(filter: FeedFilter, items: ImmutableList<FeedItem>) {
        stories[filter.positionKey()] = items
    }
}

private const val STORIES_WAIT_MILLIS = 1_500L
