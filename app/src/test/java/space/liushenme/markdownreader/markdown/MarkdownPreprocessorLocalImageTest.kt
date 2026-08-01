package space.liushenme.markdownreader.markdown

import android.content.Context
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
}
