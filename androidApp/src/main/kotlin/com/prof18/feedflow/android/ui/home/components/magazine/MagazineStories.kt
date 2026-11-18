package com.prof18.feedflow.android.ui.home.components.magazine

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prof18.feedflow.android.R
import com.prof18.feedflow.android.ui.components.FeedSourceLogoImage
import com.prof18.feedflow.android.ui.feedsourcelist.singleAndLongClickModifier
import com.prof18.feedflow.android.ui.home.components.FeedItemHeroImage
import com.prof18.feedflow.android.ui.home.components.list.FeedItemContextMenu
import com.prof18.feedflow.android.ui.home.components.list.FeedItemE2eIds
import com.prof18.feedflow.android.ui.home.components.list.imageAlpha
import com.prof18.feedflow.android.ui.home.components.list.imageColorFilter
import com.prof18.feedflow.android.ui.home.components.list.textColor
import com.prof18.feedflow.android.ui.home.components.list.toClickUrlInfo
import com.prof18.feedflow.android.ui.style.Spacing
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedFontSizes
import com.prof18.feedflow.core.model.FeedItem
import com.prof18.feedflow.core.model.FeedItemDisplaySettings
import com.prof18.feedflow.core.model.FeedItemId
import com.prof18.feedflow.core.model.FeedItemUrlInfo
import com.prof18.feedflow.core.model.FeedItemUrlTitle
import com.prof18.feedflow.core.model.FeedSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private const val TILE_IMAGE_RATIO = 4f / 3f
private const val LEAD_IMAGE_RATIO = 16f / 9f
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_MINUTE = 60_000L
private val StoryCorner = 14.dp
private val ImageCorner = 12.dp

/** Everything a story needs to react to taps and its long-press menu. */
@Immutable
internal data class MagazineStoryActions(
    val currentFeedFilter: FeedFilter,
    val feedFontSize: FeedFontSizes,
    val displaySettings: FeedItemDisplaySettings,
    val shareMenuLabel: String,
    val shareCommentsMenuLabel: String,
    val onFeedItemClick: (FeedItemUrlInfo) -> Unit,
    val onBookmarkClick: (FeedItemId, Boolean) -> Unit,
    val onReadStatusClick: (FeedItemId, Boolean) -> Unit,
    val onCommentClick: (FeedItemUrlInfo) -> Unit,
    val onShareClick: (FeedItemUrlTitle) -> Unit,
    val onOpenFeedSettings: (FeedSource) -> Unit,
    val onOpenFeedWebsite: (String) -> Unit,
    val onMarkAllAboveAsRead: (String) -> Unit,
    val onMarkAllBelowAsRead: (String) -> Unit,
)

