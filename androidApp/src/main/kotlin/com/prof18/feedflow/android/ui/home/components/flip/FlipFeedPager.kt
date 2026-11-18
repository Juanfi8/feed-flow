package com.prof18.feedflow.android.ui.home.components.flip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prof18.feedflow.android.ui.home.components.magazine.MagazineStoryActions
import com.prof18.feedflow.android.ui.home.components.magazine.rememberReadGrouping
import com.prof18.feedflow.android.ui.utils.LocalReduceMotion
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedItem
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.absoluteValue

// How much a page shrinks and darkens as the next one slides over it.
private const val DEPTH_SCALE = 0.08f
private const val DEPTH_DIM = 0.45f
private val DepthCorner = 24.dp

// Ask for more stories this many pages before the end.
private const val PAGINATION_PAGES_LEFT = 3

/**
 * The Flip style: one page at a time, flipped vertically. The page being left stays put, sinks
 * back and dims while the next one slides up over it (and the reverse when flipping back).
 */
@Composable
internal fun FlipFeedPager(
    feedItems: ImmutableList<FeedItem>,
    actions: MagazineStoryActions,
    isRefreshing: Boolean,
    topPadding: Dp,
    onItemsPagedPast: (Collection<String>) -> Unit,
    requestMoreItems: () -> Unit,
    currentTabName: String?,
    nextCategory: NextCategory?,
    modifier: Modifier = Modifier,
) {
    val currentFeedFilter = actions.currentFeedFilter
    val isGroupedAsRead = rememberReadGrouping(currentFeedFilter, isRefreshing)
    val pages = remember(feedItems, isGroupedAsRead, currentFeedFilter) {
        buildFlipPages(
            items = feedItems,
            isGroupedAsRead = isGroupedAsRead,
            mixSources = currentFeedFilter is FeedFilter.Timeline,
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(currentFeedFilter) { pagerState.scrollToPage(0) }
    MarkPagesReadWhenPassed(pagerState, pages, onItemsPagedPast)
    PaginateNearLastPage(pagerState, pages.size, requestMoreItems)

    val reduceMotion = LocalReduceMotion.current
    VerticalPager(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding),
        state = pagerState,
        key = { index -> pages.getOrNull(index)?.key ?: index },
        beyondViewportPageCount = 1,
    ) { index ->
        val page = pages.getOrNull(index) ?: return@VerticalPager
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (reduceMotion) Modifier else Modifier.depthTransition(pagerState, index)),
        ) {
            FlipPageView(
                page = page,
                pageIndex = index,
                pageCount = pages.size,
                actions = actions,
                currentTabName = currentTabName,
                nextCategory = nextCategory,
            )
        }
    }
}

/**
 * Depth transition. A page with a positive offset is the one being covered: it is held in place
 * (cancelling the pager's own movement), scaled down, rounded and dimmed. The incoming page has
 * the higher index, so the pager already draws it above.
 */
private fun Modifier.depthTransition(pagerState: PagerState, index: Int): Modifier =
    this
        .graphicsLayer {
            val offset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
            if (offset > 0f) {
                val progress = offset.coerceIn(0f, 1f)
                translationY = progress * size.height
                val scale = 1f - DEPTH_SCALE * progress
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape(DepthCorner * progress)
                clip = true
            } else {
                val progress = offset.absoluteValue.coerceIn(0f, 1f)
                shape = RoundedCornerShape(DepthCorner * progress)
                clip = progress > 0f
            }
        }
        .drawWithContent {
            drawContent()
            val offset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
            if (offset > 0f) {
                drawRect(Color.Black, alpha = DEPTH_DIM * offset.coerceIn(0f, 1f))
            }
        }

/** Pages flipped past, in either order, have their stories marked read (once each). */
@Composable
private fun MarkPagesReadWhenPassed(
    pagerState: PagerState,
    pages: List<FlipPage>,
    onItemsPagedPast: (Collection<String>) -> Unit,
) {
    val latestPages by rememberUpdatedState(pages)
    val latestCallback by rememberUpdatedState(onItemsPagedPast)
    LaunchedEffect(pagerState) {
        var furthest = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (settled > furthest) {
                val passed = latestPages.subList(furthest.coerceAtLeast(0), settled.coerceAtMost(latestPages.size))
                    .flatMap { page -> page.items.map { it.id } }
                if (passed.isNotEmpty()) latestCallback(passed)
                furthest = settled
            } else if (settled == 0) {
                furthest = 0
            }
        }
    }
}

@Composable
private fun PaginateNearLastPage(pagerState: PagerState, pageCount: Int, requestMoreItems: () -> Unit) {
    val shouldPaginate by remember(pageCount) {
        derivedStateOf { pagerState.currentPage >= pageCount - PAGINATION_PAGES_LEFT }
    }
    val latestRequestMoreItems by rememberUpdatedState(requestMoreItems)
    SideEffect(shouldPaginate) {
        if (shouldPaginate) latestRequestMoreItems()
    }
}
