package com.prof18.feedflow.android.settings.feedlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prof18.feedflow.android.home.FeedStyle
import com.prof18.feedflow.android.home.FeedStyleSettings
import com.prof18.feedflow.shared.data.SettingsRepository
import com.prof18.feedflow.shared.presentation.FeedListSettingsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FeedListSettingsScreen(
    navigateBack: () -> Unit,
) {
    val viewModel = koinViewModel<FeedListSettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fontSizesState by viewModel.feedFontSizeState.collectAsStateWithLifecycle()
    val feedStyleSettings = koinInject<FeedStyleSettings>()
    val isFlipEnabled by feedStyleSettings.isFlipEnabled.collectAsStateWithLifecycle()
    val settingsRepository = koinInject<SettingsRepository>()
    val isArticleImageEnhancementEnabled by settingsRepository.articleImageEnhancementFlow.collectAsStateWithLifecycle()

    FeedListSettingsScreenContent(
        navigateBack = navigateBack,
        fontSizes = fontSizesState,
        state = state,
        updateFontScale = viewModel::updateFontScale,
        feedStyle = FeedStyle.of(state.feedLayout, isFlipEnabled),
        onFeedStyleSelected = { style ->
            feedStyleSettings.select(style)
            viewModel.updateFeedLayout(style.layout)
        },
        isArticleImageEnhancementEnabled = isArticleImageEnhancementEnabled,
        onArticleImageEnhancementChanged = settingsRepository::setArticleImageEnhancementEnabled,
        setGridLayoutEnabled = viewModel::updateGridLayoutEnabled,
        setHideDescription = viewModel::updateHideDescription,
        setHideImages = viewModel::updateHideImages,
        setHideDate = viewModel::updateHideDate,
        onDateFormatSelected = viewModel::updateDateFormat,
        onTimeFormatSelected = viewModel::updateTimeFormat,
        onSwipeActionSelected = viewModel::updateSwipeAction,
        setRemoveTitleFromDescription = viewModel::updateRemoveTitleFromDescription,
        onFeedOrderSelected = viewModel::updateFeedOrder,
        setHideUnreadDot = viewModel::updateHideUnreadDot,
        setHideFeedSource = viewModel::updateHideFeedSource,
        onDescriptionLineLimitSelected = viewModel::updateDescriptionLineLimit,
    )
}
