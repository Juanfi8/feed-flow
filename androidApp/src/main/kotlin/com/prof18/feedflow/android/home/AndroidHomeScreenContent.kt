package com.prof18.feedflow.android.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.prof18.feedflow.android.home.tabs.HomeTabsController
import com.prof18.feedflow.android.home.tabs.HomeTabsLayer
import com.prof18.feedflow.android.home.tabs.HomeTabsPager
import com.prof18.feedflow.android.home.tabs.flipTabContext
import com.prof18.feedflow.android.home.tabs.rememberHomeTabsController
import com.prof18.feedflow.android.ui.components.ConfirmationDialog
import com.prof18.feedflow.android.ui.home.FeedListActions
import com.prof18.feedflow.android.ui.home.FeedManagementActions
import com.prof18.feedflow.android.ui.home.ShareBehavior
import com.prof18.feedflow.android.ui.home.components.EmptyFeedView
import com.prof18.feedflow.android.ui.home.components.NoFeedsSourceView
import com.prof18.feedflow.android.ui.home.components.ScrollToTopButton
import com.prof18.feedflow.android.ui.home.components.list.FeedListMaxContentWidth
import com.prof18.feedflow.android.ui.home.components.magazine.HomeFeedList
import com.prof18.feedflow.android.ui.home.components.position.rememberFeedReloader
import com.prof18.feedflow.android.ui.style.Spacing
import com.prof18.feedflow.android.ui.utils.LocalFeedFlowStrings
import com.prof18.feedflow.android.ui.utils.LocalReduceMotion
import com.prof18.feedflow.android.ui.utils.scrollToItemConditionally
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedItemId
import com.prof18.feedflow.core.model.FeedLayout
import com.prof18.feedflow.core.model.FeedOrder
import com.prof18.feedflow.core.model.FeedSource
import com.prof18.feedflow.core.model.NoFeedSourcesStatus
import com.prof18.feedflow.shared.presentation.model.HomeDisplayState
import com.prof18.feedflow.shared.presentation.model.HomeViewMenuState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val listTopContentPadding = 4.dp
private val feedLoaderReservedHeight = 48.dp
private val floatingToolbarHeight = 64.dp
private val scrimFeather = 24.dp

