package com.prof18.feedflow.android.home.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.NavDrawerState
import kotlinx.collections.immutable.ImmutableList

private val tabStripGap = 4.dp
private val tabSwitchSlideDistance = 48.dp
private const val TAB_SWITCH_FADE = 0.6f

/**
 * Everything the home screen needs from the category tabs: which tabs exist, which one is open,
 * how tall the header is, and the scroll-linked collapse and tab-switch slide.
 */
@Stable
internal class HomeTabsController(
    val tabs: ImmutableList<HomeTab>,
    val selectedIndex: Int,
    val collapse: HomeHeaderCollapseState,
    private val slide: Animatable<Float, *>,
) {
    val showTabs: Boolean get() = tabs.isNotEmpty()
    val showFeedChips: Boolean get() = tabs.getOrNull(selectedIndex)?.hasFeedChips() == true

    fun tabsTop(toolbarBottom: Dp): Dp = toolbarBottom + tabStripGap

    /** Where the feed list starts: below the toolbar, plus the tab strip and chips when shown. */
    fun headerBottom(toolbarBottom: Dp): Dp = when {
        !showTabs -> toolbarBottom
        showFeedChips -> tabsTop(toolbarBottom) + HomeTabStripHeight + HomeFeedChipsHeight
        else -> tabsTop(toolbarBottom) + HomeTabStripHeight
    }

    fun updateCollapseLimits(density: Density, statusBarHeight: Dp, toolbarBottom: Dp) = with(density) {
        collapse.updateLimits(
            toolbarCollapsePx = if (showTabs) (tabsTop(toolbarBottom) - statusBarHeight).toPx() else 0f,
            chipsCollapsePx = if (showFeedChips) HomeFeedChipsHeight.toPx() else 0f,
        )
    }

    /** Horizontal offset of the feed list while it slides in after a tab switch. */
    fun slideTranslationPx(density: Density, isRtl: Boolean): Float = with(density) {
        slide.value * (if (isRtl) -1f else 1f) * tabSwitchSlideDistance.toPx()
    }

    fun slideAlpha(): Float = 1f - kotlin.math.abs(slide.value) * TAB_SWITCH_FADE
}

@Composable
internal fun rememberHomeTabsController(
    navDrawerState: NavDrawerState,
    currentFeedFilter: FeedFilter,
    reduceMotionEnabled: Boolean,
    isListAtTop: () -> Boolean,
): HomeTabsController {
    val tabs = remember(navDrawerState) { buildHomeTabs(navDrawerState) }
    val selectedIndex = remember(tabs, currentFeedFilter) { tabs.selectedTabIndex(currentFeedFilter) }
    val collapse = remember { HomeHeaderCollapseState() }
    val slide = remember { Animatable(0f) }
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }

    // Slide the list in from the side of the newly opened tab, so switching reads as moving along
    // the strip.
    LaunchedEffect(selectedIndex) {
        val from = previousIndex
        previousIndex = selectedIndex
        if (reduceMotionEnabled || from < 0 || selectedIndex < 0 || from == selectedIndex) {
            return@LaunchedEffect
        }
        slide.snapTo(if (selectedIndex > from) 1f else -1f)
        slide.animateTo(0f, tween(durationMillis = 250, easing = FastOutSlowInEasing))
    }

    // A list that reaches its top (scroll-to-top, new filter) shows the whole header again.
    val atTop = isListAtTop()
    SideEffect(atTop, currentFeedFilter) {
        if (atTop) collapse.expand()
    }

    return HomeTabsController(
        tabs = tabs,
        selectedIndex = selectedIndex,
        collapse = collapse,
        slide = slide,
    )
}

/**
 * The tab strip layer of the home screen. Tapping the open tab goes back to the whole category
 * when a single feed is open, otherwise it scrolls to the top.
 */
@Composable
internal fun HomeTabsLayer(
    controller: HomeTabsController,
    currentFeedFilter: FeedFilter,
    showCounts: Boolean,
    statusBarHeight: Dp,
    toolbarBottom: Dp,
    containerColor: Color,
    onFilterSelected: (FeedFilter) -> Unit,
    onScrollToTop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!controller.showTabs) return
    HomeTabsOverlay(
        modifier = modifier,
        tabs = controller.tabs,
        selectedIndex = controller.selectedIndex,
        currentFeedFilter = currentFeedFilter,
        showCounts = showCounts,
        collapseState = controller.collapse,
        statusBarHeight = statusBarHeight,
        tabsTop = controller.tabsTop(toolbarBottom),
        containerColor = containerColor,
        onFilterSelected = onFilterSelected,
        onSelectedTabClick = {
            if (currentFeedFilter is FeedFilter.Source) {
                onFilterSelected(controller.tabs[controller.selectedIndex].filter)
            } else {
                controller.collapse.expand()
                onScrollToTop()
            }
        },
    )
}
