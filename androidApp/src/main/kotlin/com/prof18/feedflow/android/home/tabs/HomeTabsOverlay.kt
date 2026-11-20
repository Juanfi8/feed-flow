package com.prof18.feedflow.android.home.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prof18.feedflow.core.model.FeedFilter
import kotlinx.collections.immutable.ImmutableList

/**
 * Scroll-linked collapse of the feed chips. Scrolling the list down tucks the chips behind the tab
 * bar, which stays pinned; any upward scroll brings them back right where the reader is.
 */
@Stable
internal class HomeHeaderCollapseState {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    private var maxCollapsePx by mutableFloatStateOf(0f)

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            offsetPx = (offsetPx - available.y).coerceIn(0f, maxCollapsePx)
            return Offset.Zero
        }
    }

    val chipsAlpha: Float
        get() = if (maxCollapsePx <= 0f) 1f else 1f - (offsetPx / maxCollapsePx).coerceIn(0f, 1f)

    fun updateLimit(maxCollapsePx: Float) {
        this.maxCollapsePx = maxCollapsePx
        offsetPx = offsetPx.coerceIn(0f, maxCollapsePx)
    }

    fun expand() {
        offsetPx = 0f
    }
}

/**
 * The compact home header: one solid bar with the drawer button, the category tabs and the
 * actions, and the feed chips of the open tab below it. Nothing scrolls visibly behind it.
 */
@Composable
internal fun HomeTabsOverlay(
    tabs: ImmutableList<HomeTab>,
    selectedIndex: Int,
    currentFeedFilter: FeedFilter,
    showCounts: Boolean,
    collapseState: HomeHeaderCollapseState,
    statusBarHeight: Dp,
    containerColor: Color,
    onFilterSelected: (FeedFilter) -> Unit,
    onSelectedTabClick: () -> Unit,
    pagerPosition: () -> Float,
    onDialStep: (Int) -> Unit,
    navigationButton: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedTab = tabs.getOrNull(selectedIndex)
    val hasChips = selectedTab != null && selectedTab.hasFeedChips()
    Box(modifier = modifier.fillMaxWidth()) {
        if (selectedTab != null && hasChips) {
            Column(
                modifier = Modifier
                    .padding(top = statusBarHeight + HomeTabBarHeight)
                    .graphicsLayer { translationY = -collapseState.offsetPx }
                    .background(containerColor),
            ) {
                HomeFeedChips(
                    modifier = Modifier.graphicsLayer { alpha = collapseState.chipsAlpha },
                    tab = selectedTab,
                    selectedFeedId = currentFeedFilter.selectedFeedId(),
                    showCounts = showCounts,
                    onFilterSelected = onFilterSelected,
                )
                HeaderDivider()
            }
        }

        Column(modifier = Modifier.background(containerColor)) {
            Spacer(modifier = Modifier.height(statusBarHeight))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeTabBarHeight)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationButton()
                HomeTabStrip(
                    modifier = Modifier.weight(1f),
                    tabs = tabs,
                    selectedIndex = selectedIndex,
                    showCounts = showCounts,
                    onTabSelected = { index -> onFilterSelected(tabs[index].filter) },
                    onSelectedTabClick = onSelectedTabClick,
                    position = pagerPosition,
                    onDialStep = onDialStep,
                )
                actions()
            }
            if (!hasChips) HeaderDivider()
        }
    }
}

@Composable
private fun HeaderDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

internal fun HomeTab.hasFeedChips(): Boolean = filter !is FeedFilter.Timeline && feeds.isNotEmpty()
