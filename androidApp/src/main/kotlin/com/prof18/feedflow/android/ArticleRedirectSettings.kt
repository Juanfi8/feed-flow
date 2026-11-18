package com.prof18.feedflow.android

import android.content.Context
import android.net.Uri
import org.json.JSONArray

/** Android-only settings for explicit article redirect actions. */
internal class ArticleRedirectSettings(context: Context) {
    private val preferences = context.getSharedPreferences("article_redirects", Context.MODE_PRIVATE)

    fun prefixes(): List<String> = runCatching {
        val array = JSONArray(preferences.getString("prefixes", "[]"))
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    fun savePrefixes(values: List<String>) {
        val clean = values.map(String::trim).filter(String::isNotEmpty).distinct()
        require(clean.all(::isValidRedirectPrefix))
        preferences.edit().putString("prefixes", JSONArray(clean).toString()).apply()
    }

    fun defaultPrefix(): String? = preferences.getString("default", null)?.takeIf { it in prefixes() }

    fun setDefaultPrefix(prefix: String?) {
        require(prefix == null || prefix in prefixes())
        preferences.edit().putString("default", prefix).apply()
    }

    fun feedOverride(feedId: String): String? =
        preferences.getString("feed:$feedId", null)?.takeIf { it in prefixes() }

    fun prefixForFeed(feedId: String): String? = feedOverride(feedId) ?: defaultPrefix()

    fun redirectsOrdinaryTaps(feedId: String): Boolean = preferences.getBoolean("auto:$feedId", false)

    fun setRedirectsOrdinaryTaps(feedId: String, enabled: Boolean) {
        preferences.edit().putBoolean("auto:$feedId", enabled).apply()
    }

    fun setFeedPrefix(feedId: String, prefix: String?) {
        require(prefix == null || prefix in prefixes())
        preferences.edit().putString("feed:$feedId", prefix).apply()
    }
}

internal fun isValidRedirectPrefix(prefix: String): Boolean {
    val uri = runCatching { Uri.parse(prefix) }.getOrNull() ?: return false
    return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && !prefix.any(Char::isWhitespace)
}
