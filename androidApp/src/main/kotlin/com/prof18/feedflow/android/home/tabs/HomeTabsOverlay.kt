package com.prof18.feedflow.android.home.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp
import com.prof18.feedflow.core.model.FeedFilter
import kotlinx.collections.immutable.ImmutableList

/**
 * Scroll-linked collapse of the home header. Scrolling the list down first slides the floating
 * toolbar away (the tab strip rises to the status bar), then tucks the feed chips behind the tabs.
 * Any upward scroll brings it back right where the reader is, without jumping to the top.
 */
@Stable
internal class HomeHeaderCollapseState {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    private var toolbarCollapsePx by mutableFloatStateOf(0f)
    private var chipsCollapsePx by mutableFloatStateOf(0f)

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            offsetPx = (offsetPx - available.y).coerceIn(0f, toolbarCollapsePx + chipsCollapsePx)
            return Offset.Zero
        }
    }

    /** Toolbar travel, clamped to the toolbar's own collapse distance. */
    val toolbarOffsetPx: Float get() = offsetPx.coerceAtMost(toolbarCollapsePx)

    val toolbarAlpha: Float get() = if (toolbarCollapsePx <= 0f) 1f else 1f - toolbarOffsetPx / toolbarCollapsePx

    val chipsAlpha: Float
        get() = if (chipsCollapsePx <= 0f) {
            1f
        } else {
            1f - ((offsetPx - toolbarCollapsePx) / chipsCollapsePx).coerceIn(0f, 1f)
        }

    fun updateLimits(toolbarCollapsePx: Float, chipsCollapsePx: Float) {
        this.toolbarCollapsePx = toolbarCollapsePx
        this.chipsCollapsePx = chipsCollapsePx
        offsetPx = offsetPx.coerceIn(0f, toolbarCollapsePx + chipsCollapsePx)
    }

    fun expand() {
        offsetPx = 0f
    }
}

/**
 * The tab strip and the feed chips of the open tab, drawn over the top of the feed list. The
 * status bar keeps a solid backdrop so articles never scroll visibly behind it.
 */
@Composable
internal fun HomeTabsOverlay(
    tabs: ImmutableList<HomeTab>,
    selectedIndex: Int,
    currentFeedFilter: FeedFilter,
    showCounts: Boolean,
    collapseState: HomeHeaderCollapseState,
    statusBarHeight: Dp,
    tabsTop: Dp,
    containerColor: Color,
    onFilterSelected: (FeedFilter) -> Unit,
    onSelectedTabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedTab = tabs.getOrNull(selectedIndex)
    Box(modifier = modifier.fillMaxWidth()) {
        if (selectedTab != null && selectedTab.hasFeedChips()) {
            HomeFeedChips(
                modifier = Modifier
                    .padding(top = tabsTop + HomeTabStripHeight)
                    .graphicsLayer {
                        translationY = -collapseState.offsetPx
                        alpha = collapseState.chipsAlpha
                    }
                    .background(containerColor),
                tab = selectedTab,
                selectedFeedId = currentFeedFilter.selectedFeedId(),
                showCounts = showCounts,
                onFilterSelected = onFilterSelected,
            )
        }

        HomeTabStrip(
            modifier = Modifier
                .padding(top = tabsTop)
                .graphicsLayer { translationY = -collapseState.toolbarOffsetPx }
                .background(containerColor),
            tabs = tabs,
            selectedIndex = selectedIndex,
            showCounts = showCounts,
            onTabSelected = { index -> onFilterSelected(tabs[index].filter) },
            onSelectedTabClick = onSelectedTabClick,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarHeight)
                .background(containerColor),
        )
    }
}

internal fun HomeTab.hasFeedChips(): Boolean = filter !is FeedFilter.Timeline && feeds.isNotEmpty()