@Composable
fun AndroidHomeScreenContent(
    displayState: HomeDisplayState,
    feedListActions: FeedListActions,
    feedManagementActions: FeedManagementActions,
    shareBehavior: ShareBehavior,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    onSearchClick: () -> Unit,
    viewMenuState: HomeViewMenuState,
    onFeedOrderChange: (FeedOrder) -> Unit,
    onShowReadArticlesTimelineChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    showDrawerMenu: Boolean = false,
    isDrawerOpen: Boolean = false,
    onDrawerMenuClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onEmptyStateClick: (() -> Unit)? = null,
    onNavigateToNextFeed: () -> Unit = { },
    revealArticleId: String? = null,
    onArticleRevealed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val reduceMotionEnabled = LocalReduceMotion.current
    val hasFeedItems = displayState.feedItems.isNotEmpty()
    val isShowingEmptyFeed = displayState.feedUpdateStatus !is NoFeedSourcesStatus &&
        !displayState.feedUpdateStatus.isLoading() &&
        !hasFeedItems
    var scrollToTopWhenItemsReturn by remember { mutableStateOf(isShowingEmptyFeed) }
    AcknowledgeRevealOnEmptyFeed(revealArticleId, isShowingEmptyFeed, onArticleRevealed)
    var isGridArrangement by remember { mutableStateOf(false) }
    val markAllReadWithConfirmation = rememberMarkAllReadWithConfirmation(
        feedFilter = displayState.currentFeedFilter,
        onMarkAllRead = feedListActions.markAllRead,
    )

    val scrollToTop: () -> Unit = {
        scope.launch {
            scrollFeedToTop(
                isGridArrangement = isGridArrangement,
                listState = listState,
                gridState = gridState,
                reduceMotionEnabled = reduceMotionEnabled,
            )
        }
    }
    // Each tab keeps its own place (see RememberFeedPosition), so switching does not scroll.
    val selectFilter: (FeedFilter) -> Unit = feedManagementActions.onFeedFilterSelected
    val feedReloader = rememberFeedReloader(onScrollToTop = scrollToTop, onRefresh = onRefresh)
    val reloadCurrentFeed: () -> Unit = { feedReloader.reload(displayState.currentFeedFilter) }
    val isPagedFeed = rememberFeedStyle(displayState.feedLayout).isPaged

    @Suppress("MagicNumber")
    val showScrollToTopButton by remember {
        derivedStateOf {
            shouldShowScrollToTopButton(
                isGridArrangement = isGridArrangement,
                listState = listState,
                gridState = gridState,
            )
        }
    }

    SideEffect(showScrollToTopButton) {
        feedListActions.onScrolledAwayFromTopChanged(showScrollToTopButton)
    }

    val isListAtTop by remember {
        derivedStateOf { isFeedAtTop(isGridArrangement, listState, gridState) }
    }
    SideEffect(isListAtTop, isPagedFeed) {
        feedReloader.isListAtTop = isListAtTop
        feedReloader.isPagedFeed = isPagedFeed
    }
    val homeTabs = rememberHomeTabsController(
        navDrawerState = displayState.navDrawerState,
        currentFeedFilter = displayState.currentFeedFilter,
        isListAtTop = { isListAtTop },
        isPaged = isPagedFeed,
    )
    val flipTabContext = homeTabs.flipTabContext(onFilterSelected = selectFilter)
    val headerCollapse = homeTabs.collapse

    LaunchedEffect(isShowingEmptyFeed, hasFeedItems) {
        when {
            isShowingEmptyFeed -> scrollToTopWhenItemsReturn = true
            hasFeedItems && scrollToTopWhenItemsReturn -> {
                scrollFeedToTop(
                    isGridArrangement = isGridArrangement,
                    listState = listState,
                    gridState = gridState,
                    reduceMotionEnabled = reduceMotionEnabled,
                )
                scrollToTopWhenItemsReturn = false
            }
        }
    }

    val contentContainerColor = displayState.feedLayout.contentContainerColor()

    Scaffold(
        modifier = modifier,
        containerColor = contentContainerColor,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) {
                            data.dismiss()
                        }
                        true
                    },
                )

                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {},
                    content = { Snackbar(data) },
                )
            }
        },
        floatingActionButton = {
            ScrollToTopButton(
                visible = showScrollToTopButton,
                onClick = {
                    scope.launch {
                        scrollFeedToTop(
                            isGridArrangement = isGridArrangement,
                            listState = listState,
                            gridState = gridState,
                            reduceMotionEnabled = reduceMotionEnabled,
                        )
                    }
                },
                containerColor = homeFloatingToolbarContainerColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                border = homeFloatingToolbarBorder(),
            )
        },
    ) { innerPadding ->
        val layoutDir = LocalLayoutDirection.current
        val density = LocalDensity.current
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val toolbarBottom = statusBarHeight + floatingToolbarHeight + FloatingToolbarDefaults.ScreenOffset
        val topInset = homeTabs.headerBottom(statusBarHeight, toolbarBottom)
        SideEffect(homeTabs) {
            homeTabs.updateCollapseLimit(density)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(headerCollapse.nestedScrollConnection)
                .background(contentContainerColor)
                .padding(start = innerPadding.calculateLeftPadding(layoutDir))
                .padding(end = innerPadding.calculateRightPadding(layoutDir)),
        ) {
            when {
                displayState.feedUpdateStatus is NoFeedSourcesStatus -> NoFeedsSourceView(
                    modifier = Modifier.padding(top = topInset),
                    onAddFeedClick = onEmptyStateClick ?: feedManagementActions.onAddFeedClick,
                )

                !displayState.feedUpdateStatus.isLoading() && displayState.feedItems.isEmpty() -> EmptyFeedView(
                    modifier = Modifier.padding(top = topInset),
                    currentFeedFilter = displayState.currentFeedFilter,
                    onReloadClick = onRefresh,
                    onBackToTimelineClick = feedListActions.onBackToTimelineClick,
                    onOpenDrawerClick = onDrawerMenuClick,
                    isDrawerVisible = showDrawerMenu,
                    nextFeedState = displayState.nextFeedDisplayState,
                    onNavigateNext = onNavigateToNextFeed,
                )

                else -> {
                    val pullToRefreshState = rememberPullToRefreshState()
                    val isRefreshing = displayState.feedUpdateStatus.isLoading()
                    val reserveLoaderSpace = rememberReserveLoaderSpace(
                        isRefreshing = isRefreshing,
                        isAwayFromTop = showScrollToTopButton,
                    )
                    PullToRefreshBox(
                        modifier = Modifier.fillMaxSize(),
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        onRefresh = reloadCurrentFeed,
                        indicator = {
                            PullToRefreshIndicator(
                                state = pullToRefreshState,
                                isRefreshing = isRefreshing,
                                topInset = topInset,
                            )
                        },
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            val feedListTopPadding by animateDpAsState(
                                targetValue = topInset + listTopContentPadding +
                                    if (reserveLoaderSpace) feedLoaderReservedHeight else 0.dp,
                                animationSpec = if (reduceMotionEnabled) {
                                    snap()
                                } else {
                                    tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                },
                                label = "feedListTopPadding",
                            )

                            if (displayState.feedItems.isEmpty() && isRefreshing) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    val feedListWidthModifier = displayState.feedLayout.feedListWidthModifier(
                                        isGridLayoutEnabled = displayState.isGridLayoutEnabled,
                                    )

                                    HomeTabsPager(
                                        modifier = feedListWidthModifier.fillMaxHeight(),
                                        controller = homeTabs,
                                        currentFeedFilter = displayState.currentFeedFilter,
                                        feedItems = displayState.feedItems,
                                        liveListState = listState,
                                        liveGridState = gridState,
                                        onFilterSelected = selectFilter,
                                        restartWhenLive = !isPagedFeed,
                                    ) { pageFilter, pageStories, page ->
                                        HomeFeedList(
                                            modifier = Modifier.fillMaxSize(),
                                            feedReloader = page.liveOr(feedReloader, null),
                                            onItemsPagedPast = page.liveOr(feedListActions.onFeedItemsPagedPast) {},
                                            onSourceClick = { source -> selectFilter(FeedFilter.Source(source)) },
                                            flipTabContext = flipTabContext,
                                            feedItems = pageStories,
                                            revealArticleId = page.liveOr(revealArticleId, null),
                                            onArticleRevealed = onArticleRevealed,
                                            listState = page.listState,
                                            gridState = page.gridState,
                                            contentPadding = PaddingValues(top = feedListTopPadding),
                                            feedFontSize = displayState.feedFontSizes,
                                            nextFeedState = displayState.nextFeedDisplayState,
                                            shareCommentsMenuLabel = shareBehavior.shareCommentsTitle,
                                            shareMenuLabel = shareBehavior.shareLinkTitle,
                                            currentFeedFilter = pageFilter,
                                            swipeActions = displayState.swipeActions,
                                            requestMoreItems = page.liveOr(feedListActions.requestNewData) {},
                                            onGridArrangementChanged = page.liveOr({ isGridArrangement = it }) {},
                                            onFeedItemClick = { feedInfo ->
                                                feedListActions.openUrl(feedInfo)
                                                feedListActions.markAsRead(FeedItemId(feedInfo.id))
                                            },
                                            onOpenInBrowser = feedListActions.openInBrowser,
                                            onBookmarkClick = feedListActions.updateBookmarkStatus,
                                            onReadStatusClick = feedListActions.updateReadStatus,
                                            onCommentClick = { feedInfo ->
                                                feedListActions.openUrl(feedInfo)
                                                feedListActions.markAsRead(FeedItemId(feedInfo.id))
                                            },
                                            onVisibleFeedItemsChanged = page.liveOr(
                                                live = feedListActions.onVisibleFeedItemsChanged,
                                                preview = {},
                                            ),
                                            markAllAsRead = markAllReadWithConfirmation,
                                            onShareClick = shareBehavior.onShareClick,
                                            onOpenFeedSettings = feedManagementActions.onEditFeedClick,
                                            onOpenFeedWebsite = feedManagementActions.onOpenWebsite,
                                            feedLayout = displayState.feedLayout,
                                            isGridLayoutEnabled = displayState.isGridLayoutEnabled,
                                            onMarkAllAboveAsRead = feedListActions.markAllAboveAsRead,
                                            onMarkAllBelowAsRead = feedListActions.markAllBelowAsRead,
                                            onNavigateNext = { onNavigateToNextFeed() },
                                            feedItemDisplaySettings = displayState.feedItemDisplaySettings,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topInset + scrimFeather)
                    .graphicsLayer { translationY = -headerCollapse.offsetPx }
                    .zIndex(zIndex = 0.5f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                contentContainerColor,
                                contentContainerColor.copy(alpha = 0.8f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(zIndex = 0.75f)
                    .graphicsLayer { translationY = -headerCollapse.offsetPx }
                    .padding(horizontal = Spacing.regular)
                    .padding(top = topInset + Spacing.small),
                contentAlignment = Alignment.TopCenter,
            ) {
                FeedRefreshPill(loadingState = displayState.feedUpdateStatus)
                NewArticlesPill(
                    count = displayState.pendingNewArticlesCount,
                    loadingState = displayState.feedUpdateStatus,
                    onClick = {
                        scope.launch {
                            scrollFeedToTop(
                                isGridArrangement = isGridArrangement,
                                listState = listState,
                                gridState = gridState,
                                reduceMotionEnabled = reduceMotionEnabled,
                            )
                            feedListActions.onShowNewArticlesClick()
                        }
                    },
                )
            }

            HomeHeader(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
                homeTabs = homeTabs,
                displayState = displayState,
                statusBarHeight = statusBarHeight,
                containerColor = contentContainerColor,
                onFilterSelected = selectFilter,
                onReload = { feedReloader.tapOpenTab(displayState.currentFeedFilter) },
                showDrawerMenu = showDrawerMenu,
                isDrawerOpen = isDrawerOpen,
                onDrawerMenuClick = onDrawerMenuClick,
                onSearchClick = onSearchClick,
                feedListActions = feedListActions,
                onEditFeedClick = feedManagementActions.onEditFeedClick,
                onRefresh = onRefresh,
                onBackupClick = onBackupClick,
                viewMenuState = viewMenuState,
                onFeedOrderChange = onFeedOrderChange,
                onShowReadArticlesTimelineChange = onShowReadArticlesTimelineChange,
            )
        }
    }
}

@Composable
private fun rememberMarkAllReadWithConfirmation(
    feedFilter: FeedFilter,
    onMarkAllRead: () -> Unit,
): () -> Unit {
    var showMarkBookmarksReadDialog by remember(feedFilter) { mutableStateOf(false) }
    if (showMarkBookmarksReadDialog) {
        val strings = LocalFeedFlowStrings.current
        ConfirmationDialog(
            title = strings.markAllReadButton,
            message = strings.markAllBookmarksReadDialogMessage,
            onConfirm = onMarkAllRead,
            onDismiss = { showMarkBookmarksReadDialog = false },
        )
    }
    return remember(feedFilter, onMarkAllRead) {
        {
            if (feedFilter is FeedFilter.Bookmarks) {
                showMarkBookmarksReadDialog = true
            } else {
                onMarkAllRead()
            }
        }
    }
}

// Removing the loader space while scrolled away would shift the whole list,
// so keep it until the user is back at the top.
@Composable
private fun rememberReserveLoaderSpace(
    isRefreshing: Boolean,
    isAwayFromTop: Boolean,
): Boolean {
    var isLoaderSpaceKept by remember { mutableStateOf(false) }
    val reserveLoaderSpace = isRefreshing || (isLoaderSpaceKept && isAwayFromTop)
    SideEffect(reserveLoaderSpace) {
        isLoaderSpaceKept = reserveLoaderSpace
    }
    return reserveLoaderSpace
}

@Composable
private fun BoxScope.PullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    topInset: Dp,
) {
    // While a refresh is running the floating pill already reports the progress,
    // so the spinner would only duplicate it. When the refresh ends the box retracts
    // the indicator, so keep it hidden until it is fully gone.
    var isRetractingAfterRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            isRetractingAfterRefresh = true
        } else if (isRetractingAfterRefresh) {
            snapshotFlow { state.distanceFraction }.first { it == 0f }
            isRetractingAfterRefresh = false
        }
    }
    if (isRefreshing || isRetractingAfterRefresh) {
        return
    }
    PullToRefreshDefaults.Indicator(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = topInset),
        state = state,
        isRefreshing = false,
    )
}

