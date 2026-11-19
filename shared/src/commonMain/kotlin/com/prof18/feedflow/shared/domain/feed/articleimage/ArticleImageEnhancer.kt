package com.prof18.feedflow.shared.domain.feed.articleimage

import co.touchlab.kermit.Logger
import com.prof18.feedflow.core.domain.DateFormatter
import com.prof18.feedflow.core.model.ArticleImageCandidate
import com.prof18.feedflow.core.model.ArticlePageMetadata
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
 * Gives recent articles a better thumbnail when their feed has none or only a low-quality one, and
 * a summary when they have none (news sitemaps never do), from the share metadata (`og:image`,
 * `og:description`, Twitter card, ...) declared in the article page's `<head>`.
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

        val (needsPage, alreadyGood) = candidates.partition { candidate ->
            candidate.needsImage() || candidate.needsSummary()
        }
        val lookups = needsPage.take(MAX_LOOKUPS_PER_RUN)
        val semaphore = Semaphore(MAX_PARALLEL_LOOKUPS)
        val found = coroutineScope {
            lookups.map { candidate ->
                async { semaphore.withPermit { candidate.feedItemId to lookUp(candidate) } }
            }.awaitAll()
        }

        // Complete articles need no page; unreadable pages are left for a later refresh.
        val results = alreadyGood.associate { it.feedItemId to ArticlePageMetadata() } +
            found.mapNotNull { (feedItemId, metadata) -> metadata?.let { feedItemId to it } }
        if (results.isEmpty()) return
        databaseHelper.saveArticlePageLookups(results, lookedUpAt = dateFormatter.currentTimeMillis())

        val improved = results.values.count { it.imageUrl != null || it.description != null }
        if (improved > 0) {
            logger.d { "Completed $improved articles from their pages" }
            feedStateRepository.publishFetchedFeeds()
        }
    }

    /** Null when the page could not be read; otherwise what the page adds to the article. */
    private suspend fun lookUp(candidate: ArticleImageCandidate): ArticlePageMetadata? {
        val head = htmlRetriever.retrieveHead(candidate.url) ?: return null
        val image = if (candidate.needsImage()) {
            ArticleImageMetadata.extractImageUrl(head.html, head.effectiveUrl)?.takeIf { it != candidate.imageUrl }
        } else {
            null
        }
        val description = if (candidate.needsSummary()) ArticleImageMetadata.extractDescription(head.html) else null
        return ArticlePageMetadata(imageUrl = image, description = description)
    }

    private fun ArticleImageCandidate.needsImage(): Boolean =
        ArticleImageMetadata.isLowQuality(imageUrl, feedSourceLogoUrl)

    private fun ArticleImageCandidate.needsSummary(): Boolean = subtitle.isNullOrBlank()

    private companion object {
        // The newest articles not checked yet; older ones are rarely looked at again.
        const val CANDIDATE_WINDOW = 200L

        // Pages downloaded per refresh at most, a few at a time.
        const val MAX_LOOKUPS_PER_RUN = 40
        const val MAX_PARALLEL_LOOKUPS = 4
    }
}
