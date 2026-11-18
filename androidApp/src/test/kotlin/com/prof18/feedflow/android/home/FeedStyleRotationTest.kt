package com.prof18.feedflow.android.home

import com.prof18.feedflow.android.R
import com.prof18.feedflow.core.model.FeedLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedStyleRotationTest {
    @Test
    fun `menu rotates card, magazine and flip`() {
        assertEquals(FeedStyle.MAGAZINE, FeedStyle.CARD.nextFeedStyle())
        assertEquals(FeedStyle.FLIP, FeedStyle.MAGAZINE.nextFeedStyle())
        assertEquals(FeedStyle.CARD, FeedStyle.FLIP.nextFeedStyle())
    }

    @Test
    fun `list is left out of the rotation`() {
        assertEquals(FeedStyle.CARD, FeedStyle.LIST.nextFeedStyle())
        val visited = generateSequence(FeedStyle.LIST.nextFeedStyle()) { it.nextFeedStyle() }.take(9).toSet()
        assertEquals(setOf(FeedStyle.CARD, FeedStyle.MAGAZINE, FeedStyle.FLIP), visited)
    }

    @Test
    fun `magazine and flip share the big image layout`() {
        assertEquals(FeedStyle.MAGAZINE, FeedStyle.of(FeedLayout.BIG_IMAGE, isFlipEnabled = false))
        assertEquals(FeedStyle.FLIP, FeedStyle.of(FeedLayout.BIG_IMAGE, isFlipEnabled = true))
        assertEquals(FeedStyle.CARD, FeedStyle.of(FeedLayout.CARD, isFlipEnabled = true))
        assertEquals(FeedLayout.BIG_IMAGE, FeedStyle.FLIP.layout)
    }

    @Test
    fun `only flip is paged and both editorial styles drop swipe settings`() {
        assertTrue(FeedStyle.FLIP.isPaged)
        assertFalse(FeedStyle.MAGAZINE.isPaged)
        assertTrue(FeedStyle.MAGAZINE.isEditorial)
        assertFalse(FeedStyle.CARD.isEditorial)
        assertEquals(R.string.feed_style_flip, FeedStyle.FLIP.styleNameRes())
    }
}
