package com.prof18.feedflow.android.ui.home.components.flip

import com.prof18.feedflow.core.model.ArticleOpenMode
import com.prof18.feedflow.core.model.FeedItem
import com.prof18.feedflow.core.model.FeedSource
import org.junit.Assert.assertEquals
import org.junit.Test

class FlipLayoutTest {

    @Test
    fun `image stories alternate a cover with two pages of pairs`() {
        val items = (1..8).map { story("i$it") }

        val pages = buildFlipPages(items, isGroupedAsRead = { false }, mixSources = false)

        assertEquals(
            listOf("cover-i1", "pair-i2", "pair-i4", "cover-i6", "pair-i7", "end"),
            pages.map { it.key },
        )
    }

    @Test
    fun `image-less stories fill quick-reads pages of three`() {
        val items = listOf(
            story("i1"),
            story("t1", image = false),
            story("t2", image = false),
            story("i2"),
            story("t3", image = false),
            story("t4", image = false),
        )

        val pages = buildFlipPages(items, isGroupedAsRead = { false }, mixSources = false)

        assertEquals(listOf("cover-i1", "quick-t1", "cover-i2", "quick-t4", "end"), pages.map { it.key })
        assertEquals(listOf("t1", "t2", "t3"), pages[1].items.map { it.id })
    }

    @Test
    fun `read stories get no page and are counted on the end page`() {
        val items = listOf(story("i1"), story("r1", read = true), story("r2", read = true))

        val pages = buildFlipPages(items, isGroupedAsRead = { it.isRead }, mixSources = false)

        assertEquals(listOf("cover-i1", "end"), pages.map { it.key })
        assertEquals(2, (pages.last() as FlipPage.End).readCount)
    }

    @Test
    fun `mixing breaks up runs from one source`() {
        val items = listOf(
            story("a1", source = "a"),
            story("a2", source = "a"),
            story("a3", source = "a"),
            story("b1", source = "b"),
            story("c1", source = "c"),
            story("a4", source = "a"),
        )

        val mixed = mixSources(items)

        assertEquals(listOf("a1", "b1", "c1", "a2", "a3", "a4"), mixed.map { it.id })
    }

    @Test
    fun `mixing keeps every story and the order when sources already vary`() {
        val items = listOf(story("a1", source = "a"), story("b1", source = "b"), story("a2", source = "a"))

        assertEquals(items, mixSources(items))
    }

    @Test
    fun `mixing never pushes a story further than the look-ahead`() {
        val items = (1..10).map { story("a$it", source = "a") } + story("b1", source = "b")

        val mixed = mixSources(items)

        assertEquals(items.toSet(), mixed.toSet())
        assertEquals("a1", mixed.first().id)
    }

    private fun story(id: String, image: Boolean = true, read: Boolean = false, source: String = "s") = FeedItem(
        id = id,
        url = "https://example.com/$id",
        title = id,
        subtitle = null,
        content = null,
        imageUrl = if (image) "https://example.com/$id.jpg" else null,
        feedSource = FeedSource(
            id = source,
            url = "https://$source.example/feed",
            title = source,
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
        ),
        pubDateMillis = null,
        isRead = read,
        dateString = null,
        commentsUrl = null,
        isBookmarked = false,
    )
}
