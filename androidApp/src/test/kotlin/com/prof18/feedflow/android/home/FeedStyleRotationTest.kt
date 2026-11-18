package com.prof18.feedflow.android.home

import com.prof18.feedflow.android.R
import com.prof18.feedflow.core.model.FeedLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedStyleRotationTest {
    @Test
    fun `menu alternates between card and magazine`() {
        assertEquals(FeedLayout.BIG_IMAGE, FeedLayout.CARD.nextFeedStyle())
        assertEquals(FeedLayout.CARD, FeedLayout.BIG_IMAGE.nextFeedStyle())
    }

    @Test
    fun `list is left out of the rotation`() {
        assertEquals(FeedLayout.CARD, FeedLayout.LIST.nextFeedStyle())
        val visited = generateSequence(FeedLayout.LIST.nextFeedStyle()) { it.nextFeedStyle() }.take(6).toSet()
        assertEquals(setOf(FeedLayout.CARD, FeedLayout.BIG_IMAGE), visited)
    }

    @Test
    fun `big image is presented as magazine`() {
        assertEquals(R.string.feed_style_magazine, FeedLayout.BIG_IMAGE.styleNameRes())
    }
}
