package com.prof18.feedflow.shared.domain.feed.articleimage

import com.prof18.feedflow.core.model.ArticleOpenMode
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.FeedItem
import com.prof18.feedflow.core.model.FeedOrder
import com.prof18.feedflow.core.model.FeedSource
import com.prof18.feedflow.database.DatabaseHelper
import com.prof18.feedflow.shared.data.SettingsRepository
import com.prof18.feedflow.shared.domain.HtmlRetriever
import com.prof18.feedflow.shared.test.KoinTestBase
import com.prof18.feedflow.shared.test.TestDispatcherProvider.testDispatcher
import com.prof18.feedflow.shared.test.buildFeedItem
import com.prof18.feedflow.shared.test.testLogger
import com.prof18.feedflow.shared.test.toParsedFeedSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.koin.test.inject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleImageEnhancerTest : KoinTestBase() {

    private val databaseHelper: DatabaseHelper by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val requestedUrls = mutableListOf<String>()

    private val source = FeedSource(
        id = "source-1",
        url = "https://example.com/rss.xml",
        title = "Example",
        category = null,
        lastSyncTimestamp = null,
        logoUrl = null,
        websiteUrl = "https://example.com",
        fetchFailed = false,
        articleOpenMode = ArticleOpenMode.DEFAULT,
        isHiddenFromTimeline = false,
        isPinned = false,
        isNotificationEnabled = false,
        isHideImagesEnabled = false,
    )

    @Test
    fun `articles without a good image get the page share image, once`() = runTest(testDispatcher) {
        settingsRepository.setArticleImageEnhancementEnabled(true)
        insert(
            buildFeedItem(id = "no-image", title = "A", pubDateMillis = 3000, source = source),
            buildFeedItem(id = "small", title = "B", pubDateMillis = 2000, source = source)
                .copy(imageUrl = "https://example.com/small-150x150.jpg"),
            buildFeedItem(id = "good", title = "C", pubDateMillis = 1000, source = source)
                .copy(imageUrl = "https://example.com/big.jpg"),
        )
        val enhancer = enhancer()

        enhancer.enhanceRecentArticles()
        enhancer.enhanceRecentArticles()

        val images = imagesById()
        assertEquals("https://example.com/media/no-image.jpg", images["no-image"])
        assertEquals("https://example.com/media/small.jpg", images["small"])
        assertEquals("https://example.com/big.jpg", images["good"])
        assertEquals(
            listOf("https://example.com/no-image", "https://example.com/small"),
            requestedUrls.sorted(),
        )
    }

    @Test
    fun `nothing is fetched while the setting is off`() = runTest(testDispatcher) {
        settingsRepository.setArticleImageEnhancementEnabled(false)
        insert(buildFeedItem(id = "no-image", title = "A", pubDateMillis = 1000, source = source))

        enhancer().enhanceRecentArticles()

        assertNull(imagesById()["no-image"])
        assertEquals(emptyList(), requestedUrls)
    }

    private fun enhancer() = ArticleImageEnhancer(
        databaseHelper = databaseHelper,
        htmlRetriever = HtmlRetriever(
            logger = testLogger,
            client = pageClient(),
            forbiddenFallbackClient = pageClient(),
        ),
        settingsRepository = settingsRepository,
        feedStateRepository = getKoin().get(),
        dateFormatter = getKoin().get(),
        logger = testLogger,
    )

    // Every page declares an og:image named after its path.
    private fun pageClient() = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val path = request.url.encodedPath
                requestedUrls += request.url.toString()
                respond(
                    content = """<html><head><meta property="og:image" content="/media$path.jpg"></head>""" +
                        "<body>${"x".repeat(1000)}</body></html>",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "text/html; charset=utf-8"),
                )
            }
        }
    }

    private suspend fun insert(vararg items: FeedItem) {
        databaseHelper.insertFeedSource(listOf(source.toParsedFeedSource()))
        databaseHelper.insertFeedItems(items.toList(), lastSyncTimestamp = 0)
    }

    private suspend fun imagesById(): Map<String, String?> = databaseHelper.getFeedItems(
        feedFilter = FeedFilter.Timeline,
        pageSize = 100,
        showReadItems = true,
        sortOrder = FeedOrder.NEWEST_FIRST,
    ).associate { it.url_hash to it.image_url }
}
