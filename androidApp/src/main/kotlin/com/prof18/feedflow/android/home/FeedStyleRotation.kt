package com.prof18.feedflow.android.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prof18.feedflow.android.R
import com.prof18.feedflow.core.model.FeedLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.koinInject

/**
 * The feed styles the Android app offers. Magazine and Flip both use the shared "big image"
 * layout, so image-related settings and sync keep working; Flip is an Android-only flag on top.
 */
enum class FeedStyle(val layout: FeedLayout) {
    LIST(FeedLayout.LIST),
    CARD(FeedLayout.CARD),
    MAGAZINE(FeedLayout.BIG_IMAGE),
    FLIP(FeedLayout.BIG_IMAGE),
    ;

    /** Pages are flipped rather than scrolled. */
    val isPaged: Boolean get() = this == FLIP

    /** Styles whose stories have no per-article swipe actions and sit under day headings. */
    val isEditorial: Boolean get() = this == MAGAZINE || this == FLIP

    companion object {
        fun of(layout: FeedLayout, isFlipEnabled: Boolean): FeedStyle = when (layout) {
            FeedLayout.LIST -> LIST
            FeedLayout.CARD -> CARD
            FeedLayout.BIG_IMAGE, FeedLayout.GRID -> if (isFlipEnabled) FLIP else MAGAZINE
        }
    }
}

/**
 * The style the overflow menu switches to: Card, Magazine, Flip, then back to Card. List is left
 * out of the rotation, so from List it moves on to Card.
 */
internal fun FeedStyle.nextFeedStyle(): FeedStyle = when (this) {
    FeedStyle.LIST, FeedStyle.FLIP -> FeedStyle.CARD
    FeedStyle.CARD -> FeedStyle.MAGAZINE
    FeedStyle.MAGAZINE -> FeedStyle.FLIP
}

@StringRes
internal fun FeedStyle.styleNameRes(): Int = when (this) {
    FeedStyle.LIST -> R.string.feed_style_list
    FeedStyle.CARD -> R.string.feed_style_card
    FeedStyle.MAGAZINE -> R.string.feed_style_magazine
    FeedStyle.FLIP -> R.string.feed_style_flip
}

/** Android-only part of the feed style: whether the big-image layout is shown as Flip pages. */
class FeedStyleSettings(context: Context) {
    private val preferences = context.getSharedPreferences("feed_style", Context.MODE_PRIVATE)
    private val flipMutableState = MutableStateFlow(preferences.getBoolean(KEY_FLIP, false))
    val isFlipEnabled: StateFlow<Boolean> = flipMutableState.asStateFlow()

    /** Saves the Android part of [style]; the caller saves `style.layout` in the shared settings. */
    internal fun select(style: FeedStyle) {
        val flip = style == FeedStyle.FLIP
        preferences.edit().putBoolean(KEY_FLIP, flip).apply()
        flipMutableState.value = flip
    }

    private companion object {
        const val KEY_FLIP = "flip"
    }
}

/** The current feed style, combining the shared layout with the Android-only Flip flag. */
@Composable
internal fun rememberFeedStyle(feedLayout: FeedLayout): FeedStyle {
    val settings = koinInject<FeedStyleSettings>()
    val isFlipEnabled by settings.isFlipEnabled.collectAsStateWithLifecycle()
    return FeedStyle.of(feedLayout, isFlipEnabled)
}
