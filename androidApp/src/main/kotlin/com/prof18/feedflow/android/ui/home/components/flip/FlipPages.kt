package com.prof18.feedflow.android.ui.home.components.flip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prof18.feedflow.android.R
import com.prof18.feedflow.android.ui.home.components.FeedItemHeroImage
import com.prof18.feedflow.android.ui.home.components.list.FeedItemContextMenu
import com.prof18.feedflow.android.ui.home.components.list.imageAlpha
import com.prof18.feedflow.android.ui.home.components.list.imageColorFilter
import com.prof18.feedflow.android.ui.home.components.list.textColor
import com.prof18.feedflow.android.ui.home.components.magazine.MagazineStoryActions
import com.prof18.feedflow.android.ui.home.components.magazine.SourceLine
import com.prof18.feedflow.android.ui.home.components.magazine.SourceLogo
import com.prof18.feedflow.android.ui.home.components.magazine.StoryContainer
import com.prof18.feedflow.android.ui.style.Spacing
import com.prof18.feedflow.android.ui.utils.LocalFeedFlowStrings
import com.prof18.feedflow.core.model.FeedItem
import com.prof18.feedflow.core.model.FeedItemId
import com.prof18.feedflow.core.model.FeedItemUrlTitle

private const val COVER_IMAGE_RATIO = 4f / 3f
private const val MAX_PROGRESS_TICKS = 12
private val PageInset = 18.dp

/** The next tab, offered on the end page. */
internal data class NextCategory(val name: String, val open: () -> Unit)

@Composable
internal fun FlipPageView(
    page: FlipPage,
    pageIndex: Int,
    pageCount: Int,
    actions: MagazineStoryActions,
    currentTabName: String?,
    nextCategory: NextCategory?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        when (page) {
            is FlipPage.Cover -> CoverPage(page.item, actions)
            is FlipPage.Pair -> PairPage(page.first, page.second, actions)
            is FlipPage.QuickReads -> QuickReadsPage(page.items, actions)
            is FlipPage.End -> EndPage(page.readCount, currentTabName, nextCategory)
        }
        if (page !is FlipPage.End) {
            FlipBottomBar(
                coverItem = (page as? FlipPage.Cover)?.item,
                pageIndex = pageIndex,
                pageCount = pageCount,
                actions = actions,
            )
        }
    }
}