@Composable
internal fun MagazineDayHeader(epochDay: Long, unreadCount: Int, todayEpochDay: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.regular, end = Spacing.regular, top = Spacing.regular, bottom = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dayLabel(epochDay, todayEpochDay),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (unreadCount > 0) {
            Text(
                modifier = Modifier.padding(start = Spacing.small),
                text = stringResource(R.string.magazine_unread_count, unreadCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.small),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
internal fun MagazineLead(item: FeedItem, actions: MagazineStoryActions, modifier: Modifier = Modifier) {
    StoryContainer(item = item, actions = actions, modifier = modifier) {
        Column {
            item.imageUrl?.let { url ->
                FeedItemHeroImage(
                    url = url,
                    aspectRatio = LEAD_IMAGE_RATIO,
                    imageAlpha = item.imageAlpha(actions.currentFeedFilter),
                    colorFilter = item.imageColorFilter(actions.currentFeedFilter),
                )
            }
            Column(modifier = Modifier.padding(horizontal = Spacing.regular, vertical = Spacing.medium / 2)) {
                SourceLine(item, actions)
                item.title?.let { title ->
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        text = title,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (actions.feedFontSize.feedTitleFontSize + 6).sp,
                        lineHeight = (actions.feedFontSize.feedTitleFontSize + 11).sp,
                        color = item.textColor(actions.currentFeedFilter),
                    )
                }
                item.subtitle?.let { summary ->
                    Summary(summary, item, actions, maxLines = 3)
                }
            }
        }
    }
}

@Composable
internal fun MagazinePair(
    first: FeedItem,
    second: FeedItem,
    actions: MagazineStoryActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MagazineTile(first, actions, Modifier.weight(1f))
        MagazineTile(second, actions, Modifier.weight(1f))
    }
}

@Composable
private fun MagazineTile(item: FeedItem, actions: MagazineStoryActions, modifier: Modifier = Modifier) {
    StoryContainer(item = item, actions = actions, modifier = modifier, shape = RoundedCornerShape(ImageCorner)) {
        Column {
            item.imageUrl?.let { url ->
                FeedItemHeroImage(
                    modifier = Modifier.clip(RoundedCornerShape(ImageCorner)),
                    url = url,
                    aspectRatio = TILE_IMAGE_RATIO,
                    imageAlpha = item.imageAlpha(actions.currentFeedFilter),
                    colorFilter = item.imageColorFilter(actions.currentFeedFilter),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            SourceLine(item, actions)
            StoryTitle(item, actions, sizeOffset = -2, maxLines = 3)
        }
    }
}

@Composable
internal fun MagazineWide(item: FeedItem, actions: MagazineStoryActions, modifier: Modifier = Modifier) {
    StoryContainer(
        item = item,
        actions = actions,
        modifier = modifier.padding(horizontal = 12.dp, vertical = Spacing.small),
        shape = RoundedCornerShape(StoryCorner),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item.imageUrl?.let { url ->
                FeedItemHeroImage(
                    modifier = Modifier
                        .width(120.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    url = url,
                    aspectRatio = TILE_IMAGE_RATIO,
                    imageAlpha = item.imageAlpha(actions.currentFeedFilter),
                    colorFilter = item.imageColorFilter(actions.currentFeedFilter),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                SourceLine(item, actions)
                StoryTitle(item, actions, sizeOffset = 0, maxLines = 4)
            }
        }
    }
}

@Composable
internal fun MagazineTextCard(item: FeedItem, actions: MagazineStoryActions, modifier: Modifier = Modifier) {
    StoryContainer(
        item = item,
        actions = actions,
        modifier = modifier.padding(horizontal = 12.dp, vertical = Spacing.small),
        shape = RoundedCornerShape(StoryCorner),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(14.dp),
        ) {
            SourceLine(item, actions)
            StoryTitle(item, actions, sizeOffset = 1, maxLines = 4)
            item.subtitle?.let { summary -> Summary(summary, item, actions, maxLines = 3) }
        }
    }
}

@Composable
internal fun MagazineQuickReads(items: List<FeedItem>, actions: MagazineStoryActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = Spacing.small)) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.regular, vertical = Spacing.xsmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                modifier = Modifier.padding(start = 6.dp),
                text = stringResource(R.string.magazine_quick_reads),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            items(items, key = { it.id }) { item ->
                StoryContainer(
                    item = item,
                    actions = actions,
                    modifier = Modifier.width(176.dp),
                    shape = RoundedCornerShape(ImageCorner),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp),
                    ) {
                        SourceLine(item, actions)
                        StoryTitle(item, actions, sizeOffset = -2, maxLines = 4)
                    }
                }
            }
        }
    }
}

