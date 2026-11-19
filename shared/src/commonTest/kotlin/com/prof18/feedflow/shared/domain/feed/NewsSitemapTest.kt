package com.prof18.feedflow.shared.domain.feed

import co.touchlab.kermit.Logger
import com.prof18.feedflow.core.model.FeedFetchTier
import com.prof18.feedflow.shared.domain.feed.httpcache.FeedHttpCacheStore
import com.prof18.feedflow.shared.test.generators.RssChannelGenerator
import com.prof18.rssparser.exception.HttpException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class NewsSitemapTest {

    private val sitemapUrl = "https://news.example.com/sitemap/news.xml"

    // Same shape as a real Google News sitemap (urlset with news, image and video namespaces).
    private val sitemap = """
        <?xml version="1.0" encoding="UTF-8"?>
        <urlset xmlns:image="http://www.google.com/schemas/sitemap-image/1.1"
                xmlns:news="http://www.google.com/schemas/sitemap-news/0.9"
                xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
           <url>
              <loc>https://news.example.com/2026/11/17/world/older-story</loc>
              <lastmod>2026-11-19T09:05:36.108000+00:00</lastmod>
              <news:news>
                 <news:publication><news:name>Example News</news:name><news:language>es</news:language></news:publication>
                 <news:publication_date>2026-11-17T06:25:29.913Z</news:publication_date>
                 <news:title>Older story</news:title>
              </news:news>
              <image:image><image:loc>https://media.example.com/older.jpg?c=original&amp;q=w_1200</image:loc></image:image>
           </url>
           <url>
              <loc>https://news.example.com/2026/11/18/world/newer-video</loc>
              <lastmod>2026-11-19T03:25:10.246000+00:00</lastmod>
              <news:news>
                 <news:publication><news:name>Example News</news:name><news:language>es</news:language></news:publication>
                 <news:title>Newer story without a date</news:title>
              </news:news>
           </url>
           <url>
              <loc>https://news.example.com/about</loc>
           </url>
        </urlset>
    """.trimIndent()

    @Test
    fun `news sitemap entries become articles, newest first`() {
        val channel = NewsSitemapParser.parse(sitemap, sitemapUrl)

        requireNotNull(channel)
        assertEquals("Example News", channel.title)
        assertEquals("https://news.example.com", channel.link)
        assertEquals(
            listOf("Newer story without a date", "Older story"),
            channel.items.map { it.title },
        )
        val newer = channel.items[0]
        assertEquals("https://news.example.com/2026/11/18/world/newer-video", newer.link)
        assertEquals("2026-11-19T03:25:10.246000+00:00", newer.pubDate)
        assertNull(newer.image)
        val older = channel.items[1]
        assertEquals("2026-11-17T06:25:29.913Z", older.pubDate)
        assertEquals("https://media.example.com/older.jpg?c=original&q=w_1200", older.image)
        assertEquals(older.link, older.guid)
    }

    @Test
    fun `plain sitemaps and other documents are not news feeds`() {
        val plain = """<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""" +
            "<url><loc>https://a.example/x</loc></url></urlset>"

        assertNull(NewsSitemapParser.parse(plain, sitemapUrl))
        assertNull(NewsSitemapParser.parse("<rss><channel><title>Feed</title></channel></rss>", sitemapUrl))
    }

    @Test
    fun `a url the feed parser finds empty is read as a news sitemap`() = runTest {
        val sitemapChannel = RssChannelGenerator.rssChannel(title = "Sitemap")
        val wrapper = wrapper(
            parser = { RssChannelGenerator.rssChannel(items = emptyList()) },
            readNewsSitemap = { sitemapChannel },
        )

        assertSame(sitemapChannel, wrapper.getRssChannel(sitemapUrl))
    }

    @Test
    fun `a url the feed parser rejects is read as a news sitemap`() = runTest {
        val sitemapChannel = RssChannelGenerator.rssChannel(title = "Sitemap")
        val wrapper = wrapper(
            parser = { error("Not an RSS document") },
            readNewsSitemap = { sitemapChannel },
        )

        assertSame(sitemapChannel, wrapper.getRssChannel(sitemapUrl))
    }

    @Test
    fun `parser errors stay errors when the url is no sitemap either`() = runTest {
        val wrapper = wrapper(parser = { error("Not an RSS document") }, readNewsSitemap = { null })

        assertFailsWith<IllegalStateException> { wrapper.getRssChannel(sitemapUrl) }
    }

    @Test
    fun `http errors skip the sitemap attempt`() = runTest {
        var sitemapAttempts = 0
        val wrapper = wrapper(
            parser = { throw HttpException(code = 404, message = "Not Found") },
            readNewsSitemap = {
                sitemapAttempts++
                null
            },
        )

        assertFailsWith<HttpException> { wrapper.getRssChannel(sitemapUrl) }
        assertEquals(0, sitemapAttempts)
    }

    private fun wrapper(
        parser: suspend (String) -> com.prof18.rssparser.model.RssChannel,
        readNewsSitemap: suspend (String) -> com.prof18.rssparser.model.RssChannel?,
    ) = RssParserWrapperImpl(
        feedHttpCacheStore = FeedHttpCacheStore(currentTimeMillis = { 0L }, logger = Logger.withTag("SitemapTest")),
        parsers = mapOf(FeedFetchTier.PRIMARY to parser),
        readNewsSitemap = readNewsSitemap,
    )
}
