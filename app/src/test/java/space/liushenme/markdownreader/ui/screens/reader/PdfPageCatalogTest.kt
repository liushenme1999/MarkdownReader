package space.liushenme.markdownreader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.importing.PdfReaderContent
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PdfPageCatalogTest {

    @Test
    fun encodeDecode_roundTripPageAndFraction() {
        val pages = listOf(
            PdfPageRef(0, "/a/pdf_page_001.png", 0, "pdf_page_001.png"),
            PdfPageRef(1, "/a/pdf_page_002.png", 80, "pdf_page_002.png"),
            PdfPageRef(2, "/a/pdf_page_003.png", 160, "pdf_page_003.png"),
        )
        val contentLen = 200
        val encoded = PdfPageCatalog.encodeProgress(pages, 1, 0.5f, contentLen)
        val decoded = PdfPageCatalog.decodeProgress(pages, encoded, contentLen)
        assertEquals(1, decoded.pageIndex)
        assertEquals(0.5f, decoded.fractionInPage, 0.08f)
    }

    @Test
    fun decode_pageStartIsZeroFraction() {
        val pages = listOf(
            PdfPageRef(0, "/a/p1.png", 0, "p1.png"),
            PdfPageRef(1, "/a/p2.png", 50, "p2.png"),
        )
        val decoded = PdfPageCatalog.decodeProgress(pages, 50, 100)
        assertEquals(1, decoded.pageIndex)
        assertEquals(0f, decoded.fractionInPage, 0.01f)
    }

    @Test
    fun resolvePageFile_prefersFileSchemeThenAssetName() {
        val dir = File.createTempFile("pdf-assets", "dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val page = File(dir, "pdf_page_001.png").apply {
            writeText("x")
            deleteOnExit()
        }
        val fromName = PdfPageCatalog.resolvePageFile("book-asset://pdf_page_001.png", dir)
        assertEquals(page.absolutePath, fromName?.absolutePath)
        val fromFile = PdfPageCatalog.resolvePageFile("file://${page.absolutePath}", dir)
        assertEquals(page.absolutePath, fromFile?.absolutePath)
    }

    @Test
    fun pageImageSources_matchSanitize() {
        val body = buildString {
            append(PdfReaderContent.buildPageImgTag("file:///tmp/pdf_page_001.png"))
            append('\n')
            append(PdfReaderContent.buildPageImgTag("file:///tmp/pdf_page_002.png"))
        }
        val sources = PdfReaderContent.pageImageSources(body)
        assertEquals(2, sources.size)
        assertTrue(sources[0].second.contains("pdf_page_001"))
        assertTrue(sources[1].second.contains("pdf_page_002"))
    }

    @Test
    fun sampleSize_keepsAtLeastTargetWidth() {
        assertEquals(1, PdfPageBitmapCache.sampleSize(1080, 1080))
        assertEquals(2, PdfPageBitmapCache.sampleSize(2160, 1080))
        assertEquals(4, PdfPageBitmapCache.sampleSize(4320, 1000))
    }

    @Test
    fun scanAssetPages_ordersJpegByPageNumber() {
        val dir = File.createTempFile("pdf-assets", "dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        File(dir, "pdf_page_002.jpg").apply { writeText("b"); deleteOnExit() }
        File(dir, "pdf_page_001.jpg").apply { writeText("a"); deleteOnExit() }
        val pages = PdfPageCatalog.scanAssetPages(dir)
        assertEquals(2, pages.size)
        assertEquals("pdf_page_001.jpg", pages[0].assetName)
        assertEquals("pdf_page_002.jpg", pages[1].assetName)
    }

    @Test
    fun parse_readsIntrinsicSizeFromDataAttrs() {
        val dir = File.createTempFile("pdf-bundle", "dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val assets = File(dir, "assets").apply { mkdirs(); deleteOnExit() }
        File(assets, "pdf_page_001.jpg").apply { writeText("x"); deleteOnExit() }
        val body = PdfReaderContent.buildPageImgTag("book-asset://pdf_page_001.jpg", 720, 1280)
        val pages = PdfPageCatalog.parse(body, dir)
        assertEquals(1, pages.size)
        assertEquals(720, pages[0].intrinsicWidth)
        assertEquals(1280, pages[0].intrinsicHeight)
    }
}
