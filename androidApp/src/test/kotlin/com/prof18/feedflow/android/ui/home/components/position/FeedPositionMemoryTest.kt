package com.prof18.feedflow.android.ui.home.components.position

import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedSourceCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPositionMemoryTest {

    private val news = FeedFilter.Category(FeedSourceCategory(id = "news", title = "News"))
    private val france = FeedFilter.Category(FeedSourceCategory(id = "france", title = "France"))

    @After
    fun tearDown() = FeedPositionStore.clear()

    @Test
    fun `each tab and style keeps its own place`() {
        FeedPositionStore.save(news, "magazine", SavedFeedPosition("pair-a", listOf("a", "b"), offset = 40))
        FeedPositionStore.save(france, "magazine", SavedFeedPosition("cover-x", listOf("x")))
        FeedPositionStore.save(news, "flip", SavedFeedPosition("cover-c", listOf("c")))

        assertEquals("pair-a", FeedPositionStore.get(news, "magazine")?.key)
        assertEquals(40, FeedPositionStore.get(news, "magazine")?.offset)
        assertEquals("cover-x", FeedPositionStore.get(france, "magazine")?.key)
        assertEquals("cover-c", FeedPositionStore.get(news, "flip")?.key)
    }

    @Test
    fun `forgetting a tab clears it in every style and leaves the others`() {
        FeedPositionStore.save(news, "magazine", SavedFeedPosition("pair-a", listOf("a")))
        FeedPositionStore.save(news, "flip", SavedFeedPosition("cover-c", listOf("c")))
        FeedPositionStore.save(france, "flip", SavedFeedPosition("cover-x", listOf("x")))

        FeedPositionStore.forget(news)

        assertNull(FeedPositionStore.get(news, "magazine"))
        assertNull(FeedPositionStore.get(news, "flip"))
        assertEquals("cover-x", FeedPositionStore.get(france, "flip")?.key)
    }

    @Test
    fun `the saved row is found by key, then by one of its stories`() {
        val rows = listOf(Row("day-0", emptyList()), Row("pair-a", listOf("a", "b")), Row("wide-c", listOf("c")))

        assertEquals(1, rows.indexOfSaved(SavedFeedPosition("pair-a", listOf("a")), Row::key, Row::ids))
        // After regrouping "b" now opens a different row.
        val regrouped = listOf(Row("day-0", emptyList()), Row("pair-b", listOf("b", "c")))
        assertEquals(1, regrouped.indexOfSaved(SavedFeedPosition("pair-a", listOf("a", "b")), Row::key, Row::ids))
        assertNull(rows.indexOfSaved(SavedFeedPosition("gone", listOf("z")), Row::key, Row::ids))
    }

    @Test
    fun `read folding decisions last until the tab is reloaded`() {
        ReadGroupingStore.decisionsFor(news)["a"] = true

        assertEquals(true, ReadGroupingStore.decisionsFor(news)["a"])
        assertTrue(ReadGroupingStore.decisionsFor(france).isEmpty())

        ReadGroupingStore.forget(news)

        assertTrue(ReadGroupingStore.decisionsFor(news).isEmpty())
    }

    @Test
    fun `filters have distinct keys`() {
        val keys = listOf(
            FeedFilter.Timeline,
            FeedFilter.Read,
            FeedFilter.Bookmarks,
            FeedFilter.Uncategorized,
            news,
            france,
        ).map { it.positionKey() }

        assertEquals(keys.size, keys.toSet().size)
    }

    private data class Row(val key: String, val ids: List<String>)
}
