package space.liushenme.markdownreader.importing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ParsedBookStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun readBundle_rewritesForeignFilePdfAssets() {
        val dir = tmp.newFolder("book")
        val assets = File(dir, ParsedBookStorage.ASSETS_DIR).also { it.mkdirs() }
        File(assets, "pdf_page_001.png").writeBytes(byteArrayOf(1, 2, 3))
        File(dir, ParsedBookStorage.BODY_FILE).writeText(
            """<img src="file:///other/device/files/assets/pdf_page_001.png" width="100%"/>""",
        )
        val extracted = ParsedBookStorage.readBundle(dir)!!
        val local = File(assets, "pdf_page_001.png").absolutePath
        assertTrue(extracted.body.contains("file://$local"))
        assertFalse(extracted.body.contains("/other/device"))
    }

    @Test
    fun isCompleteBundle_pdfRequiresPageImages() {
        val dir = tmp.newFolder("pdf")
        File(dir, ParsedBookStorage.BODY_FILE).writeText(
            PdfReaderContent.buildPageImgTag("book-asset://pdf_page_001.png"),
        )
        assertFalse(ParsedBookStorage.isCompleteBundle(dir, "pdf"))
        File(dir, ParsedBookStorage.ASSETS_DIR).mkdirs()
        File(dir, "${ParsedBookStorage.ASSETS_DIR}/pdf_page_001.png").writeBytes(byteArrayOf(1))
        assertTrue(ParsedBookStorage.isCompleteBundle(dir, "pdf"))
    }

    @Test
    fun isCompleteBundle_pdfFormatRejectsMarkdownBody() {
        val dir = tmp.newFolder("md")
        File(dir, ParsedBookStorage.BODY_FILE).writeText("# 默认示例\n\n这是 Markdown。")
        assertFalse(ParsedBookStorage.isCompleteBundle(dir, "pdf"))
        assertTrue(ParsedBookStorage.isCompleteBundle(dir, "markdown"))
    }

    @Test
    fun looksLikePdfBody_detectsPageImages() {
        val body = PdfReaderContent.buildPageImgTag("file:///a/pdf_page_001.png")
        assertTrue(PdfReaderContent.looksLikePdfBody(body))
        assertFalse(PdfReaderContent.looksLikePdfBody("# 默认 Markdown"))
        assertEquals(
            listOf("pdf_page_001.png"),
            PdfReaderContent.referencedPageAssetNames(body),
        )
    }

    @Test
    fun shouldReplaceBundle_overwritesMarkdownWithPdf() {
        val dst = tmp.newFolder("dst")
        val src = tmp.newFolder("src")
        File(dst, ParsedBookStorage.BODY_FILE).writeText("# leftover markdown")
        File(src, ParsedBookStorage.BODY_FILE).writeText(
            PdfReaderContent.buildPageImgTag("book-asset://pdf_page_001.png"),
        )
        File(src, ParsedBookStorage.ASSETS_DIR).mkdirs()
        File(src, "${ParsedBookStorage.ASSETS_DIR}/pdf_page_001.png").writeBytes(byteArrayOf(1))
        assertTrue(ParsedBookStorage.shouldReplaceBundle(dst, src))
        assertFalse(ParsedBookStorage.shouldReplaceBundle(src, dst))
    }
}
