package com.prof18.feedflow.android.ui.home.components.magazine

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.prof18.feedflow.android.home.FeedStyle
import com.prof18.feedflow.android.home.rememberFeedStyle
import com.prof18.feedflow.android.home.tabs.FlipTabContext
import com.prof18.feedflow.android.ui.home.components.flip.FlipFeedPager
import com.prof18.feedflow.android.ui.home.components.list.FeedList
import com.prof18.feedflow.android.ui.home.components.position.FeedReloader
import com.prof18.feedflow.android.ui.home.components.position.SavedFeedPosition
import com.prof18.feedflow.android.ui.home.components.position.indexOfSaved
import com.prof18.feedflow.android.ui.home.components.position.rememberFeedPosition
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedFontSizes
import com.prof18.feedflow.core.model.FeedItem
import com.prof18.feedflow.core.model.FeedItemDisplaySettings
import com.prof18.feedflow.core.model.FeedItemId
import com.prof18.feedflow.core.model.FeedItemUrlInfo
import com.prof18.feedflow.core.model.FeedItemUrlTitle
import com.prof18.feedflow.core.model.FeedLayout
import com.prof18.feedflow.core.model.FeedSource
import com.prof18.feedflow.core.model.SwipeActions
import com.prof18.feedflow.core.model.VisibleFeedItem
import com.prof18.feedflow.shared.presentation.model.NextFeedDisplayState
import kotlinx.collections.immutable.ImmutableList

/** The home feed: Magazine or Flip for the "Big image" layout, the regular list for the others. */
@Suppress("LongParameterList")
@Composable
internal fun HomeFeedList(
    feedItems: ImmutableList<FeedItem>,
    feedReloader: FeedReloader?,
    nextFeedState: NextFeedDisplayState,
    feedFontSize: FeedFontSizes,
    feedLayout: FeedLayout,
    currentFeedFilter: FeedFilter,
    shareMenuLabel: String,
    shareCommentsMenuLabel: String,
    swipeActions: SwipeActions,
    onVisibleFeedItemsChanged: (List<VisibleFeedItem>) -> Unit,
    requestMoreItems: () -> Unit,
    onFeedItemClick: (FeedItemUrlInfo) -> Unit,
    onOpenInBrowser: (FeedItemUrlInfo) -> Unit,
    onBookmarkClick: (FeedItemId, Boolean) -> Unit,
    onReadStatusClick: (FeedItemId, Boolean) -> Unit,
    onCommentClick: (FeedItemUrlInfo) -> Unit,
    markAllAsRead: () -> Unit,
    onShareClick: (FeedItemUrlTitle) -> Unit,
    onOpenFeedSettings: (FeedSource) -> Unit,
    onOpenFeedWebsite: (String) -> Unit,
    onNavigateNext: () -> Unit,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    isGridLayoutEnabled: Boolean,
    onGridArrangementChanged: (Boolean) -> Unit,
    onMarkAllAboveAsRead: (String) -> Unit,
    onMarkAllBelowAsRead: (String) -> Unit,
    feedItemDisplaySettings: FeedItemDisplaySettings,
    revealArticleId: String?,
    onArticleRevealed: () -> Unit,
    onItemsPagedPast: (Collection<String>) -> Unit,
    onSourceClick: (FeedSource) -> Unit,
    flipTabContext: FlipTabContext,
    modifier: Modifier = Modifier,
) {
    val feedStyle = rememberFeedStyle(feedLayout)
    val reloadGeneration = feedReloader?.generation ?: 0
    // Only the open tab has a reloader; neighbouring tabs drawn while sliding are previews.
    val isPreview = feedReloader == null

    // Magazine and Flip are always a single column; the regular list reports its own arrangement.
    val latestOnGridArrangementChanged by rememberUpdatedState(onGridArrangementChanged)
    SideEffect(feedLayout) {
        if (feedLayout == FeedLayout.BIG_IMAGE) latestOnGridArrangementChanged(false)
    }

    val actions = remember(
        currentFeedFilter,
        feedFontSize,
        feedItemDisplaySettings,
        shareMenuLabel,
        shareCommentsMenuLabel,
        onFeedItemClick,
        onBookmarkClick,
        onReadStatusClick,
        onCommentClick,
        onShareClick,
        onOpenFeedSettings,
        onOpenFeedWebsite,
        onMarkAllAboveAsRead,
        onMarkAllBelowAsRead,
        onSourceClick,
    ) {
        MagazineStoryActions(
            currentFeedFilter = currentFeedFilter,
            feedFontSize = feedFontSize,
            displaySettings = feedItemDisplaySettings,
            shareMenuLabel = shareMenuLabel,
            shareCommentsMenuLabel = shareCommentsMenuLabel,
            onFeedItemClick = onFeedItemClick,
            onBookmarkClick = onBookmarkClick,
            onReadStatusClick = onReadStatusClick,
            onCommentClick = onCommentClick,
            onShareClick = onShareClick,
            onOpenFeedSettings = onOpenFeedSettings,
            onOpenFeedWebsite = onOpenFeedWebsite,
            onMarkAllAboveAsRead = onMarkAllAboveAsRead,
            onMarkAllBelowAsRead = onMarkAllBelowAsRead,
            onSourceClick = onSourceClick,
        )
    }
    when {
        feedLayout != FeedLayout.BIG_IMAGE -> {
            var isGridArrangement by remember { mutableStateOf(false) }
            val isRestoringPlace = rememberListFeedPosition(
                isPreview = isPreview,
                filter = currentFeedFilter,
                reloadGeneration = reloadGeneration,
                feedItems = feedItems,
                isGridArrangement = isGridArrangement,
                listState = listState,
                gridState = gridState,
            )
            FeedList(
                modifier = modifier,
                feedItems = feedItems,
                revealArticleId = revealArticleId,
                onArticleRevealed = onArticleRevealed,
                listState = listState,
                gridState = gridState,
                contentPadding = contentPadding,
                feedFontSize = feedFontSize,
                nextFeedState = nextFeedState,
                shareCommentsMenuLabel = shareCommentsMenuLabel,
                shareMenuLabel = shareMenuLabel,
                currentFeedFilter = currentFeedFilter,
                swipeActions = swipeActions,
                requestMoreItems = requestMoreItems,
                onGridArrangementChanged = { isGrid ->
                    isGridArrangement = isGrid
                    onGridArrangementChanged(isGrid)
                },
                onFeedItemClick = onFeedItemClick,
                onOpenInBrowser = onOpenInBrowser,
                onBookmarkClick = onBookmarkClick,
                onReadStatusClick = onReadStatusClick,
                onCommentClick = onCommentClick,
                onVisibleFeedItemsChanged = { visible ->
                    if (!isRestoringPlace.value) onVisibleFeedItemsChanged(visible)
                },
                markAllAsRead = markAllAsRead,
                onShareClick = onShareClick,
                onOpenFeedSettings = onOpenFeedSettings,
                onOpenFeedWebsite = onOpenFeedWebsite,
                feedLayout = feedLayout,
                isGridLayoutEnabled = isGridLayoutEnabled,
                onMarkAllAboveAsRead = onMarkAllAboveAsRead,
                onMarkAllBelowAsRead = onMarkAllBelowAsRead,
                onNavigateNext = onNavigateNext,
                feedItemDisplaySettings = feedItemDisplaySettings,
            )
        }

        feedStyle == FeedStyle.FLIP ->
            FlipFeedPager(
                modifier = modifier,
                feedItems = feedItems,
                actions = actions,
                feedReloader = feedReloader,
                topPadding = contentPadding.calculateTopPadding(),
                onItemsPagedPast = onItemsPagedPast,
                requestMoreItems = requestMoreItems,
                currentTabName = flipTabContext.currentTabName,
                nextCategory = flipTabContext.nextCategory,
            )

        else ->
            MagazineFeedList(
                modifier = modifier,
                feedItems = feedItems,
                actions = actions,
                reloadGeneration = reloadGeneration,
                isPreview = isPreview,
                nextFeedState = nextFeedState,
                listState = listState,
                contentPadding = contentPadding,
                onVisibleFeedItemsChanged = onVisibleFeedItemsChanged,
                requestMoreItems = requestMoreItems,
                markAllAsRead = markAllAsRead,
                onNavigateNext = onNavigateNext,
                revealArticleId = revealArticleId,
                onArticleRevealed = onArticleRevealed,
            )
    }
}

