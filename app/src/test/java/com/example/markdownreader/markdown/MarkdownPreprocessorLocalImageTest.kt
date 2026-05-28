package com.example.markdownreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPreprocessorLocalImageTest {

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
        val out = MarkdownPreprocessor.prepare(md)
        assertFalse(out.contains("static/images/demo.jpg"))
        assertTrue(out.contains("本地"))
    }
}
