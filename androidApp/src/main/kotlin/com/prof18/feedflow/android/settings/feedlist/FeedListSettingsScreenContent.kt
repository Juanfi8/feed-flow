package com.prof18.feedflow.android.settings.feedlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.prof18.feedflow.android.R
import com.prof18.feedflow.android.home.FeedStyle
import com.prof18.feedflow.android.settings.SettingsE2eIds
import com.prof18.feedflow.android.ui.readermode.SliderWithPlusMinus
import com.prof18.feedflow.android.ui.readermode.wholeValueSteps
import com.prof18.feedflow.android.ui.settings.CompactSettingDropdownRow
import com.prof18.feedflow.android.ui.settings.FeedItemPreview
import com.prof18.feedflow.android.ui.settings.SettingDropdownOption
import com.prof18.feedflow.android.ui.settings.SettingSwitchItem
import com.prof18.feedflow.android.ui.style.Spacing
import com.prof18.feedflow.android.ui.theme.FeedFlowTheme
import com.prof18.feedflow.android.ui.utils.LocalFeedFlowStrings
import com.prof18.feedflow.core.model.DateFormat
import com.prof18.feedflow.core.model.DescriptionLineLimit
import com.prof18.feedflow.core.model.FeedFontSizes
import com.prof18.feedflow.core.model.FeedItemDisplaySettings
import com.prof18.feedflow.core.model.FeedLayout
import com.prof18.feedflow.core.model.FeedListSettingsState
import com.prof18.feedflow.core.model.FeedOrder
import com.prof18.feedflow.core.model.SwipeActionType
import com.prof18.feedflow.core.model.SwipeDirection
import com.prof18.feedflow.core.model.TimeFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.roundToInt

