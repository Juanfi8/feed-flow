package com.prof18.feedflow.core.model

/** An article that may get a better thumbnail from its page's metadata. */
data class ArticleImageCandidate(
    val feedItemId: String,
    val url: String,
    val imageUrl: String?,
    val feedSourceLogoUrl: String?,
)
