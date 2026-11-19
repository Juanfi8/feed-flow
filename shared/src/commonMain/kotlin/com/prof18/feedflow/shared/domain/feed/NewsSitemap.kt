package com.prof18.feedflow.shared.domain.feed

import co.touchlab.kermit.Logger
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.parser.Parser
import com.prof18.feedflow.shared.domain.HtmlRetrievalResult
import com.prof18.feedflow.shared.domain.HtmlRetriever
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem

/**
 * Reads Google News sitemaps (`<urlset>` with `news:` entries), which some publishers offer
 * instead of RSS, as a regular feed: each entry becomes an article with its link, headline,
 * publication date and image. Sitemaps carry no summaries.
 */
internal class NewsSitemapReader(
    private val htmlRetriever: HtmlRetriever,
    private val logger: Logger,
) {
    /** The sitemap at [url] as a feed, or null when the URL is not a news sitemap. */
    suspend fun read(url: String): RssChannel? {
        val content = htmlRetriever.retrieveResult(url) as? HtmlRetrievalResult.Content ?: return null
        val channel = NewsSitemapParser.parse(content.html, content.effectiveUrl)
        if (channel != null) logger.d { "Read ${channel.items.size} articles from the news sitemap at $url" }
        return channel
    }
}

internal object NewsSitemapParser {

    fun parse(xml: String, sitemapUrl: String): RssChannel? {
        if (!xml.contains("<urlset", ignoreCase = true)) return null
        val document = try {
            Ksoup.parse(xml, Parser.xmlParser(), sitemapUrl)
        } catch (_: Exception) {
            return null
        }
        val entries = document.getElementsByTag("url").mapNotNull(::toEntry)
        if (entries.isEmpty()) return null

        val publicationName = document.getElementsByTag("news:name").firstOrNull()?.text()?.trim()
        val siteUrl = siteRoot(sitemapUrl)
        return RssChannel(
            title = publicationName?.takeIf { it.isNotEmpty() } ?: siteUrl?.substringAfter("://"),
            link = siteUrl,
            description = null,
            image = null,
            lastBuildDate = null,
            updatePeriod = null,
            items = entries.sortedByDescending { it.date.orEmpty() }.map { it.toRssItem() },
            itunesChannelData = null,
            youtubeChannelData = null,
        )
    }

    // Only entries with a news headline are articles; plain sitemap URLs have nothing to show.
    private fun toEntry(url: Element): SitemapEntry? {
        val link = url.childText("loc") ?: return null
        val news = url.getElementsByTag("news:news").firstOrNull() ?: return null
        val title = news.getElementsByTag("news:title").firstOrNull()?.text()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null
        val date = news.getElementsByTag("news:publication_date").firstOrNull()?.text()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: url.childText("lastmod")
        val image = url.getElementsByTag("image:loc").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() }
        return SitemapEntry(link = link, title = title, date = date, image = image)
    }

    private fun Element.childText(tag: String): String? =
        children()
            .firstOrNull { child -> child.tagName().equals(tag, ignoreCase = true) }
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun siteRoot(url: String): String? {
        val scheme = url.substringBefore("://", missingDelimiterValue = "")
        if (scheme != "http" && scheme != "https") return null
        val host = url.substringAfter("://").substringBefore('/')
        return "$scheme://$host"
    }

    private data class SitemapEntry(val link: String, val title: String, val date: String?, val image: String?) {
        fun toRssItem() = RssItem(
            guid = link,
            title = title,
            author = null,
            link = link,
            pubDate = date,
            description = null,
            content = null,
            image = image,
            audio = null,
            video = null,
            sourceName = null,
            sourceUrl = null,
            categories = emptyList(),
            itunesItemData = null,
            commentsUrl = null,
            youtubeItemData = null,
            rawEnclosure = null,
        )
    }
}
