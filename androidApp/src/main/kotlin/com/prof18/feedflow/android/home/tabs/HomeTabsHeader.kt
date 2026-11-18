package com.prof18.feedflow.android.home.tabs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.prof18.feedflow.android.R
import com.prof18.feedflow.android.ui.style.Spacing
import com.prof18.feedflow.android.ui.utils.LocalFeedFlowStrings
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedSource
import kotlinx.collections.immutable.ImmutableList

internal val HomeTabBarHeight = 56.dp
internal val HomeFeedChipsHeight = 48.dp

// How far a finger travels along the strip to move one tab.
private val tabDragStep = 64.dp

/**
 * Category tabs. Tap a tab to open it, tap the open tab to jump back to the top, or drag along the
 * strip to step through the tabs like a dial: the highlight follows the finger and the category
 * opens when the finger lifts.
 */
@Composable
internal fun HomeTabStrip(
    tabs: ImmutableList<HomeTab>,
    selectedIndex: Int,
    showCounts: Boolean,
    onTabSelected: (Int) -> Unit,
    onSelectedTabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var dragPreviewIndex by remember { mutableStateOf<Int?>(null) }
    val shownIndex = dragPreviewIndex ?: selectedIndex
    val tabBounds = remember { mutableStateMapOf<Int, IntRange>() }
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val lastIndex = tabs.lastIndex

    LaunchedEffect(shownIndex, scrollState.maxValue, tabBounds[shownIndex]) {
        val bounds = tabBounds[shownIndex] ?: return@LaunchedEffect
        val center = (bounds.first + bounds.last) / 2
        val target = (center - scrollState.viewportSize / 2).coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeTabBarHeight)
            .pointerInput(lastIndex, isRtl) {
                val stepPx = tabDragStep.toPx()
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulated = 0f
                        dragPreviewIndex = currentSelectedIndex.coerceAtLeast(0)
                    },
                    onDragEnd = {
                        val target = dragPreviewIndex
                        dragPreviewIndex = null
                        if (target != null && target != currentSelectedIndex) {
                            currentOnTabSelected(target)
                        }
                    },
                    onDragCancel = { dragPreviewIndex = null },
                ) { change, dragAmount ->
                    change.consume()
                    accumulated += if (isRtl) -dragAmount else dragAmount
                    var index = dragPreviewIndex ?: return@detectHorizontalDragGestures
                    val before = index
                    while (accumulated <= -stepPx) {
                        index = (index + 1).coerceAtMost(lastIndex)
                        accumulated += stepPx
                    }
                    while (accumulated >= stepPx) {
                        index = (index - 1).coerceAtLeast(0)
                        accumulated -= stepPx
                    }
                    if (index != before) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dragPreviewIndex = index
                    }
                }
            }
            .horizontalScroll(scrollState, enabled = false),
    ) {
        Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, tab ->
                HomeTabItem(
                    label = tab.label(),
                    unreadCount = tab.unreadCount.takeIf { showCounts && it > 0 },
                    selected = index == shownIndex,
                    onClick = {
                        if (index == selectedIndex) onSelectedTabClick() else onTabSelected(index)
                    },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val start = coordinates.positionInParent().x.toInt()
                        tabBounds[index] = start..(start + coordinates.size.width)
                    },
                )
            }
        }
        if (shownIndex >= 0) SlidingTabIndicator(tabBounds[shownIndex])
    }
}

private val TabHorizontalPadding = 12.dp
private val TabIndicatorHeight = 3.dp

/** One underline that glides between tabs, and follows the finger while dragging the strip. */
@Composable
private fun BoxScope.SlidingTabIndicator(bounds: IntRange?) {
    if (bounds == null) return
    val inset = with(LocalDensity.current) { TabHorizontalPadding.roundToPx() }
    val indicatorSpring = spring<Int>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    val start by animateIntAsState(
        targetValue = bounds.first + inset,
        animationSpec = indicatorSpring,
        label = "tabIndicatorStart",
    )
    val width by animateIntAsState(
        targetValue = bounds.last - bounds.first - 2 * inset,
        animationSpec = indicatorSpring,
        label = "tabIndicatorWidth",
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .offset { IntOffset(start, 0) }
            .layout { measurable, _ ->
                val placeable = measurable.measure(
                    Constraints.fixed(width.coerceAtLeast(0), TabIndicatorHeight.roundToPx()),
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun HomeTabItem(
    label: String,
    unreadCount: Long?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = if (unreadCount != null) {
        stringResource(R.string.home_tabs_unread_count, label, unreadCount.toInt())
    } else {
        label
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(IntrinsicSize.Max)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = description
            }
            .padding(horizontal = TabHorizontalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
            if (unreadCount != null) {
                Spacer(modifier = Modifier.width(Spacing.xsmall))
                Text(
                    text = unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** Feeds of the open tab: "All" keeps the whole category, a feed chip narrows to that feed. */
@Composable
internal fun HomeFeedChips(
    tab: HomeTab,
    selectedFeedId: String?,
    showCounts: Boolean,
    onFilterSelected: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(tab.filter, selectedFeedId) {
        val selectedPosition = if (selectedFeedId == null) {
            0
        } else {
            tab.feeds.indexOfFirst { it.feedSource.id == selectedFeedId } + 1
        }
        val isVisible = listState.layoutInfo.visibleItemsInfo.any { info ->
            info.index == selectedPosition &&
                info.offset >= 0 &&
                info.offset + info.size <= listState.layoutInfo.viewportEndOffset
        }
        if (selectedPosition >= 0 && !isVisible) {
            listState.animateScrollToItem((selectedPosition - 1).coerceAtLeast(0))
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeFeedChipsHeight),
        state = listState,
        contentPadding = PaddingValues(horizontal = Spacing.regular),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedFeedId == null,
                onClick = { onFilterSelected(tab.filter) },
                label = { Text(stringResource(R.string.home_tabs_all_in_tab)) },
            )
        }
        items(tab.feeds, key = { it.feedSource.id }) { item ->
            FeedChip(
                feedSource = item.feedSource,
                unreadCount = item.unreadCount.takeIf { showCounts && it > 0 },
                selected = item.feedSource.id == selectedFeedId,
                onClick = { onFilterSelected(FeedFilter.Source(item.feedSource)) },
            )
        }
    }
}

@Composable
private fun FeedChip(
    feedSource: FeedSource,
    unreadCount: Long?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = if (unreadCount != null) "${feedSource.title} · $unreadCount" else feedSource.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
@ReadOnlyComposable
internal fun HomeTab.label(): String = when {
    title != null -> title
    filter is FeedFilter.Timeline -> stringResource(R.string.home_tabs_all_feeds)
    else -> LocalFeedFlowStrings.current.noCategory
}
