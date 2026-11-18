package com.prof18.feedflow.android.ui.home.components.flip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.prof18.feedflow.android.ui.home.components.magazine.MagazineStoryActions
import com.prof18.feedflow.android.ui.home.components.magazine.rememberReadGrouping
import com.prof18.feedflow.android.ui.utils.LocalReduceMotion
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedItem
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.absoluteValue

// Fold geometry: half a turn per page, the leaf upright at the halfway point.
private const val FULL_TURN = 180f
private const val HALF = 0.5f
private const val FOLD_MAX_SHADE = 0.4f
private const val FOLD_CAMERA_DISTANCE = 18f

// Ask for more stories this many pages before the end.
private const val PAGINATION_PAGES_LEFT = 3

/**
 * The Flip style: one page at a time, turned vertically with a fold across the middle of the
 * page that follows the finger.
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
        val topHalf = rememberGraphicsLayer()
        val bottomHalf = rememberGraphicsLayer()
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The page being folded over is drawn above the one revealed underneath; the pager's
                // current page flips at the halfway point, exactly when the leaf passes upright.
                .zIndex(if (index == pagerState.currentPage) 1f else 0f)
                .then(
                    if (reduceMotion) Modifier else Modifier.foldTransition(pagerState, index, topHalf, bottomHalf),
                ),
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
 * Fold transition: pages turn like a sheet hinged across the middle. Both pages of a turn are held
 * in place (cancelling the pager's own slide). In the first half, the earlier page's lower half
 * lifts toward the reader over the next page's lower half; in the second half, the next page's
 * upper half comes down over the earlier page's upper half. Shading follows the leaf's angle.
 */
private fun Modifier.foldTransition(
    pagerState: PagerState,
    index: Int,
    topHalf: GraphicsLayer,
    bottomHalf: GraphicsLayer,
): Modifier = this
    .graphicsLayer {
        val offset = pagerState.offsetOf(index)
        translationY = offset * size.height
        alpha = if (offset.absoluteValue >= 1f) 0f else 1f
    }
    .drawWithContent {
        val offset = pagerState.offsetOf(index)
        if (offset == 0f || offset.absoluteValue >= 1f) {
            drawContent()
            return@drawWithContent
        }
        val middle = size.height / 2f
        val isEarlierPage = offset > 0f
        // 0 shows the earlier page, 1 the later one.
        val progress = if (isEarlierPage) offset else 1f + offset

        fun ContentDrawScope.staticHalf(top: Boolean, shade: Float) {
            clipRect(top = if (top) 0f else middle, bottom = if (top) middle else size.height) {
                this@staticHalf.drawContent()
                if (shade > 0f) drawRect(Color.Black, alpha = shade)
            }
        }

        fun ContentDrawScope.leaf(layer: GraphicsLayer, top: Boolean, angle: Float, shade: Float) {
            layer.record {
                clipRect(top = if (top) 0f else middle, bottom = if (top) middle else size.height) {
                    this@leaf.drawContent()
                    drawRect(Color.Black, alpha = shade)
                }
            }
            layer.pivotOffset = Offset(size.width / 2f, middle)
            layer.cameraDistance = FOLD_CAMERA_DISTANCE
            layer.rotationX = angle
            drawLayer(layer)
        }

        if (isEarlierPage) {
            staticHalf(top = true, shade = FOLD_MAX_SHADE * ((progress - HALF) / HALF).coerceAtLeast(0f))
            if (progress < HALF) {
                leaf(bottomHalf, top = false, angle = FULL_TURN * progress, shade = FOLD_MAX_SHADE * progress / HALF)
            }
        } else {
            staticHalf(top = false, shade = FOLD_MAX_SHADE * (1f - progress))
            if (progress > HALF) {
                leaf(
                    topHalf,
                    top = true,
                    angle = FULL_TURN * progress - FULL_TURN,
                    shade = FOLD_MAX_SHADE * (1f - progress) / HALF,
                )
            }
        }
    }

/** Distance of [index] from the settled position: positive above it, negative below. */
private fun PagerState.offsetOf(index: Int): Float = (currentPage - index) + currentPageOffsetFraction

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