/** One row for a whole day of read stories; a tap opens (or closes) it. */
@Composable
internal fun MagazineReadGroupHeader(
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = if (isExpanded) {
        RoundedCornerShape(topStart = StoryCorner, topEnd = StoryCorner)
    } else {
        RoundedCornerShape(StoryCorner)
    }
    val toggleDescription = stringResource(
        if (isExpanded) R.string.magazine_read_group_collapse else R.string.magazine_read_group_expand,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = Spacing.small)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = toggleDescription }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(18.dp),
            imageVector = Icons.Default.DoneAll,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.small),
            text = stringResource(R.string.magazine_read_group, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            modifier = Modifier.rotate(degrees = if (isExpanded) 180f else 0f),
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun MagazineReadRow(
    item: FeedItem,
    isLast: Boolean,
    actions: MagazineStoryActions,
    modifier: Modifier = Modifier,
) {
    val shape = if (isLast) {
        RoundedCornerShape(bottomStart = StoryCorner, bottomEnd = StoryCorner)
    } else {
        RectangleShapeCompat
    }
    StoryContainer(
        item = item,
        actions = actions,
        modifier = modifier
            .padding(horizontal = 12.dp)
            .clip(shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.imageUrl?.let { url ->
                FeedItemHeroImage(
                    modifier = Modifier
                        .width(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    url = url,
                    aspectRatio = 1f,
                    imageAlpha = item.imageAlpha(actions.currentFeedFilter),
                    colorFilter = item.imageColorFilter(actions.currentFeedFilter),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.feedSource.title + " · " + storyTime(item, LocalDate.now().toEpochDay()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.title.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = item.textColor(actions.currentFeedFilter),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun MagazineShowMoreRead(remaining: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(bottomStart = StoryCorner, bottomEnd = StoryCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick) {
            Text(stringResource(R.string.magazine_show_more, remaining))
        }
    }
}

/** Tap opens the story (with the feed's redirect), long-press opens the usual article menu. */
@Composable
private fun StoryContainer(
    item: FeedItem,
    actions: MagazineStoryActions,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShapeCompat,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(shape)
            .testTag(FeedItemE2eIds.row(item.id))
            .singleAndLongClickModifier(
                onClick = { item.toClickUrlInfo(context)?.let(actions.onFeedItemClick) },
                onLongClick = { showMenu = true },
            ),
    ) {
        content()
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

@Composable
private fun SourceLine(item: FeedItem, actions: MagazineStoryActions) {
    val showSource = !actions.displaySettings.isHideFeedSourceEnabled
    val showDot = !item.isRead && !actions.displaySettings.isHideUnreadDotEnabled
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showSource) {
            SourceLogo(item)
            Spacer(modifier = Modifier.width(6.dp))
        }
        val time = storyTime(item, LocalDate.now().toEpochDay())
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = if (showSource) item.feedSource.title else time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showSource && time.isNotEmpty()) {
            Text(
                text = " · $time",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (showDot) {
            Spacer(modifier = Modifier.weight(weight = 0.01f))
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun SourceLogo(item: FeedItem) {
    val logoUrl = item.feedSource.logoUrl
    if (logoUrl != null) {
        FeedSourceLogoImage(imageUrl = logoUrl, size = 16.dp, cornerRadius = 4.dp)
    } else {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(11.dp),
                imageVector = Icons.Default.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StoryTitle(item: FeedItem, actions: MagazineStoryActions, sizeOffset: Int, maxLines: Int) {
    item.title?.let { title ->
        val size = actions.feedFontSize.feedTitleFontSize + sizeOffset
        Text(
            modifier = Modifier.padding(top = Spacing.xsmall),
            text = title,
            fontSize = size.sp,
            lineHeight = (size + 4).sp,
            fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold,
            color = item.textColor(actions.currentFeedFilter),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Summary(summary: String, item: FeedItem, actions: MagazineStoryActions, maxLines: Int) {
    Text(
        modifier = Modifier.padding(top = Spacing.xsmall),
        text = summary,
        fontSize = actions.feedFontSize.feedDescFontSize.sp,
        lineHeight = (actions.feedFontSize.feedDescFontSize + 6).sp,
        color = item.textColor(actions.currentFeedFilter, MaterialTheme.colorScheme.onSurfaceVariant),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "12 min" or "3 h" for today's stories, the clock time for older ones. */
@Composable
@ReadOnlyComposable
private fun storyTime(item: FeedItem, todayEpochDay: Long): String {
    val millis = item.pubDateMillis ?: return item.dateString.orEmpty()
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
    if (day != todayEpochDay) return DateFormat.getTimeFormat(context).format(Date(millis))
    val minutes = ((System.currentTimeMillis() - millis) / MILLIS_PER_MINUTE).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.magazine_time_now)
        minutes < MINUTES_PER_HOUR -> stringResource(R.string.magazine_time_minutes, minutes)
        else -> stringResource(R.string.magazine_time_hours, minutes / MINUTES_PER_HOUR)
    }
}

@Composable
@ReadOnlyComposable
private fun dayLabel(epochDay: Long, todayEpochDay: Long): String = when (epochDay) {
    todayEpochDay -> stringResource(R.string.magazine_today)
    todayEpochDay - 1 -> stringResource(R.string.magazine_yesterday)
    else -> {
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

private val RectangleShapeCompat: Shape = androidx.compose.ui.graphics.RectangleShape
