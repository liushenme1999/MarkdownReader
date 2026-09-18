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
    fun isCompleteBundle_pdfJpegRequiresPageImages() {
        val dir = tmp.newFolder("pdf-jpg")
        File(dir, ParsedBookStorage.BODY_FILE).writeText(
            PdfReaderContent.buildPageImgTag("book-asset://pdf_page_001.jpg", 720, 1280),
        )
        assertFalse(ParsedBookStorage.isCompleteBundle(dir, "pdf"))
        File(dir, ParsedBookStorage.ASSETS_DIR).mkdirs()
        File(dir, "${ParsedBookStorage.ASSETS_DIR}/pdf_page_001.jpg").writeBytes(byteArrayOf(1))
        assertTrue(ParsedBookStorage.isCompleteBundle(dir, "pdf"))
    }

    @Test
    fun installStagedAssets_copiesPageFiles() {
        val staging = File(tmp.root, "staging-assets").apply { mkdirs() }
        File(staging, "pdf_page_001.jpg").writeBytes(byteArrayOf(7, 8, 9))
        val bundle = File(tmp.root, "bundle-dest").apply { mkdirs() }
        assertTrue(ParsedBookStorage.installStagedAssets(staging, bundle))
        assertTrue(File(bundle, "${ParsedBookStorage.ASSETS_DIR}/pdf_page_001.jpg").isFile)
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
