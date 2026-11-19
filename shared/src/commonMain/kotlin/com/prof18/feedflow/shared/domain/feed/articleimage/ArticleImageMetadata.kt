package com.prof18.feedflow.shared.domain.feed.articleimage

import com.fleeksoft.ksoup.Ksoup

/**
 * Finds an article's share image in its page metadata and tells apart thumbnails too small or too
 * generic to show as a story image.
 */
internal object ArticleImageMetadata {

    // Images whose URL says they are this small (in either dimension) count as low quality.
    private const val MIN_IMAGE_SIDE = 300

    // In order of preference: the share image, its Twitter card twin, then older conventions.
    private val imageSelectors = listOf(
        "meta[property=og:image:secure_url]" to "content",
        "meta[property=og:image]" to "content",
        "meta[name=og:image]" to "content",
        "meta[property=og:image:url]" to "content",
        "meta[name=twitter:image]" to "content",
        "meta[property=twitter:image]" to "content",
        "meta[name=twitter:image:src]" to "content",
        "link[rel=image_src]" to "href",
        "meta[itemprop=image]" to "content",
    )

    private val genericMarkers = listOf(
        "favicon",
        "1x1",
        "spacer",
        "pixel.",
        "/pixel",
        "blank.",
        "placeholder",
        "default-image",
        "default_image",
        "/logo",
        "logo.",
        "logo_",
        "logo-",
        "avatar",
        "gravatar.com",
        "/icons/",
        "/emoji/",
    )

    // Hints that a feed image is a reduced copy. Share images are full size even when their path
    // mentions thumbnails, so these only judge the feed's own image.
    private val smallMarkers = listOf("thumb", "-small.", "_small.", "/small/")

    private val wordpressSize = Regex("""-(\d{2,4})x(\d{2,4})\.(?:jpe?g|png|webp|gif|avif)$""")
    private val bloggerSize = Regex("""/[swh](\d{2,4})(?:-[a-z0-9-]+)?/""")
    private val googleSize = Regex("""=[swh](\d{2,4})(?:-[a-z0-9-]+)*$""")
    private val sizeQuery = Regex("""(?:^|[?&])(?:w|h|width|height|size|resize|maxwidth|max_width)=(\d{2,4})""")

    /**
     * The first usable share image declared in [html] (ideally just the page's `<head>`), resolved
     * against [pageUrl] or the page's own `<base>`. Generic images such as logos are skipped.
     */
    fun extractImageUrl(html: String, pageUrl: String): String? {
        val document = try {
            Ksoup.parse(html, pageUrl)
        } catch (_: Exception) {
            return null
        }
        for ((selector, attribute) in imageSelectors) {
            val found = document.select(selector)
                .asSequence()
                .map { element -> element.absUrl(attribute).trim() }
                .firstOrNull { url -> url.isWebUrl() && !isGeneric(url.lowercase().substringBefore('?')) }
            if (found != null) return found
        }
        return null
    }

    /**
     * Whether a feed's image is missing, a generic image (logo, icon, tracking pixel, the feed's own
     * logo) or explicitly small according to its URL, so a share image would be an improvement.
     */
    fun isLowQuality(imageUrl: String?, feedLogoUrl: String?): Boolean {
        if (imageUrl.isNullOrBlank()) return true
        val url = imageUrl.trim().lowercase()
        if (feedLogoUrl != null && url == feedLogoUrl.trim().lowercase()) return true
        val path = url.substringBefore('#').substringBefore('?')
        val query = url.substringAfter('?', missingDelimiterValue = "")
        return isGeneric(path) ||
            smallMarkers.any { it in path } ||
            isSmall(wordpressSize.find(path)?.groupValues?.drop(1)) ||
            isSmall(bloggerSize.find(path)?.groupValues?.drop(1)) ||
            isSmall(googleSize.find(path)?.groupValues?.drop(1)) ||
            isSmall(sizeQuery.findAll(query).map { it.groupValues[1] }.toList())
    }

    private fun isGeneric(path: String): Boolean =
        path.endsWith(".svg") || path.endsWith(".ico") || genericMarkers.any { it in path }

    private fun isSmall(sizes: List<String>?): Boolean =
        !sizes.isNullOrEmpty() && sizes.any { (it.toIntOrNull() ?: Int.MAX_VALUE) < MIN_IMAGE_SIDE }

    private fun String.isWebUrl(): Boolean = startsWith("https://") || startsWith("http://")
}
