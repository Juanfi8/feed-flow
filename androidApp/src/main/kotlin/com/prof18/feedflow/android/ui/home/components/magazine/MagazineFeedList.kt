package com.prof18.feedflow.android.ui.home.components.magazine

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.prof18.feedflow.android.ui.home.components.list.FeedFooterButtons
import com.prof18.feedflow.android.ui.home.components.list.PullToNextIndicator
import com.prof18.feedflow.android.ui.home.components.list.PullToNextLayout
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedItem
import com.prof18.feedflow.core.model.VisibleFeedItem
import com.prof18.feedflow.shared.presentation.model.NextFeedDisplayState
import com.prof18.feedflow.shared.presentation.model.NextFeedDisplayState.NextFeedDisplayEnabledState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.ZoneId

// Ask for the next page this many rows before the end, like the plain list does.
private const val PAGINATION_THRESHOLD = 8

/**
 * The "Big image" feed as a magazine: day sections, a lead story, paired tiles, wide cards,
 * quick reads, and each day's read stories folded into one row at its end.
 */
@Composable
internal fun MagazineFeedList(
    feedItems: ImmutableList<FeedItem>,
    actions: MagazineStoryActions,
    isRefreshing: Boolean,
    nextFeedState: NextFeedDisplayState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onVisibleFeedItemsChanged: (List<VisibleFeedItem>) -> Unit,
    requestMoreItems: () -> Unit,
    markAllAsRead: () -> Unit,
    onNavigateNext: () -> Unit,
    modifier: Modifier = Modifier,
    revealArticleId: String? = null,
    onArticleRevealed: () -> Unit = {},
) {
    val currentFeedFilter = actions.currentFeedFilter
    val isGroupedAsRead = rememberReadGrouping(currentFeedFilter, isRefreshing)
    val readRowsShown = remember(currentFeedFilter) { mutableStateMapOf<Long, Int>() }
    val zoneId = remember { ZoneId.systemDefault() }
    val todayEpochDay = LocalDate.now(zoneId).toEpochDay()
    val readRowsSnapshot = readRowsShown.toMap()
    val blocks = remember(feedItems, isGroupedAsRead, readRowsSnapshot, todayEpochDay) {
        buildMagazineBlocks(
            items = feedItems,
            isGroupedAsRead = isGroupedAsRead,
            readRowsShown = { day -> readRowsSnapshot[day] ?: 0 },
            todayEpochDay = todayEpochDay,
            zoneId = zoneId,
        )
    }

    RevealStoryEffect(revealArticleId, blocks, listState, onArticleRevealed)
    ReportVisibleStories(blocks, feedItems, listState, onVisibleFeedItemsChanged)
    PaginateNearEnd(listState, requestMoreItems)

    PullToNextLayout(
        modifier = modifier,
        onNavigateNext = onNavigateNext,
        enabled = nextFeedState is NextFeedDisplayEnabledState,
        indicator = { progress ->
            PullToNextIndicator(
                progress = progress,
                title = (nextFeedState as? NextFeedDisplayEnabledState)?.title,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
            items(blocks, key = { it.key }, contentType = { it::class }) { block ->
                MagazineBlockView(
                    block = block,
                    actions = actions,
                    todayEpochDay = todayEpochDay,
                    onToggleReadGroup = { day ->
                        readRowsShown[day] = if ((readRowsShown[day] ?: 0) > 0) 0 else READ_ROWS_FIRST_PAGE
                    },
                    onShowMoreRead = { day ->
                        readRowsShown[day] = (readRowsShown[day] ?: 0) + READ_ROWS_PAGE
                    },
                )
            }
            item(key = "footer") {
                FeedFooterButtons(
                    currentFeedFilter = currentFeedFilter,
                    nextFeedState = nextFeedState,
                    markAllAsRead = markAllAsRead,
                    onNavigateNext = onNavigateNext,
                )
            }
        }
    }
}

@Composable
private fun MagazineBlockView(
    block: MagazineBlock,
    actions: MagazineStoryActions,
    todayEpochDay: Long,
    onToggleReadGroup: (Long) -> Unit,
    onShowMoreRead: (Long) -> Unit,
) {
    when (block) {
        is MagazineBlock.DayHeader -> MagazineDayHeader(block.epochDay, block.unreadCount, todayEpochDay)
        is MagazineBlock.Lead -> MagazineLead(block.item, actions)
        is MagazineBlock.Pair -> MagazinePair(block.first, block.second, actions)
        is MagazineBlock.Wide -> MagazineWide(block.item, actions)
        is MagazineBlock.TextCard -> MagazineTextCard(block.item, actions)
        is MagazineBlock.QuickReads -> MagazineQuickReads(block.items, actions)
        is MagazineBlock.ReadGroupHeader -> MagazineReadGroupHeader(
            count = block.count,
            isExpanded = block.isExpanded,
            onToggle = { onToggleReadGroup(block.epochDay) },
        )
        is MagazineBlock.ReadRow -> MagazineReadRow(block.item, block.isLast, actions)
        is MagazineBlock.ShowMoreRead -> MagazineShowMoreRead(
            remaining = block.remaining,
            onClick = { onShowMoreRead(block.epochDay) },
        )
    }
}

/**
 * Decides once per story whether it sits in its day's read group, so stories read while scrolling
 * dim in place instead of jumping away. The decision is taken again when the feed reloads: a new
 * tab or filter, a finished refresh, or coming back to the app. The Read and Bookmarks views show
 * read stories on purpose, so nothing is folded there.
 */
@Composable
private fun rememberReadGrouping(currentFeedFilter: FeedFilter, isRefreshing: Boolean): (FeedItem) -> Boolean {
    var generation by remember { mutableIntStateOf(0) }
    var wasRefreshing by remember { mutableStateOf(isRefreshing) }
    SideEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) generation++
        wasRefreshing = isRefreshing
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { generation++ }

    val foldsRead = currentFeedFilter !is FeedFilter.Read && currentFeedFilter !is FeedFilter.Bookmarks
    return remember(currentFeedFilter, generation) {
        val decisions = HashMap<String, Boolean>()
        val grouping: (FeedItem) -> Boolean = { item ->
            foldsRead && decisions.getOrPut(item.id) { item.isRead }
        }
        grouping
    }
}

