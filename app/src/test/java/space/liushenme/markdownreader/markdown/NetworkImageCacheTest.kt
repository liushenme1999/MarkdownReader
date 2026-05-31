package space.liushenme.markdownreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NetworkImageCacheTest {

    @Test
    fun rewriteCachedUrls_replacesMarkdownAndHtmlWhenCached() {
        val url = "https://example.com/a.png"
        val cached = File("/tmp/network_image_cache/abc.png")
        val md = """
            ![alt]($url)
            <img src="$url" alt="x"/>
        """.trimIndent()
        val out = NetworkImageCache.rewriteCachedUrls(md) { if (it == url) cached else null }
        assertFalse(out.contains("https://example.com/a.png"))
        assertTrue(out.contains("file:///tmp/network_image_cache/abc.png"))
        assertTrue(out.contains("![alt](file://"))
        assertTrue(out.contains("src=\"file://"))
    }

    @Test
    fun rewriteCachedUrls_leavesUncachedUrlsUntouched() {
        val url = "https://example.com/missing.jpg"
        val md = "![x]($url)"
        val out = NetworkImageCache.rewriteCachedUrls(md) { null }
        assertEquals(md, out)
    }

    @Test
    fun guessContentType_prefersHeaderThenUrlExtension() {
        assertEquals("image/png", NetworkImageCache.guessContentType("image/png", "https://x.test/a"))
        assertEquals("image/jpeg", NetworkImageCache.guessContentType(null, "https://x.test/a.jpg"))
    }

    @Test
    fun extensionFromContentType_mapsCommonMimeTypes() {
        assertEquals("jpg", NetworkImageCache.extensionFromContentType("image/jpeg"))
        assertEquals("webp", NetworkImageCache.extensionFromContentType("image/webp"))
    }
}