@Composable
internal fun FeedListSettingsScreenContent(
    navigateBack: () -> Unit,
    fontSizes: FeedFontSizes,
    state: FeedListSettingsState,
    updateFontScale: (Int) -> Unit,
    feedStyle: FeedStyle,
    onFeedStyleSelected: (FeedStyle) -> Unit,
    isArticleImageEnhancementEnabled: Boolean,
    onArticleImageEnhancementChanged: (Boolean) -> Unit,
    setGridLayoutEnabled: (Boolean) -> Unit,
    setHideDescription: (Boolean) -> Unit,
    setHideImages: (Boolean) -> Unit,
    setHideDate: (Boolean) -> Unit,
    onDateFormatSelected: (DateFormat) -> Unit,
    onTimeFormatSelected: (TimeFormat) -> Unit,
    onSwipeActionSelected: (SwipeDirection, SwipeActionType) -> Unit,
    setRemoveTitleFromDescription: (Boolean) -> Unit,
    onFeedOrderSelected: (FeedOrder) -> Unit,
    setHideUnreadDot: (Boolean) -> Unit,
    setHideFeedSource: (Boolean) -> Unit,
    onDescriptionLineLimitSelected: (DescriptionLineLimit) -> Unit,
) {
    val strings = LocalFeedFlowStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsFeedListTitle) },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.testTag(SettingsE2eIds.BACK_BUTTON),
                        onClick = navigateBack,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        val layoutDir = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .padding(top = paddingValues.calculateTopPadding())
                .padding(start = paddingValues.calculateLeftPadding(layoutDir))
                .padding(end = paddingValues.calculateRightPadding(layoutDir)),
        ) {
            FeedItemPreview(
                fontSizes = fontSizes,
                feedLayout = state.feedLayout,
                isHideDescriptionEnabled = state.isHideDescriptionEnabled,
                isHideImagesEnabled = state.isHideImagesEnabled,
                isHideDateEnabled = state.isHideDateEnabled,
                dateFormat = state.dateFormat,
                timeFormat = state.timeFormat,
                feedItemDisplaySettings = FeedItemDisplaySettings(
                    isHideUnreadDotEnabled = state.isHideUnreadDotEnabled,
                    isHideFeedSourceEnabled = state.isHideFeedSourceEnabled,
                    descriptionLineLimit = state.descriptionLineLimit,
                ),
            )

            // Magazine has no per-article swipe actions and shows times under day headings,
            // so the swipe and date format settings only apply to List and Card.
            val isMagazine = feedStyle.isEditorial
            LazyColumn {
                item {
                    Text(
                        text = LocalFeedFlowStrings.current.settingsFeedListScaleTitle,
                        modifier = Modifier.padding(Spacing.regular),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                item {
                    SliderWithPlusMinus(
                        modifier = Modifier.padding(horizontal = Spacing.regular),
                        value = fontSizes.scaleFactor.toFloat(),
                        onValueChange = { updateFontScale(it.roundToInt()) },
                        valueRange = FONT_SCALE_RANGE,
                        steps = wholeValueSteps(FONT_SCALE_RANGE),
                    )
                }

                item {
                    Spacer(modifier = Modifier.padding(top = Spacing.regular))
                    CompactSettingDropdownRow(
                        modifier = Modifier.testTag(SettingsE2eIds.FEED_LIST_LAYOUT),
                        title = strings.feedLayoutTitle,
                        currentValue = feedStyle,
                        options = persistentListOf(
                            SettingDropdownOption(FeedStyle.LIST, strings.settingsFeedLayoutList),
                            SettingDropdownOption(FeedStyle.CARD, strings.settingsFeedLayoutCard),
                            SettingDropdownOption(FeedStyle.MAGAZINE, stringResource(R.string.feed_style_magazine)),
                            SettingDropdownOption(FeedStyle.FLIP, stringResource(R.string.feed_style_flip)),
                        ),
                        onOptionSelected = onFeedStyleSelected,
                    )
                }

                if (isMagazine) {
                    item {
                        Text(
                            modifier = Modifier.padding(horizontal = Spacing.regular, vertical = Spacing.small),
                            text = stringResource(feedStyle.hintRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.feedLayout.supportsGridToggle()) {
                    item {
                        SettingSwitchItem(
                            title = strings.settingsFeedLayoutGridToggle,
                            isChecked = state.isGridLayoutEnabled,
                            onCheckedChange = setGridLayoutEnabled,
                        )
                    }
                }

                item {
                    SettingSwitchItem(
                        title = strings.settingsHideDescription,
                        isChecked = state.isHideDescriptionEnabled,
                        onCheckedChange = setHideDescription,
                    )
                }

                item {
                    SettingSwitchItem(
                        modifier = Modifier.testTag(SettingsE2eIds.FEED_LIST_HIDE_IMAGES),
                        title = strings.settingsHideImages,
                        isChecked = state.isHideImagesEnabled,
                        onCheckedChange = setHideImages,
                    )
                }

                item {
                    SettingSwitchItem(
                        title = stringResource(R.string.article_images_setting),
                        supportingText = stringResource(R.string.article_images_setting_description),
                        isChecked = isArticleImageEnhancementEnabled,
                        onCheckedChange = onArticleImageEnhancementChanged,
                        enabled = !state.isHideImagesEnabled,
                    )
                }

                item {
                    SettingSwitchItem(
                        title = strings.settingsHideDate,
                        isChecked = state.isHideDateEnabled,
                        onCheckedChange = setHideDate,
                    )
                }

                item {
                    SettingSwitchItem(
                        title = strings.settingsHideUnreadDot,
                        isChecked = state.isHideUnreadDotEnabled,
                        onCheckedChange = setHideUnreadDot,
                    )
                }

                item {
                    SettingSwitchItem(
                        title = strings.settingsHideFeedSource,
                        isChecked = state.isHideFeedSourceEnabled,
                        onCheckedChange = setHideFeedSource,
                    )
                }

                item {
                    SettingSwitchItem(
                        title = strings.settingsHideDuplicatedTitleFromDesc,
                        isChecked = state.isRemoveTitleFromDescriptionEnabled,
                        onCheckedChange = setRemoveTitleFromDescription,
                    )
                }

                item {
                    CompactSettingDropdownRow(
                        title = strings.settingsDescriptionMaxLines,
                        currentValue = state.descriptionLineLimit,
                        options = persistentListOf(
                            SettingDropdownOption(
                                DescriptionLineLimit.THREE,
                                strings.settingsDescriptionLinesThree,
                            ),
                            SettingDropdownOption(
                                DescriptionLineLimit.FIVE,
                                strings.settingsDescriptionLinesFive,
                            ),
                            SettingDropdownOption(
                                DescriptionLineLimit.NO_LIMIT,
                                strings.settingsDescriptionLinesNoLimit,
                            ),
                        ),
                        onOptionSelected = onDescriptionLineLimitSelected,
                    )
                }

                if (!isMagazine) {
                    item {
                        CompactSettingDropdownRow(
                            title = strings.dateFormatTitle,
                            currentValue = state.dateFormat,
                            options = persistentListOf(
                                SettingDropdownOption(DateFormat.NORMAL, strings.dateFormatNormal),
                                SettingDropdownOption(DateFormat.DAY_MONTH_DOT, strings.dateFormatDayMonthDot),
                                SettingDropdownOption(DateFormat.AMERICAN, strings.dateFormatAmerican),
                                SettingDropdownOption(DateFormat.ISO, strings.dateFormatIso),
                            ),
                            onOptionSelected = onDateFormatSelected,
                        )
                    }
                }

                item {
                    CompactSettingDropdownRow(
                        title = strings.timeFormatTitle,
                        currentValue = state.timeFormat,
                        options = persistentListOf(
                            SettingDropdownOption(TimeFormat.HOURS_24, strings.timeFormatHours24),
                            SettingDropdownOption(TimeFormat.HOURS_12, strings.timeFormatHours12),
                        ),
                        onOptionSelected = onTimeFormatSelected,
                    )
                }

                item {
                    CompactSettingDropdownRow(
                        modifier = Modifier.testTag(SettingsE2eIds.FEED_LIST_ORDER),
                        title = strings.settingsFeedOrderTitle,
                        currentValue = state.feedOrder,
                        options = persistentListOf(
                            SettingDropdownOption(
                                FeedOrder.NEWEST_FIRST,
                                strings.settingsFeedOrderNewestFirst,
                            ),
                            SettingDropdownOption(
                                FeedOrder.OLDEST_FIRST,
                                strings.settingsFeedOrderOldestFirst,
                            ),
                        ),
                        onOptionSelected = onFeedOrderSelected,
                    )
                }

                if (!isMagazine) {
                    item {
                        CompactSettingDropdownRow(
                            title = strings.settingsLeftSwipeAction,
                            currentValue = state.leftSwipeActionType,
                            options = swipeActionOptions(
                                toggleReadLabel = strings.settingsSwipeActionToggleRead,
                                toggleBookmarkLabel = strings.settingsSwipeActionToggleBookmark,
                                openInBrowserLabel = strings.settingsSwipeActionOpenInBrowser,
                                redirectLabel = stringResource(R.string.redirect_open),
                                noneLabel = strings.settingsSwipeActionNone,
                            ),
                            onOptionSelected = { action -> onSwipeActionSelected(SwipeDirection.LEFT, action) },
                        )
                    }
                }

                if (!isMagazine) {
                    item {
                        CompactSettingDropdownRow(
                            title = strings.settingsRightSwipeAction,
                            currentValue = state.rightSwipeActionType,
                            options = swipeActionOptions(
                                toggleReadLabel = strings.settingsSwipeActionToggleRead,
                                toggleBookmarkLabel = strings.settingsSwipeActionToggleBookmark,
                                openInBrowserLabel = strings.settingsSwipeActionOpenInBrowser,
                                redirectLabel = stringResource(R.string.redirect_open),
                                noneLabel = strings.settingsSwipeActionNone,
                            ),
                            onOptionSelected = { action -> onSwipeActionSelected(SwipeDirection.RIGHT, action) },
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
                }
            }
        }
    }
}

private fun swipeActionOptions(
    toggleReadLabel: String,
    toggleBookmarkLabel: String,
    openInBrowserLabel: String,
    noneLabel: String,
    redirectLabel: String,
): ImmutableList<SettingDropdownOption<SwipeActionType>> = persistentListOf(
    SettingDropdownOption(
        SwipeActionType.TOGGLE_READ_STATUS,
        toggleReadLabel,
    ),
    SettingDropdownOption(
        SwipeActionType.TOGGLE_BOOKMARK_STATUS,
        toggleBookmarkLabel,
    ),
    SettingDropdownOption(
        SwipeActionType.OPEN_IN_BROWSER,
        openInBrowserLabel,
    ),
    SettingDropdownOption(SwipeActionType.OPEN_REDIRECTED, redirectLabel),
    SettingDropdownOption(
        SwipeActionType.NONE,
        noneLabel,
    ),
)

// Magazine is always a single column, so only Card can switch to a grid.
private fun FeedLayout.supportsGridToggle(): Boolean = this == FeedLayout.CARD

@Preview
@Composable
private fun FeedListSettingsScreenContentPreview() {
    FeedFlowTheme {
        FeedListSettingsScreenContent(
            navigateBack = {},
            fontSizes = FeedFontSizes(),
            state = FeedListSettingsState(
                isHideDescriptionEnabled = false,
                isHideImagesEnabled = true,
                isHideDateEnabled = false,
                dateFormat = DateFormat.NORMAL,
                timeFormat = TimeFormat.HOURS_24,
                feedLayout = FeedLayout.LIST,
                feedOrder = FeedOrder.NEWEST_FIRST,
            ),
            updateFontScale = {},
            feedStyle = FeedStyle.LIST,
            onFeedStyleSelected = {},
            isArticleImageEnhancementEnabled = false,
            onArticleImageEnhancementChanged = {},
            setGridLayoutEnabled = {},
            setHideDescription = {},
            setHideImages = {},
            setHideDate = {},
            onDateFormatSelected = {},
            onTimeFormatSelected = {},
            onSwipeActionSelected = { _, _ -> },
            setRemoveTitleFromDescription = {},
            onFeedOrderSelected = {},
            setHideUnreadDot = {},
            setHideFeedSource = {},
            onDescriptionLineLimitSelected = {},
        )
    }
}

private val FONT_SCALE_RANGE = -4f..16f

private fun FeedStyle.hintRes(): Int = if (isPaged) R.string.feed_style_flip_hint else R.string.feed_style_magazine_hint
