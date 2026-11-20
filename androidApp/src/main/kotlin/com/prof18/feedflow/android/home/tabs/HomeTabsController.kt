package com.prof18.feedflow.android.home.tabs

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.prof18.feedflow.android.ui.home.components.flip.NextCategory
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.NavDrawerState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/**
 * Everything the home screen needs from the category tabs: which tabs exist, which one is open,
 * how tall the header is, the scroll-linked collapse, and the pager that slides between tabs.
 */
@Stable
internal class HomeTabsController(
    val tabs: ImmutableList<HomeTab>,
    val selectedIndex: Int,
    val collapse: HomeHeaderCollapseState,
    val pagerState: PagerState,
    private val isPaged: Boolean,
) {
    val showTabs: Boolean get() = tabs.isNotEmpty()
    val showFeedChips: Boolean get() = tabs.getOrNull(selectedIndex)?.hasFeedChips() == true

    /** Where the feed list starts below the compact tab bar and, when shown, the feed chips. */
    fun headerBottom(statusBarHeight: Dp, floatingToolbarBottom: Dp): Dp = when {
        !showTabs -> floatingToolbarBottom
        showFeedChips -> statusBarHeight + HomeTabBarHeight + HomeFeedChipsHeight
        else -> statusBarHeight + HomeTabBarHeight
    }

    // Flip pages fill the space below the header, so the chips stay put there.
    fun updateCollapseLimit(density: Density) = with(density) {
        collapse.updateLimit(if (showFeedChips && !isPaged) HomeFeedChipsHeight.toPx() else 0f)
    }

    /** Where the feed pager is between tabs (2.5 is halfway from the third tab to the fourth). */
    fun pagerPosition(): Float = pagerState.currentPage + pagerState.currentPageOffsetFraction
}

@Composable
internal fun rememberHomeTabsController(
    navDrawerState: NavDrawerState,
    currentFeedFilter: FeedFilter,
    isListAtTop: () -> Boolean,
    isPaged: Boolean = false,
): HomeTabsController {
    val tabs = remember(navDrawerState) { buildHomeTabs(navDrawerState) }
    val selectedIndex = remember(tabs, currentFeedFilter) { tabs.selectedTabIndex(currentFeedFilter) }
    val collapse = remember { HomeHeaderCollapseState() }
    val pagerState = rememberPagerState(initialPage = selectedIndex.coerceAtLeast(0)) { tabs.size }

    // A list that reaches its top (scroll-to-top, new filter) shows the whole header again.
    val atTop = isListAtTop()
    SideEffect(atTop, currentFeedFilter) {
        if (atTop) collapse.expand()
    }

    return HomeTabsController(
        tabs = tabs,
        selectedIndex = selectedIndex,
        collapse = collapse,
        pagerState = pagerState,
        isPaged = isPaged,
    )
}

/**
 * The tab strip layer of the home screen. Tapping the open tab goes back to the whole category
 * when a single feed is open; otherwise [onReload] decides (back to the top first, then reload).
 */
@Composable
internal fun HomeTabsLayer(
    controller: HomeTabsController,
    currentFeedFilter: FeedFilter,
    showCounts: Boolean,
    statusBarHeight: Dp,
    containerColor: Color,
    onFilterSelected: (FeedFilter) -> Unit,
    onReload: () -> Unit,
    navigationButton: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    if (!controller.showTabs) return
    HomeTabsOverlay(
        modifier = modifier,
        tabs = controller.tabs,
        selectedIndex = controller.selectedIndex,
        currentFeedFilter = currentFeedFilter,
        showCounts = showCounts,
        collapseState = controller.collapse,
        statusBarHeight = statusBarHeight,
        containerColor = containerColor,
        navigationButton = navigationButton,
        actions = actions,
        pagerPosition = controller::pagerPosition,
        onDialStep = { index -> scope.launch { controller.pagerState.animateScrollToPage(index) } },
        onFilterSelected = onFilterSelected,
        onSelectedTabClick = {
            if (currentFeedFilter is FeedFilter.Source) {
                onFilterSelected(controller.tabs[controller.selectedIndex].filter)
            } else {
                controller.collapse.expand()
                onReload()
            }
        },
    )
}

/**
 * What the Flip end page needs from the tabs: the open tab's name and a way to flip into the next
 * one. Null fields when there are no tabs or the open tab is the last.
 */
@Composable
internal fun HomeTabsController.flipTabContext(onFilterSelected: (FeedFilter) -> Unit): FlipTabContext {
    val currentTab = tabs.getOrNull(selectedIndex)
    val nextTab = tabs.stepFrom(selectedIndex, forward = true)?.let { tabs[it] }
    val nextName = nextTab?.label()
    return FlipTabContext(
        currentTabName = currentTab?.label(),
        nextCategory = if (nextTab != null && nextName != null) {
            NextCategory(name = nextName, open = { onFilterSelected(nextTab.filter) })
        } else {
            null
        },
    )
}

internal data class FlipTabContext(val currentTabName: String?, val nextCategory: NextCategory?)
