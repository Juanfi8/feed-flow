package com.prof18.feedflow.android.ui.home.components.magazine

import com.prof18.feedflow.core.model.ArticleOpenMode
import com.prof18.feedflow.core.model.FeedItem
import com.prof18.feedflow.core.model.FeedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MagazineLayoutTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 18)
    private val yesterday = today.minusDays(1)

    @Test
    fun `first unread image story leads, then pairs with a wide card every third row`() {
        val items = (1..9).map { story("i$it", today) }

        val blocks = build(items)

        assertEquals(
            listOf(
                "day-0",
                "lead-i1",
                "pair-i2",
                "pair-i4",
                "wide-i6",
                "pair-i7",
                "wide-i9",
            ),
            blocks.map { it.key },
        )
    }

    @Test
    fun `image-less runs become quick reads and a lone one becomes a text card`() {
        val items = listOf(
            story("a", today),
            story("t1", today, image = false),
            story("t2", today, image = false),
            story("b", today),
            story("c", today),
            story("t3", today, image = false),
        )

        val blocks = build(items)

        assertEquals(
            listOf("day-0", "lead-a", "quick-t1", "pair-b", "text-t3"),
            blocks.map { it.key },
        )
        assertEquals(listOf("t1", "t2"), blocks.first { it is MagazineBlock.QuickReads }.itemIds)
    }

    @Test
    fun `days get their own sections and only the first one has a lead`() {
        val items = listOf(
            story("a", today),
            story("b", yesterday),
            story("c", yesterday),
        )

        val blocks = build(items)

        assertEquals(listOf("day-0", "lead-a", "day-1", "pair-b"), blocks.map { it.key })
        val headers = blocks.filterIsInstance<MagazineBlock.DayHeader>()
        assertEquals(listOf(today.toEpochDay(), yesterday.toEpochDay()), headers.map { it.epochDay })
    }

    @Test
    fun `read stories collapse into one row at the end of their day`() {
        val items = listOf(story("a", today)) + (1..300).map { story("r$it", today, read = true) }

        val blocks = build(items)

        assertEquals(listOf("day-0", "lead-a", "read-group-0"), blocks.map { it.key })
        val group = blocks.last() as MagazineBlock.ReadGroupHeader
        assertEquals(300, group.count)
        assertFalse(group.isExpanded)
    }

    @Test
    fun `an opened read group shows a page of rows and offers the rest`() {
        val items = (1..12).map { story("r$it", today, read = true) }

        val blocks = build(items, readRowsShown = READ_ROWS_FIRST_PAGE)

        val rows = blocks.filterIsInstance<MagazineBlock.ReadRow>()
        assertEquals(READ_ROWS_FIRST_PAGE, rows.size)
        assertFalse(rows.last().isLast)
        assertEquals(7, (blocks.last() as MagazineBlock.ShowMoreRead).remaining)
        assertTrue((blocks[1] as MagazineBlock.ReadGroupHeader).isExpanded)
    }

    @Test
    fun `a story read after grouping stays in the grid`() {
        val readLater = story("b", today, read = true)
        val items = listOf(story("a", today), readLater, story("c", today))

        val blocks = buildMagazineBlocks(
            items = items,
            isGroupedAsRead = { false },
            readRowsShown = { 0 },
            todayEpochDay = today.toEpochDay(),
            zoneId = zone,
        )

        assertEquals(listOf("day-0", "lead-a", "pair-b"), blocks.map { it.key })
        assertEquals(2, (blocks.first() as MagazineBlock.DayHeader).unreadCount)
    }

    @Test
    fun `undated stories stay with the story before them`() {
        val items = listOf(story("a", yesterday), story("b", null), story("c", yesterday))

        val blocks = build(items)

        assertEquals(1, blocks.count { it is MagazineBlock.DayHeader })
    }

    private fun build(items: List<FeedItem>, readRowsShown: Int = 0) = buildMagazineBlocks(
        items = items,
        isGroupedAsRead = { it.isRead },
        readRowsShown = { readRowsShown },
        todayEpochDay = today.toEpochDay(),
        zoneId = zone,
    )

    private fun story(id: String, day: LocalDate?, image: Boolean = true, read: Boolean = false) = FeedItem(
        id = id,
        url = "https://example.com/$id",
        title = id,
        subtitle = null,
        content = null,
        imageUrl = if (image) "https://example.com/$id.jpg" else null,
        feedSource = source,
        pubDateMillis = day?.atTime(12, 0)?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        isRead = read,
        dateString = null,
        commentsUrl = null,
        isBookmarked = false,
    )

    private val source = FeedSource(
        id = "source",
        url = "https://example.com/feed",
        title = "Example",
        category = null,
        lastSyncTimestamp = null,
        logoUrl = null,
        websiteUrl = null,
        fetchFailed = false,
        articleOpenMode = ArticleOpenMode.DEFAULT,
        isHiddenFromTimeline = false,
        isPinned = false,
        isNotificationEnabled = false,
        isHideImagesEnabled = false,
    )
}