/** Keeps the place in List and Card styles by the article at the top of the list or grid. */
@Composable
private fun rememberListFeedPosition(
    isPreview: Boolean,
    filter: FeedFilter,
    reloadGeneration: Int,
    feedItems: ImmutableList<FeedItem>,
    isGridArrangement: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
): State<Boolean> =
    rememberFeedPosition(
        filter = filter,
        style = "list",
        reloadGeneration = reloadGeneration,
        savesPosition = !isPreview,
        stories = feedItems,
        current = {
            val index = if (isGridArrangement) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
            val offset = if (isGridArrangement) {
                gridState.firstVisibleItemScrollOffset
            } else {
                listState.firstVisibleItemScrollOffset
            }
            feedItems.getOrNull(index)?.let { item ->
                SavedFeedPosition(
                    key = item.id,
                    itemIds = feedItems.drop(index).take(NEARBY_ARTICLES).map { it.id },
                    offset = offset,
                )
            }
        },
        restore = { saved ->
            val index = feedItems.indexOfSaved(saved, keyOf = { it.id }, itemIdsOf = { listOf(it.id) })
            if (index != null) {
                val offset = if (feedItems[index].id == saved.key) saved.offset else 0
                if (isGridArrangement) gridState.scrollToItem(index, offset) else listState.scrollToItem(index, offset)
            }
            index != null
        },
        scrollToTop = {
            if (isGridArrangement) gridState.scrollToItem(0) else listState.scrollToItem(0)
        },
    )

// Articles remembered below the saved one, to find the place again if it is gone.
private const val NEARBY_ARTICLES = 4