/**
 * Reports the stories on screen in feed order, so mark-as-read-on-scroll works as in the plain
 * list: rows are mapped back to the stories they show.
 */
@Composable
private fun ReportVisibleStories(
    blocks: List<MagazineBlock>,
    feedItems: ImmutableList<FeedItem>,
    listState: LazyListState,
    onVisibleFeedItemsChanged: (List<VisibleFeedItem>) -> Unit,
) {
    val latestBlocks by rememberUpdatedState(blocks)
    val positions by rememberUpdatedState(
        remember(feedItems) { feedItems.withIndex().associate { (index, item) -> item.id to index } },
    )
    val latestCallback by rememberUpdatedState(onVisibleFeedItemsChanged)
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .sortedBy { it.offset }
                .flatMap { info -> latestBlocks.getOrNull(info.index)?.itemIds.orEmpty() }
                .mapNotNull { id -> positions[id]?.let { VisibleFeedItem(id = id, index = it) } }
        }
            .distinctUntilChanged()
            .collect { latestCallback(it) }
    }
}

@Composable
private fun PaginateNearEnd(listState: LazyListState, requestMoreItems: () -> Unit) {
    val shouldPaginate by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= listState.layoutInfo.totalItemsCount - PAGINATION_THRESHOLD
        }
    }
    val latestRequestMoreItems by rememberUpdatedState(requestMoreItems)
    SideEffect(shouldPaginate) {
        if (shouldPaginate) latestRequestMoreItems()
    }
}

@Composable
private fun RevealStoryEffect(
    revealArticleId: String?,
    blocks: List<MagazineBlock>,
    listState: LazyListState,
    onArticleRevealed: () -> Unit,
) {
    val latestOnArticleRevealed by rememberUpdatedState(onArticleRevealed)
    LaunchedEffect(revealArticleId, blocks) {
        val articleId = revealArticleId ?: return@LaunchedEffect
        val index = blocks.indexOfFirst { articleId in it.itemIds }
        if (index >= 0) {
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
            if (!isVisible) listState.scrollToItem(index)
            latestOnArticleRevealed()
        }
    }
}
