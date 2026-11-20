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
import com.prof18.feedflow.android.ui.home.components.position.FeedReloader
import com.prof18.feedflow.android.ui.home.components.position.SavedFeedPosition
import com.prof18.feedflow.android.ui.home.components.position.indexOfSaved
import com.prof18.feedflow.android.ui.home.components.position.rememberFeedPosition
import com.prof18.feedflow.android.ui.utils.LocalReduceMotion
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedItem
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.absoluteValue

// Fold geometry: half a turn per page, the leaf upright at the halfway point.
private const val FULL_TURN = 180f
private const val HALF = 0.5f
private const val FOLD_MAX_SHADE = 0.4f

// RenderNode measures camera distance in units of 72 px. Keeping the camera eight half-pages away
// gives a mostly flat turn: the leaf grows by under 15% at its steepest instead of rushing at
// the reader (a fixed distance zoomed it several times on tall screens).
private const val FOLD_CAMERA_HALF_PAGES = 8f
private const val RENDER_NODE_CAMERA_UNIT_PX = 72f

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
    feedReloader: FeedReloader?,
    topPadding: Dp,
    onItemsPagedPast: (Collection<String>) -> Unit,
    requestMoreItems: () -> Unit,
    currentTabName: String?,
    nextCategory: NextCategory?,
    modifier: Modifier = Modifier,
) {
    val currentFeedFilter = actions.currentFeedFilter
    val reloadGeneration = feedReloader?.generation ?: 0
    val isGroupedAsRead = rememberReadGrouping(currentFeedFilter, reloadGeneration)
    val pages = remember(feedItems, isGroupedAsRead, currentFeedFilter) {
        buildFlipPages(
            items = feedItems,
            isGroupedAsRead = isGroupedAsRead,
            mixSources = currentFeedFilter is FeedFilter.Timeline,
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    val isRestoringPlace = rememberFeedPosition(
        filter = currentFeedFilter,
        style = "flip",
        reloadGeneration = reloadGeneration,
        stories = feedItems,
        current = {
            pages.getOrNull(pagerState.settledPage)?.let { page ->
                SavedFeedPosition(key = page.key, itemIds = page.items.map { it.id })
            }
        },
        restore = { saved ->
            val index = pages.indexOfSaved(saved, keyOf = { it.key }, itemIdsOf = { page -> page.items.map { it.id } })
            if (index != null) pagerState.scrollToPage(index)
            index != null
        },
        scrollToTop = { pagerState.scrollToPage(0) },
    )
    FollowOpenTabTaps(feedReloader, pagerState)
    MarkPagesReadWhenPassed(pagerState, pages, isRestoring = { isRestoringPlace.value }, onItemsPagedPast)
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
            layer.cameraDistance = FOLD_CAMERA_HALF_PAGES * middle / RENDER_NODE_CAMERA_UNIT_PX
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

/** Pages flipped past have their stories marked read (once each); restoring a saved page does not count. */
@Composable
private fun MarkPagesReadWhenPassed(
    pagerState: PagerState,
    pages: List<FlipPage>,
    isRestoring: () -> Boolean,
    onItemsPagedPast: (Collection<String>) -> Unit,
) {
    val latestPages by rememberUpdatedState(pages)
    val latestIsRestoring by rememberUpdatedState(isRestoring)
    val latestCallback by rememberUpdatedState(onItemsPagedPast)
    LaunchedEffect(pagerState) {
        var furthest = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (latestIsRestoring()) {
                // Jumping back to a saved page is not reading the pages before it.
                furthest = settled
            } else if (settled > furthest) {
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

/**
 * Lets the first tap on the open tab bring the feed back to its first page, and tells the reloader
 * whether it is already there (then the next tap reloads).
 */
@Composable
private fun FollowOpenTabTaps(feedReloader: FeedReloader?, pagerState: PagerState) {
    if (feedReloader == null) return
    val isAtStart = pagerState.settledPage == 0
    SideEffect(isAtStart) { feedReloader.isPagedAtStart = isAtStart }
    LaunchedEffect(feedReloader, pagerState) {
        feedReloader.scrollToTopRequests.collect { pagerState.animateScrollToPage(0) }
    }
}
