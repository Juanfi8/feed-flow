package com.prof18.feedflow.android.home.tabs

import com.prof18.feedflow.core.model.ArticleOpenMode
import com.prof18.feedflow.core.model.DrawerItem
import com.prof18.feedflow.core.model.DrawerItem.DrawerFeedSource.FeedSourceCategoryWrapper
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedSource
import com.prof18.feedflow.core.model.FeedSourceCategory
import com.prof18.feedflow.core.model.NavDrawerState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTabsTest {
    private val news = FeedSourceCategory(id = "news", title = "News")
    private val france = FeedSourceCategory(id = "france", title = "France")
    private val elComercio = feedSource("ec", news)
    private val leMonde = feedSource("lm", france)
    private val loose = feedSource("loose", null)

    private val state = NavDrawerState(
        timeline = persistentListOf(DrawerItem.Timeline(unreadCount = 30)),
        categories = persistentListOf(
            DrawerItem.DrawerCategory(news, unreadCount = 12),
            DrawerItem.DrawerCategory(france, unreadCount = 8),
        ),
        feedSourcesByCategory = persistentMapOf(
            FeedSourceCategoryWrapper(france) to listOf(DrawerItem.DrawerFeedSource(leMonde, 8)),
            FeedSourceCategoryWrapper(null) to listOf(DrawerItem.DrawerFeedSource(loose, 10)),
            FeedSourceCategoryWrapper(news) to listOf(DrawerItem.DrawerFeedSource(elComercio, 12)),
        ),
    )

    @Test
    fun `tabs start with all feeds and follow drawer category order`() {
        val tabs = buildHomeTabs(state)

        assertEquals(
            listOf(
                FeedFilter.Timeline,
                FeedFilter.Category(france),
                FeedFilter.Uncategorized,
                FeedFilter.Category(news),
            ),
            tabs.map { it.filter },
        )
        assertEquals(listOf(30L, 8L, 10L, 12L), tabs.map { it.unreadCount })
        assertEquals(listOf("lm"), tabs[1].feeds.map { it.feedSource.id })
    }

    @Test
    fun `no categories means no tabs`() {
        assertTrue(buildHomeTabs(NavDrawerState()).isEmpty())
    }

    @Test
    fun `a single feed selects the tab of its category`() {
        val tabs = buildHomeTabs(state)

        assertEquals(0, tabs.selectedTabIndex(FeedFilter.Timeline))
        assertEquals(3, tabs.selectedTabIndex(FeedFilter.Category(news.copy(title = "Renamed"))))
        assertEquals(3, tabs.selectedTabIndex(FeedFilter.Source(elComercio)))
        assertEquals(2, tabs.selectedTabIndex(FeedFilter.Source(loose)))
        assertEquals(-1, tabs.selectedTabIndex(FeedFilter.Bookmarks))
    }

    @Test
    fun `stepping stops at both ends and enters the strip from outside it`() {
        val tabs = buildHomeTabs(state)

        assertEquals(1, tabs.stepFrom(0, forward = true))
        assertNull(tabs.stepFrom(0, forward = false))
        assertNull(tabs.stepFrom(3, forward = true))
        assertEquals(0, tabs.stepFrom(-1, forward = true))
        assertNull(tabs.stepFrom(-1, forward = false))
    }

    @Test
    fun `only a source filter selects a feed chip`() {
        assertEquals("ec", FeedFilter.Source(elComercio).selectedFeedId())
        assertNull(FeedFilter.Category(news).selectedFeedId())
    }

    private fun feedSource(id: String, category: FeedSourceCategory?) = FeedSource(
        id = id,
        url = "https://$id.example/feed",
        title = id,
        category = category,
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
