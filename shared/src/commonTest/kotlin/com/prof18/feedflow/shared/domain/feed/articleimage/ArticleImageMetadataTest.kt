package com.prof18.feedflow.shared.domain.feed.articleimage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleImageMetadataTest {

    @Test
    fun `open graph image wins over the twitter card`() {
        val html = head(
            """<meta name="twitter:image" content="https://cdn.example.com/twitter.jpg">""",
            """<meta property="og:image" content="https://cdn.example.com/share.jpg">""",
        )

        assertEquals(
            "https://cdn.example.com/share.jpg",
            ArticleImageMetadata.extractImageUrl(html, "https://example.com/news/story"),
        )
    }

    @Test
    fun `relative images resolve against the page`() {
        val html = head("""<meta name="twitter:image" content="/media/story.jpg">""")

        assertEquals(
            "https://example.com/media/story.jpg",
            ArticleImageMetadata.extractImageUrl(html, "https://example.com/news/story"),
        )
    }

    @Test
    fun `generic share images fall through to the next declaration`() {
        val html = head(
            """<meta property="og:image" content="https://example.com/static/logo.svg">""",
            """<link rel="image_src" href="https://example.com/media/story.jpg">""",
        )

        assertEquals(
            "https://example.com/media/story.jpg",
            ArticleImageMetadata.extractImageUrl(html, "https://example.com/news/story"),
        )
    }

    @Test
    fun `pages without share images give nothing`() {
        assertNull(ArticleImageMetadata.extractImageUrl(head("<title>Story</title>"), "https://example.com/a"))
    }

    @Test
    fun `missing generic and explicitly small images are low quality`() {
        assertTrue(ArticleImageMetadata.isLowQuality(null, feedLogoUrl = null))
        assertTrue(ArticleImageMetadata.isLowQuality("https://example.com/favicon.ico", feedLogoUrl = null))
        assertTrue(ArticleImageMetadata.isLowQuality("https://example.com/wp/photo-150x150.jpg", feedLogoUrl = null))
        assertTrue(ArticleImageMetadata.isLowQuality("https://cdn.example.com/photo.jpg?w=120", feedLogoUrl = null))
        assertTrue(ArticleImageMetadata.isLowQuality("https://bp.blogspot.com/a/s72-c/photo.jpg", feedLogoUrl = null))
        assertTrue(
            ArticleImageMetadata.isLowQuality(
                "https://example.com/brand.png",
                feedLogoUrl = "https://example.com/brand.png",
            ),
        )
    }

    @Test
    fun `full size images are kept`() {
        assertFalse(ArticleImageMetadata.isLowQuality("https://cdn.example.com/photo.jpg", feedLogoUrl = null))
        assertFalse(ArticleImageMetadata.isLowQuality("https://example.com/wp/photo-1200x630.jpg", feedLogoUrl = null))
        assertFalse(ArticleImageMetadata.isLowQuality("https://cdn.example.com/photo.jpg?w=1024", feedLogoUrl = null))
    }

    private fun head(vararg tags: String) = "<html><head>${tags.joinToString("")}</head><body></body></html>"
}