private fun FeedLayout.isCardStyleLayout(): Boolean = when (this) {
    FeedLayout.CARD,
    FeedLayout.BIG_IMAGE,
    FeedLayout.GRID,
    -> true
    FeedLayout.LIST -> false
}

@Composable
private fun FeedLayout.contentContainerColor(): Color =
    if (isCardStyleLayout()) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

private fun FeedLayout.feedListWidthModifier(isGridLayoutEnabled: Boolean): Modifier =
    if (isGridLayoutEnabled && isCardStyleLayout()) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .widthIn(max = FeedListMaxContentWidth)
            .fillMaxWidth()
    }

@Composable
private fun AcknowledgeRevealOnEmptyFeed(
    revealArticleId: String?,
    isShowingEmptyFeed: Boolean,
    onArticleRevealed: () -> Unit,
) {
    // A settled empty timeline has no list to reveal into, so the request is cleared here.
    SideEffect(revealArticleId, isShowingEmptyFeed) {
        if (revealArticleId != null && isShowingEmptyFeed) onArticleRevealed()
    }
}

private fun shouldShowScrollToTopButton(
    isGridArrangement: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
): Boolean =
    if (isGridArrangement) {
        gridState.firstVisibleItemIndex > 1
    } else {
        listState.firstVisibleItemIndex > 1
    }

