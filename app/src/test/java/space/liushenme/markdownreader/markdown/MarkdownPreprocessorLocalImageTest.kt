package space.liushenme.markdownreader.markdown

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MarkdownPreprocessorLocalImageTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()


    @Test
    fun stripLocalRelativeImages_replacesMarkdownWithAlt() {
        val md = "![本地素材图](static/images/demo.jpg)"
        val out = MarkdownPreprocessor.stripLocalRelativeImages(md)
        assertEquals("本地素材图", out)
    }

    @Test
    fun stripLocalRelativeImages_keepsHttpImages() {
        val md = "![net](https://picsum.photos/200/100)"
        val out = MarkdownPreprocessor.stripLocalRelativeImages(md)
        assertEquals(md, out)
    }

    @Test
    fun stripLocalRelativeImages_stripsHtmlImgWithRelativeSrc() {
        val md = """<img src="static/images/demo.jpg" alt="本地">"""
        val out = MarkdownPreprocessor.stripLocalRelativeImages(md)
        assertEquals("本地", out)
    }

    @Test
    fun isLoadableImageRef_detectsRemoteAndSpecialSchemes() {
        assertTrue(MarkdownPreprocessor.isLoadableImageRef("https://a/b.png"))
        assertTrue(MarkdownPreprocessor.isLoadableImageRef("file:///tmp/x"))
        assertTrue(MarkdownPreprocessor.isLoadableImageRef("diagram://mermaid/abc"))
        assertFalse(MarkdownPreprocessor.isLoadableImageRef("static/x.jpg"))
    }

    @Test
    fun prepare_stripsLocalImageInPipeline() {
        val md = "## 7.3\n![本地](static/images/demo.jpg)\n"
        val out = MarkdownPreprocessor.prepare(md, context)
        assertFalse(out.contains("static/images/demo.jpg"))
        assertTrue(out.contains("本地"))
    }

    @Test
    fun rewriteRelativeImagesToFileUri_rewritesExistingFile() {
        val dir = File(context.cacheDir, "img_rewrite_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val img = File(dir, "demo.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val md = "![本地](./demo.png)"
        val out = MarkdownPreprocessor.rewriteRelativeImagesToFileUri(md, dir)
        assertTrue(out.contains("file://${img.canonicalFile.absolutePath}"))
        assertTrue(out.contains("![本地]"))
    }

    @Test
    fun rewriteRelativeImagesToFileUri_missingFileKeepsAlt() {
        val dir = File(context.cacheDir, "img_rewrite_missing").apply {
            deleteRecursively()
            mkdirs()
        }
        val md = "![本地](./missing.png)"
        val out = MarkdownPreprocessor.rewriteRelativeImagesToFileUri(md, dir)
        assertEquals("本地", out)
    }

    @Test
    fun rewriteRelativeImagesToFileUri_keepsHttp() {
        val dir = File(context.cacheDir, "img_rewrite_http").apply { mkdirs() }
        val md = "![net](https://example.com/a.png)"
        val out = MarkdownPreprocessor.rewriteRelativeImagesToFileUri(md, dir)
        assertEquals(md, out)
    }
}
