package com.prof18.feedflow.android.home

import androidx.annotation.StringRes
import com.prof18.feedflow.android.R
import com.prof18.feedflow.core.model.FeedLayout

/**
 * The style the overflow menu switches to. It alternates between Card and Magazine; List is left
 * out of the rotation, so from List it moves on to Card.
 */
internal fun FeedLayout.nextFeedStyle(): FeedLayout = when (this) {
    FeedLayout.CARD -> FeedLayout.BIG_IMAGE
    FeedLayout.LIST, FeedLayout.BIG_IMAGE, FeedLayout.GRID -> FeedLayout.CARD
}

/** Android name of a feed style; "Big image" is presented as Magazine. */
@StringRes
internal fun FeedLayout.styleNameRes(): Int = when (this) {
    FeedLayout.LIST -> R.string.feed_style_list
    FeedLayout.CARD -> R.string.feed_style_card
    FeedLayout.BIG_IMAGE, FeedLayout.GRID -> R.string.feed_style_magazine
}
