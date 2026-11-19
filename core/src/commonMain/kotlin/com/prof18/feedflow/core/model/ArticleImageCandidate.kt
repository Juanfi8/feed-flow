package com.prof18.feedflow.core.model

/** An article that may get a better thumbnail or a summary from its page's metadata. */
data class ArticleImageCandidate(
    val feedItemId: String,
    val url: String,
    val imageUrl: String?,
    val subtitle: String?,
    val feedSourceLogoUrl: String?,
)

/** What an article page's metadata adds to the article; null fields keep what it has. */
data class ArticlePageMetadata(
    val imageUrl: String? = null,
    val description: String? = null,
)
