package com.prof18.feedflow.shared.domain.feed

import com.prof18.feedflow.core.model.FeedFetchTier
import com.prof18.feedflow.shared.domain.feed.httpcache.FeedHttpCacheStore
import com.prof18.feedflow.shared.domain.recovery.FeedVerificationRequired
import com.prof18.rssparser.exception.HttpException
import com.prof18.rssparser.model.RssChannel
import kotlinx.coroutines.CancellationException

internal interface RssParserWrapper {
    suspend fun getRssChannel(url: String, allowBrowserTier: Boolean = true): RssChannel
}

internal class RssParserWrapperImpl(
    private val feedHttpCacheStore: FeedHttpCacheStore,
    private val parsers: Map<FeedFetchTier, suspend (String) -> RssChannel>,
    private val readNewsSitemap: suspend (String) -> RssChannel? = { null },
) : RssParserWrapper {
    override suspend fun getRssChannel(url: String, allowBrowserTier: Boolean): RssChannel {
        val permitted = FeedFetchTier.entries.filter { it != FeedFetchTier.BROWSER || allowBrowserTier }
        val remembered = feedHttpCacheStore.tierFor(url)?.takeIf { it in permitted }
        val tiers = listOfNotNull(remembered) + permitted.filter { it != remembered }
        var lastForbidden: Throwable? = null
        for (tier in tiers) {
            val parser = parsers[tier] ?: continue
            try {
                val channel = parseOrReadSitemap(parser, url)
                feedHttpCacheStore.recordSuccessfulTier(url, tier)
                return channel
            } catch (error: HttpException) {
                val challenge = feedHttpCacheStore.challengeFor(url)
                lastForbidden = when {
                    challenge != null -> FeedVerificationRequired(challenge.copy(requestedUrl = url))
                    error.code == HTTP_FORBIDDEN -> error
                    else -> throw error
                }
            }
        }
        throw checkNotNull(lastForbidden) { "No fetch tier ran for $url" }
    }

    /**
     * Some publishers only offer a Google News sitemap. The feed parser finds no articles in it
     * (or rejects it), so in that case the URL is read again as a news sitemap.
     */
    private suspend fun parseOrReadSitemap(parser: suspend (String) -> RssChannel, url: String): RssChannel {
        val channel = try {
            parser(url)
        } catch (e: HttpException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return readNewsSitemap(url) ?: throw e
        }
        return if (channel.items.isEmpty()) readNewsSitemap(url) ?: channel else channel
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }
}
