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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.prof18.feedflow.android.ui.home.components.list.FeedFooterButtons
import com.prof18.feedflow.android.ui.home.components.list.PullToNextIndicator
import com.prof18.feedflow.android.ui.home.components.list.PullToNextLayout
import com.prof18.feedflow.android.ui.home.components.position.ReadGroupingStore
import com.prof18.feedflow.android.ui.home.components.position.SavedFeedPosition
import com.prof18.feedflow.android.ui.home.components.position.indexOfSaved
import com.prof18.feedflow.android.ui.home.components.position.rememberFeedPosition
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
    reloadGeneration: Int,
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
    val isGroupedAsRead = rememberReadGrouping(currentFeedFilter, reloadGeneration)
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

    val isRestoringPlace = rememberFeedPosition(
        filter = currentFeedFilter,
        style = "magazine",
        reloadGeneration = reloadGeneration,
        stories = feedItems,
        current = { listState.currentMagazinePosition(blocks) },
        restore = { saved ->
            val index = blocks.indexOfSaved(saved, keyOf = { it.key }, itemIdsOf = { it.itemIds })
            if (index != null) {
                listState.scrollToItem(index, if (blocks[index].key == saved.key) saved.offset else 0)
            }
            index != null
        },
        scrollToTop = { listState.scrollToItem(0) },
    )
    RevealStoryEffect(revealArticleId, blocks, listState, onArticleRevealed)
    ReportVisibleStories(blocks, feedItems, listState) { visible ->
        if (!isRestoringPlace.value) onVisibleFeedItemsChanged(visible)
    }
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
 * Decides once per story whether it sits in its day's read group, so stories read while reading
 * dim in place instead of jumping away. The decisions last until the reader reloads the feed
 * (tapping its tab or pulling to refresh), which [reloadGeneration] counts; switching tabs or coming
 * back from an article keeps them. The Read and Bookmarks views show read stories on purpose, so
 * nothing is folded there.
 */
@Composable
internal fun rememberReadGrouping(currentFeedFilter: FeedFilter, reloadGeneration: Int): (FeedItem) -> Boolean {
    val foldsRead = currentFeedFilter !is FeedFilter.Read && currentFeedFilter !is FeedFilter.Bookmarks
    return remember(currentFeedFilter, reloadGeneration) {
        val decisions = ReadGroupingStore.decisionsFor(currentFeedFilter)
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

/** The row at the top of the magazine, with the stories it (or the next row) shows. */
private fun LazyListState.currentMagazinePosition(blocks: List<MagazineBlock>): SavedFeedPosition? {
    val index = firstVisibleItemIndex
    val block = blocks.getOrNull(index) ?: return null
    val nearbyStories = blocks.drop(index).asSequence().flatMap { it.itemIds }.take(NEARBY_STORIES).toList()
    return SavedFeedPosition(key = block.key, itemIds = nearbyStories, offset = firstVisibleItemScrollOffset)
}

// Stories remembered around the saved row, to find the place again if that row regroups.
private const val NEARBY_STORIES = 4
