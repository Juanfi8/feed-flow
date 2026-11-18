package com.prof18.feedflow.android.home.tabs

import androidx.compose.runtime.Immutable
import com.prof18.feedflow.core.model.DrawerItem
import com.prof18.feedflow.core.model.FeedFilter
import com.prof18.feedflow.core.model.NavDrawerState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Builds the home tab strip from the drawer state: "All feeds" first, then one tab per category in
 * drawer order (Uncategorized included where the drawer shows it). Without categories there is
 * nothing to switch between, so no tabs are returned and the home screen keeps its plain header.
 */
internal fun buildHomeTabs(navDrawerState: NavDrawerState): ImmutableList<HomeTab> {
    if (navDrawerState.feedSourcesByCategory.isEmpty()) return persistentListOf()

    val timelineCount = navDrawerState.timeline
        .filterIsInstance<DrawerItem.Timeline>()
        .firstOrNull()
        ?.unreadCount ?: 0L
    val categoryCounts = navDrawerState.categories
        .filterIsInstance<DrawerItem.DrawerCategory>()
        .associate { it.category.id to it.unreadCount }

    val categoryTabs = navDrawerState.feedSourcesByCategory.map { (wrapper, items) ->
        val feeds = items.filterIsInstance<DrawerItem.DrawerFeedSource>().toImmutableList()
        val category = wrapper.feedSourceCategory
        if (category == null) {
            HomeTab(
                filter = FeedFilter.Uncategorized,
                title = null,
                unreadCount = feeds.sumOf { it.unreadCount },
                feeds = feeds,
            )
        } else {
            HomeTab(
                filter = FeedFilter.Category(category),
                title = category.title,
                unreadCount = categoryCounts[category.id] ?: feeds.sumOf { it.unreadCount },
                feeds = feeds,
            )
        }
    }

    return (
        listOf(
            HomeTab(
                filter = FeedFilter.Timeline,
                title = null,
                unreadCount = timelineCount,
                feeds = persistentListOf(),
            ),
        ) + categoryTabs
        ).toImmutableList()
}

/** Index of the tab that owns [filter], or -1 for filters outside the strip (Read, Bookmarks). */
internal fun List<HomeTab>.selectedTabIndex(filter: FeedFilter): Int = indexOfFirst { tab ->
    tab.owns(filter)
}

/** Feed id of the selected chip, or null when the whole tab ("All") is selected. */
internal fun FeedFilter.selectedFeedId(): String? = (this as? FeedFilter.Source)?.feedSource?.id

/**
 * The tab one step away from [currentIndex], or null at either end. From a filter outside the
 * strip (-1), moving forward lands on the first tab.
 */
internal fun List<HomeTab>.stepFrom(currentIndex: Int, forward: Boolean): Int? {
    val target = when {
        currentIndex < 0 -> if (forward) 0 else return null
        forward -> currentIndex + 1
        else -> currentIndex - 1
    }
    return target.takeIf { it in indices }
}

private fun HomeTab.owns(filter: FeedFilter): Boolean = when (val own = this.filter) {
    FeedFilter.Timeline -> filter is FeedFilter.Timeline
    FeedFilter.Uncategorized ->
        filter is FeedFilter.Uncategorized || (filter is FeedFilter.Source && filter.feedSource.category == null)
    is FeedFilter.Category -> when (filter) {
        is FeedFilter.Category -> filter.feedCategory.id == own.feedCategory.id
        is FeedFilter.Source -> filter.feedSource.category?.id == own.feedCategory.id
        else -> false
    }
    else -> false
}

@Immutable
internal data class HomeTab(
    /** Timeline, Category or Uncategorized. */
    val filter: FeedFilter,
    /** Category title; null for "All feeds" and "Uncategorized", whose labels are localized. */
    val title: String?,
    val unreadCount: Long,
    val feeds: ImmutableList<DrawerItem.DrawerFeedSource>,
)
