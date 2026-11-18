package com.prof18.feedflow.android.ui.home.components.magazine

import androidx.compose.runtime.Immutable
import com.prof18.feedflow.core.model.FeedItem
import java.time.Instant
import java.time.ZoneId

/** Read rows shown when a day's read group is first opened, and how many each "Show more" adds. */
internal const val READ_ROWS_FIRST_PAGE = 5
internal const val READ_ROWS_PAGE = 20

// Every third row of image stories is one wide card, to break the rhythm of the pairs.
private const val ROWS_PER_WIDE_CARD = 3

/**
 * Lays the feed out as a magazine: a heading per day, the first unread image story as the lead,
 * then image stories in pairs with a wide card every third row, and runs of image-less stories
 * as a quick-reads strip (a single one becomes a text card). Unread stories keep their feed order,
 * so mark-as-read-on-scroll still passes them in the order they appear.
 *
 * Stories that [isGroupedAsRead] leave the grid and gather in a collapsed group at the end of
 * their day, so a day with hundreds of read stories costs one row until it is opened.
 * [readRowsShown] gives, per day, how many of those rows are open (0 keeps the group collapsed).
 */
internal fun buildMagazineBlocks(
    items: List<FeedItem>,
    isGroupedAsRead: (FeedItem) -> Boolean,
    readRowsShown: (dayKey: Long) -> Int,
    todayEpochDay: Long,
    zoneId: ZoneId,
): List<MagazineBlock> {
    val blocks = mutableListOf<MagazineBlock>()
    splitIntoDays(items, todayEpochDay, zoneId).forEachIndexed { sectionIndex, section ->
        val (read, unread) = section.items.partition(isGroupedAsRead)
        blocks += MagazineBlock.DayHeader(
            sectionIndex = sectionIndex,
            epochDay = section.epochDay,
            unreadCount = section.items.count { !it.isRead },
        )

        val lead = if (sectionIndex == 0) unread.firstOrNull { it.hasImage() } else null
        lead?.let { blocks += MagazineBlock.Lead(it) }
        blocks += layoutStories(unread.filter { it.id != lead?.id })

        if (read.isNotEmpty()) {
            val shown = readRowsShown(section.epochDay).coerceIn(0, read.size)
            blocks += MagazineBlock.ReadGroupHeader(
                sectionIndex = sectionIndex,
                epochDay = section.epochDay,
                count = read.size,
                isExpanded = shown > 0,
            )
            read.take(shown).forEachIndexed { index, item ->
                blocks += MagazineBlock.ReadRow(item, isLast = index == shown - 1 && shown == read.size)
            }
            if (shown in 1 until read.size) {
                blocks += MagazineBlock.ShowMoreRead(
                    sectionIndex = sectionIndex,
                    epochDay = section.epochDay,
                    remaining = read.size - shown,
                )
            }
        }
    }
    return blocks
}

private fun layoutStories(stories: List<FeedItem>): List<MagazineBlock> {
    val blocks = mutableListOf<MagazineBlock>()
    val pendingImage = mutableListOf<FeedItem>()
    val pendingText = mutableListOf<FeedItem>()
    var imageRows = 0

    fun flushText() {
        when (pendingText.size) {
            0 -> Unit
            1 -> blocks += MagazineBlock.TextCard(pendingText.single())
            else -> blocks += MagazineBlock.QuickReads(pendingText.toList())
        }
        pendingText.clear()
    }

    fun flushImage() {
        if (pendingImage.isNotEmpty()) {
            blocks += MagazineBlock.Wide(pendingImage.single())
            imageRows++
            pendingImage.clear()
        }
    }

    stories.forEach { story ->
        if (story.hasImage()) {
            flushText()
            when {
                pendingImage.isEmpty() && imageRows % ROWS_PER_WIDE_CARD == ROWS_PER_WIDE_CARD - 1 -> {
                    blocks += MagazineBlock.Wide(story)
                    imageRows++
                }
                pendingImage.isEmpty() -> pendingImage += story
                else -> {
                    blocks += MagazineBlock.Pair(pendingImage.single(), story)
                    imageRows++
                    pendingImage.clear()
                }
            }
        } else {
            flushImage()
            pendingText += story
        }
    }
    flushImage()
    flushText()
    return blocks
}

/**
 * Consecutive stories from the same local day form a section. A story without a date stays with
 * the one before it (or with today, at the very top).
 */
private fun splitIntoDays(items: List<FeedItem>, todayEpochDay: Long, zoneId: ZoneId): List<DaySection> {
    var previousDay = todayEpochDay
    val days = items.map { item ->
        val day = item.pubDateMillis?.let { millis ->
            Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate().toEpochDay()
        } ?: previousDay
        previousDay = day
        day
    }
    val sections = mutableListOf<DaySection>()
    items.indices.forEach { index ->
        val day = days[index]
        val last = sections.lastOrNull()
        if (last != null && last.epochDay == day) {
            last.items += items[index]
        } else {
            sections += DaySection(day, mutableListOf(items[index]))
        }
    }
    return sections
}

internal fun FeedItem.hasImage(): Boolean = !imageUrl.isNullOrBlank()

private class DaySection(val epochDay: Long, val items: MutableList<FeedItem>)

@Immutable
internal sealed interface MagazineBlock {
    val key: String

    /** Stories this block shows, in feed order; used to report what is on screen. */
    val itemIds: List<String>

    data class DayHeader(val sectionIndex: Int, val epochDay: Long, val unreadCount: Int) : MagazineBlock {
        override val key = "day-$sectionIndex"
        override val itemIds = emptyList<String>()
    }

    data class Lead(val item: FeedItem) : MagazineBlock {
        override val key = "lead-${item.id}"
        override val itemIds = listOf(item.id)
    }

    data class Pair(val first: FeedItem, val second: FeedItem) : MagazineBlock {
        override val key = "pair-${first.id}"
        override val itemIds = listOf(first.id, second.id)
    }

    data class Wide(val item: FeedItem) : MagazineBlock {
        override val key = "wide-${item.id}"
        override val itemIds = listOf(item.id)
    }

    data class TextCard(val item: FeedItem) : MagazineBlock {
        override val key = "text-${item.id}"
        override val itemIds = listOf(item.id)
    }

    data class QuickReads(val items: List<FeedItem>) : MagazineBlock {
        override val key = "quick-${items.first().id}"
        override val itemIds = items.map { it.id }
    }

    data class ReadGroupHeader(
        val sectionIndex: Int,
        val epochDay: Long,
        val count: Int,
        val isExpanded: Boolean,
    ) : MagazineBlock {
        override val key = "read-group-$sectionIndex"
        override val itemIds = emptyList<String>()
    }

    data class ReadRow(val item: FeedItem, val isLast: Boolean) : MagazineBlock {
        override val key = "read-${item.id}"
        override val itemIds = listOf(item.id)
    }

    data class ShowMoreRead(val sectionIndex: Int, val epochDay: Long, val remaining: Int) : MagazineBlock {
        override val key = "read-more-$sectionIndex"
        override val itemIds = emptyList<String>()
    }
}
