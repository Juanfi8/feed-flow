package com.prof18.feedflow.android.ui.home.components.flip

import androidx.compose.runtime.Immutable
import com.prof18.feedflow.android.ui.home.components.magazine.hasImage
import com.prof18.feedflow.core.model.FeedItem

// How far ahead the mixer may reach for a story from another source, so the order stays close to
// newest first.
private const val MIX_LOOKAHEAD = 6

// The mixer avoids repeating any of the last few sources.
private const val MIX_RECENT_SOURCES = 2

// Every third image page is a single cover story; the others pair two stories.
private const val IMAGE_PAGES_PER_COVER = 3

internal const val QUICK_READS_PER_PAGE = 3

/**
 * Turns the feed into Flip pages: a cover story, then two pages of story pairs, and so on, with
 * image-less stories gathered three to a quick-reads page. Stories that [isGroupedAsRead] get no
 * page; the end page counts them instead. With [mixSources], stories are reordered so the same
 * source rarely appears twice in a row (see [mixSources]).
 */
internal fun buildFlipPages(
    items: List<FeedItem>,
    isGroupedAsRead: (FeedItem) -> Boolean,
    mixSources: Boolean,
): List<FlipPage> {
    val (read, unread) = items.partition(isGroupedAsRead)
    val stories = if (mixSources) mixSources(unread) else unread
    val pages = mutableListOf<FlipPage>()
    val quickReads = mutableListOf<FeedItem>()
    var pendingImage: FeedItem? = null
    var imagePages = 0

    stories.forEach { story ->
        if (!story.hasImage()) {
            quickReads += story
            if (quickReads.size == QUICK_READS_PER_PAGE) {
                pages += FlipPage.QuickReads(quickReads.toList())
                quickReads.clear()
            }
            return@forEach
        }
        val waiting = pendingImage
        when {
            waiting != null -> {
                pages += FlipPage.Pair(waiting, story)
                pendingImage = null
                imagePages++
            }
            imagePages % IMAGE_PAGES_PER_COVER == 0 -> {
                pages += FlipPage.Cover(story)
                imagePages++
            }
            else -> pendingImage = story
        }
    }
    pendingImage?.let { pages += FlipPage.Cover(it) }
    if (quickReads.isNotEmpty()) pages += FlipPage.QuickReads(quickReads.toList())
    pages += FlipPage.End(readCount = read.size)
    return pages
}

/**
 * Reorders stories so the same source rarely shows up twice in a row, while staying close to the
 * original order: each next story is the first one, within a short look-ahead, whose source is not
 * among the last two used. When every candidate repeats a recent source, the oldest-waiting story
 * goes next, so nothing is pushed back indefinitely.
 */
internal fun mixSources(stories: List<FeedItem>): List<FeedItem> {
    val pending = stories.toMutableList()
    val recentSources = ArrayDeque<String>()
    val mixed = ArrayList<FeedItem>(stories.size)
    while (pending.isNotEmpty()) {
        val window = minOf(MIX_LOOKAHEAD, pending.size)
        val index = (0 until window)
            .firstOrNull { pending[it].feedSource.id !in recentSources }
            ?: 0
        val story = pending.removeAt(index)
        mixed += story
        recentSources.addLast(story.feedSource.id)
        if (recentSources.size > MIX_RECENT_SOURCES) recentSources.removeFirst()
    }
    return mixed
}

/**
 * Stories on the pages the reader flipped past, going forward from the page with [fromKey] to the
 * page at [toIndex]. Nothing when going back, or when that page is no longer there.
 */
internal fun List<FlipPage>.storiesFlippedPast(fromKey: String?, toIndex: Int): List<String> {
    val from = indexOfFirst { it.key == fromKey }
    if (from < 0 || toIndex <= from) return emptyList()
    return subList(from, toIndex.coerceAtMost(size)).flatMap { page -> page.items.map { it.id } }
}

@Immutable
internal sealed interface FlipPage {
    val key: String

    /** Stories on the page, marked read once the reader flips past it. */
    val items: List<FeedItem>

    data class Cover(val item: FeedItem) : FlipPage {
        override val key = "cover-${item.id}"
        override val items = listOf(item)
    }

    data class Pair(val first: FeedItem, val second: FeedItem) : FlipPage {
        override val key = "pair-${first.id}"
        override val items = listOf(first, second)
    }

    data class QuickReads(override val items: List<FeedItem>) : FlipPage {
        override val key = "quick-${items.first().id}"
    }

    data class End(val readCount: Int) : FlipPage {
        override val key = "end"
        override val items = emptyList<FeedItem>()
    }
}
