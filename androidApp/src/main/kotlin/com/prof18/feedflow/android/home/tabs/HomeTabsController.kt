package com.prof18.feedflow.android.home.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prof18.feedflow.android.ui.home.components.flip.NextCategory
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.NavDrawerState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val tabSwitchSlideDistance = 72.dp
private const val TAB_SWITCH_OUT_MILLIS = 120

// How long the new tab's stories may take to arrive before the list slides in anyway.
private const val TAB_SWITCH_WAIT_MILLIS = 400L

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

    /** Horizontal offset of the feed list while it slides in after a tab switch. */
    fun slideTranslationPx(density: Density, isRtl: Boolean): Float = with(density) {
        slide.value * (if (isRtl) -1f else 1f) * tabSwitchSlideDistance.toPx()
    }

    fun slideAlpha(): Float = 1f - kotlin.math.abs(slide.value).coerceAtMost(1f)
}

@Composable
internal fun rememberHomeTabsController(
    navDrawerState: NavDrawerState,
    currentFeedFilter: FeedFilter,
    reduceMotionEnabled: Boolean,
    isListAtTop: () -> Boolean,
    feedItems: Any? = null,
    isPaged: Boolean = false,
): HomeTabsController {
    val tabs = remember(navDrawerState) { buildHomeTabs(navDrawerState) }
    val selectedIndex = remember(tabs, currentFeedFilter) { tabs.selectedTabIndex(currentFeedFilter) }
    val collapse = remember { HomeHeaderCollapseState() }
    val slide = remember { Animatable(0f) }
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }

    val latestFeedItems by rememberUpdatedState(feedItems)

    // Switching tabs reads as moving along the strip: the old stories slide out and fade toward the
    // tab being left, and the new ones slide in from the side of the tab being opened once they
    // have loaded, so the old list never slides back in.
    LaunchedEffect(selectedIndex) {
        val from = previousIndex
        previousIndex = selectedIndex
        if (reduceMotionEnabled || from < 0 || selectedIndex < 0 || from == selectedIndex) {
            return@LaunchedEffect
        }
        val direction = if (selectedIndex > from) 1f else -1f
        val itemsBefore = latestFeedItems
        slide.animateTo(-direction, tween(durationMillis = TAB_SWITCH_OUT_MILLIS, easing = FastOutLinearInEasing))
        if (latestFeedItems === itemsBefore) {
            withTimeoutOrNull(TAB_SWITCH_WAIT_MILLIS) {
                snapshotFlow { latestFeedItems }.first { it !== itemsBefore }
            }
        }
        slide.snapTo(direction)
        slide.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
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
        isPaged = isPaged,
    )
}

/**
 * The tab strip layer of the home screen. Tapping the open tab goes back to the whole category
 * when a single feed is open, otherwise it reloads the tab: back to the top, refreshed, with read
 * stories folded away again.
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
