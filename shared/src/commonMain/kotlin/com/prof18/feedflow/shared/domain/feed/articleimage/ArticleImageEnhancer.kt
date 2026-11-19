package com.prof18.feedflow.shared.domain.feed.articleimage

import co.touchlab.kermit.Logger
import com.prof18.feedflow.core.domain.DateFormatter
import com.prof18.feedflow.core.model.ArticleImageCandidate
import com.prof18.feedflow.database.DatabaseHelper
import com.prof18.feedflow.shared.data.SettingsRepository
import com.prof18.feedflow.shared.domain.HtmlRetriever
import com.prof18.feedflow.shared.domain.feed.FeedStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Gives recent articles a better thumbnail when their feed has none or only a low-quality one, by
 * reading the share image (`og:image`, Twitter card, ...) declared in the article page's `<head>`.
 *
 * Runs after a refresh when the setting is on. Each article is checked once: the result, image or
 * not, is remembered, so pages are not downloaded again on the next refresh. Only pages that could
 * not be read are tried again later.
 */
internal class ArticleImageEnhancer(
    private val databaseHelper: DatabaseHelper,
    private val htmlRetriever: HtmlRetriever,
    private val settingsRepository: SettingsRepository,
    private val feedStateRepository: FeedStateRepository,
    private val dateFormatter: DateFormatter,
    private val logger: Logger,
) {
    suspend fun enhanceRecentArticles() {
        if (!settingsRepository.getArticleImageEnhancementEnabled()) return
        try {
            enhance()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.d(e) { "Unable to enhance article thumbnails, skipping" }
        }
    }

    private suspend fun enhance() {
        val candidates = databaseHelper.getArticleImageCandidates(limit = CANDIDATE_WINDOW)
        if (candidates.isEmpty()) return

        val (needsImage, alreadyGood) = candidates.partition { candidate ->
            ArticleImageMetadata.isLowQuality(candidate.imageUrl, candidate.feedSourceLogoUrl)
        }
        val lookups = needsImage.take(MAX_LOOKUPS_PER_RUN)
        val semaphore = Semaphore(MAX_PARALLEL_LOOKUPS)
        val found = coroutineScope {
            lookups.map { candidate ->
                async { semaphore.withPermit { candidate.feedItemId to lookUp(candidate) } }
            }.awaitAll()
        }

        // Good feed images need no page; unreadable pages are left for a later refresh.
        val results = alreadyGood.associate { it.feedItemId to null } +
            found.mapNotNull { (feedItemId, lookup) -> lookup?.let { feedItemId to it.imageUrl } }
        if (results.isEmpty()) return
        databaseHelper.saveArticleImageLookups(results, lookedUpAt = dateFormatter.currentTimeMillis())

        val improved = results.values.count { it != null }
        if (improved > 0) {
            logger.d { "Found better thumbnails for $improved articles" }
            feedStateRepository.publishFetchedFeeds()
        }
    }

    /** Null when the page could not be read; otherwise the share image, if any is better. */
    private suspend fun lookUp(candidate: ArticleImageCandidate): Lookup? {
        val head = htmlRetriever.retrieveHead(candidate.url) ?: return null
        val image = ArticleImageMetadata.extractImageUrl(head.html, head.effectiveUrl)
            ?.takeIf { it != candidate.imageUrl }
        return Lookup(image)
    }

    private data class Lookup(val imageUrl: String?)

    private companion object {
        // The newest articles not checked yet; older ones are rarely looked at again.
        const val CANDIDATE_WINDOW = 200L

        // Pages downloaded per refresh at most, a few at a time.
        const val MAX_LOOKUPS_PER_RUN = 40
        const val MAX_PARALLEL_LOOKUPS = 4
    }
}