private suspend fun scrollFeedToTop(
    isGridArrangement: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    reduceMotionEnabled: Boolean,
) {
    if (isGridArrangement) {
        gridState.scrollToItemConditionally(0, reduceMotionEnabled = reduceMotionEnabled)
    } else {
        listState.scrollToItemConditionally(0, reduceMotionEnabled = reduceMotionEnabled)
    }
}

private fun isFeedAtTop(
    isGridArrangement: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
): Boolean = if (isGridArrangement) {
    gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
} else {
    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
}

/** The compact tab bar when there are categories to switch between, else the floating toolbar. */
@Composable
private fun HomeHeader(
    homeTabs: HomeTabsController,
    displayState: HomeDisplayState,
    statusBarHeight: Dp,
    containerColor: Color,
    onFilterSelected: (FeedFilter) -> Unit,
    onReload: () -> Unit,
    showDrawerMenu: Boolean,
    isDrawerOpen: Boolean,
    onDrawerMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    feedListActions: FeedListActions,
    onEditFeedClick: (FeedSource) -> Unit,
    onRefresh: () -> Unit,
    onBackupClick: () -> Unit,
    viewMenuState: HomeViewMenuState,
    onFeedOrderChange: (FeedOrder) -> Unit,
    onShowReadArticlesTimelineChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!homeTabs.showTabs) {
        HomeFloatingToolbar(
            modifier = modifier,
            displayState = displayState,
            showDrawerMenu = showDrawerMenu,
            isDrawerOpen = isDrawerOpen,
            onDrawerMenuClick = onDrawerMenuClick,
            onSearchClick = onSearchClick,
            onMarkAllReadClicked = feedListActions.markAllRead,
            onClearOldArticlesClicked = feedListActions.onClearOldArticlesClicked,
            onEditFeedClick = onEditFeedClick,
            onRefreshClick = onRefresh,
            onBackupClick = onBackupClick,
            viewMenuState = viewMenuState,
            onFeedOrderChange = onFeedOrderChange,
            onShowReadArticlesTimelineChange = onShowReadArticlesTimelineChange,
        )
        return
    }
    HomeTabsLayer(
        modifier = modifier,
        controller = homeTabs,
        currentFeedFilter = displayState.currentFeedFilter,
        showCounts = !displayState.isUnreadCountHidden,
        statusBarHeight = statusBarHeight,
        containerColor = containerColor,
        onFilterSelected = onFilterSelected,
        onReload = onReload,
        navigationButton = {
            if (showDrawerMenu) {
                DrawerIcon(onDrawerMenuClick = onDrawerMenuClick, isDrawerOpen = isDrawerOpen)
            }
        },
        actions = {
            HomeToolbarActions(
                displayState = displayState,
                onSearchClick = onSearchClick,
                onMarkAllReadClicked = feedListActions.markAllRead,
                onClearOldArticlesClicked = feedListActions.onClearOldArticlesClicked,
                onRefreshClick = onRefresh,
                onEditFeedClick = onEditFeedClick,
                onBackupClick = onBackupClick,
                viewMenuState = viewMenuState,
                onFeedOrderChange = onFeedOrderChange,
                onShowReadArticlesTimelineChange = onShowReadArticlesTimelineChange,
            )
        },
    )
}
