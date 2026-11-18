package com.prof18.feedflow.android

import android.app.Application
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [Build.VERSION_CODES.Q])
class ArticleRedirectSettingsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("article_redirects", 0).edit().clear().commit()
    }

    @Test
    fun `feed override falls back to default when cleared or removed`() {
        val settings = ArticleRedirectSettings(context)
        val first = "https://example.com/"
        val second = "https://mirror.example/"
        settings.savePrefixes(listOf(first, second))
        settings.setDefaultPrefix(first)
        settings.setFeedPrefix("feed", second)
        assertEquals(second, settings.prefixForFeed("feed"))
        settings.savePrefixes(listOf(first))
        assertEquals(first, settings.prefixForFeed("feed"))
        settings.setDefaultPrefix(null)
        assertNull(settings.prefixForFeed("feed"))
    }

    @Test
    fun `prefix validation requires a web URL without whitespace`() {
        assertTrue(isValidRedirectPrefix("https://mirror.example/"))
        assertFalse(isValidRedirectPrefix("javascript:alert(1)"))
        assertFalse(isValidRedirectPrefix("https://bad host/"))
        assertFalse(isValidRedirectPrefix("https:///path"))
    }
}