@Composable
private fun ColumnScope.CoverPage(item: FeedItem, actions: MagazineStoryActions) {
    StoryContainer(item = item, actions = actions, modifier = Modifier.weight(1f)) {
        Column {
            item.imageUrl?.let { url ->
                FeedItemHeroImage(
                    url = url,
                    aspectRatio = COVER_IMAGE_RATIO,
                    imageAlpha = item.imageAlpha(actions.currentFeedFilter),
                    colorFilter = item.imageColorFilter(actions.currentFeedFilter),
                )
            }
            Column(modifier = Modifier.padding(horizontal = PageInset, vertical = Spacing.regular)) {
                SourceLine(item, actions)
                item.title?.let { title ->
                    Text(
                        modifier = Modifier.padding(top = Spacing.small),
                        text = title,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (actions.feedFontSize.feedTitleFontSize + 8).sp,
                        lineHeight = (actions.feedFontSize.feedTitleFontSize + 13).sp,
                        color = item.textColor(actions.currentFeedFilter),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.subtitle?.let { summary ->
                    PageSummary(summary, item, actions, maxLines = actions.displaySettings.descriptionLineLimit.lines)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PairPage(first: FeedItem, second: FeedItem, actions: MagazineStoryActions) {
    PairStory(first, actions, Modifier.weight(1f))
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = PageInset),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
    PairStory(second, actions, Modifier.weight(1f))
}

@Composable
private fun PairStory(item: FeedItem, actions: MagazineStoryActions, modifier: Modifier = Modifier) {
    StoryContainer(item = item, actions = actions, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = PageInset, vertical = Spacing.regular)) {
            item.title?.let { title ->
                Text(
                    text = title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (actions.feedFontSize.feedTitleFontSize + 3).sp,
                    lineHeight = (actions.feedFontSize.feedTitleFontSize + 8).sp,
                    color = item.textColor(actions.currentFeedFilter),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.padding(top = Spacing.small),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SourceLine(item, actions)
                    item.subtitle?.let { summary -> PageSummary(summary, item, actions, maxLines = 4) }
                }
                item.imageUrl?.let { url ->
                    FeedItemHeroImage(
                        modifier = Modifier
                            .width(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        url = url,
                        aspectRatio = 1f,
                        imageAlpha = item.imageAlpha(actions.currentFeedFilter),
                        colorFilter = item.imageColorFilter(actions.currentFeedFilter),
                    )
                }
            }
            StoryActions(item, actions)
        }
    }
}

@Composable
private fun ColumnScope.QuickReadsPage(items: List<FeedItem>, actions: MagazineStoryActions) {
    Row(
        modifier = Modifier.padding(start = PageInset, end = PageInset, top = Spacing.regular),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(18.dp),
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            modifier = Modifier.padding(start = 6.dp),
            text = stringResource(R.string.flip_quick_reads),
            style = MaterialTheme.typography.titleSmall,
        )
    }
    items.forEachIndexed { index, item ->
        if (index > 0) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = PageInset),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        StoryContainer(item = item, actions = actions, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = PageInset, vertical = Spacing.small)) {
                SourceLine(item, actions)
                item.title?.let { title ->
                    Text(
                        modifier = Modifier.padding(top = Spacing.xsmall),
                        text = title,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (actions.feedFontSize.feedTitleFontSize + 1).sp,
                        lineHeight = (actions.feedFontSize.feedTitleFontSize + 6).sp,
                        color = item.textColor(actions.currentFeedFilter),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.subtitle?.let { summary -> PageSummary(summary, item, actions, maxLines = 3) }
            }
        }
    }
}

@Composable
private fun ColumnScope.EndPage(readCount: Int, currentTabName: String?, nextCategory: NextCategory?) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(Spacing.large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            modifier = Modifier.size(48.dp),
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            modifier = Modifier.padding(top = Spacing.regular),
            text = currentTabName?.let { stringResource(R.string.flip_caught_up_in, it) }
                ?: stringResource(R.string.flip_caught_up),
            fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (readCount > 0) {
            Text(
                modifier = Modifier.padding(top = Spacing.small),
                text = stringResource(R.string.flip_read_folded, readCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (nextCategory != null) {
            FilledTonalButton(modifier = Modifier.padding(top = Spacing.medium), onClick = nextCategory.open) {
                Text(stringResource(R.string.flip_next_category, nextCategory.name))
            }
        }
    }
}

/**
 * Progress ticks for the tab, then the source of a single-story page with its actions. Pages
 * with several stories carry each story's actions inline, so the bar only shows progress there.
 */
@Composable
private fun FlipBottomBar(
    coverItem: FeedItem?,
    pageIndex: Int,
    pageCount: Int,
    actions: MagazineStoryActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageInset)
            .padding(top = Spacing.small, bottom = Spacing.small),
    ) {
        ProgressTicks(pageIndex, pageCount)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (coverItem != null) {
                SourceChip(coverItem, actions)
                Spacer(modifier = Modifier.weight(1f))
                StoryActions(coverItem, actions, showMore = true)
            } else {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.flip_page_of, pageIndex + 1, pageCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProgressTicks(pageIndex: Int, pageCount: Int) {
    val ticks = pageCount.coerceIn(1, MAX_PROGRESS_TICKS)
    val filled = ((pageIndex + 1) * ticks + pageCount - 1) / pageCount
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(ticks) { tick ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (tick < filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
    }
}

@Composable
private fun SourceChip(item: FeedItem, actions: MagazineStoryActions) {
    val description = stringResource(R.string.flip_open_feed, item.feedSource.title)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = description) { actions.onSourceClick(item.feedSource) }
            .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceLogo(item)
        Text(
            modifier = Modifier
                .padding(start = 6.dp)
                .widthIn(max = 160.dp),
            text = item.feedSource.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StoryActions(item: FeedItem, actions: MagazineStoryActions, showMore: Boolean = false) {
    var showMenu by remember { mutableStateOf(false) }
    val strings = LocalFeedFlowStrings.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { actions.onBookmarkClick(FeedItemId(item.id), !item.isBookmarked) }) {
            Icon(
                imageVector = if (item.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (item.isBookmarked) {
                    strings.menuRemoveFromBookmark
                } else {
                    strings.menuAddToBookmark
                },
                tint = if (item.isBookmarked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = { actions.onShareClick(FeedItemUrlTitle(url = item.url, title = item.title)) }) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = actions.shareMenuLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showMore) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = strings.moreOptionsButtonContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FeedItemContextMenu(
                    showMenu = showMenu,
                    closeMenu = { showMenu = false },
                    feedItem = item,
                    shareMenuLabel = actions.shareMenuLabel,
                    shareCommentsMenuLabel = actions.shareCommentsMenuLabel,
                    onBookmarkClick = actions.onBookmarkClick,
                    onReadStatusClick = actions.onReadStatusClick,
                    onCommentClick = actions.onCommentClick,
                    onShareClick = actions.onShareClick,
                    onOpenFeedSettings = actions.onOpenFeedSettings,
                    onOpenFeedWebsite = actions.onOpenFeedWebsite,
                    onMarkAllAboveAsRead = actions.onMarkAllAboveAsRead,
                    onMarkAllBelowAsRead = actions.onMarkAllBelowAsRead,
                )
            }
        }
    }
}

@Composable
private fun PageSummary(summary: String, item: FeedItem, actions: MagazineStoryActions, maxLines: Int) {
    Text(
        modifier = Modifier.padding(top = Spacing.small),
        text = summary,
        fontSize = (actions.feedFontSize.feedDescFontSize + 1).sp,
        lineHeight = (actions.feedFontSize.feedDescFontSize + 8).sp,
        color = item.textColor(actions.currentFeedFilter, MaterialTheme.colorScheme.onSurfaceVariant),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
