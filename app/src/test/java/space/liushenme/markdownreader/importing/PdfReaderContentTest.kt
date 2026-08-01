package space.liushenme.markdownreader.importing

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PdfReaderContentTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()


    @Test
    fun sanitizeStoredBody_removesLegacyPageHeadings() {
        val raw = "## 第 1 页\n\n<img src=\"file:///a/pdf_page_001.png\" width=\"10\" height=\"20\"/>\n\n## 第 2 页\n\n<img src=\"file:///a/pdf_page_002.png\" width=\"10\" height=\"20\"/>"
        val sanitized = PdfReaderContent.sanitizeStoredBody(raw)
        assertTrue(!sanitized.contains("## 第"))
        assertEquals(2, PdfReaderContent.splitToPages(sanitized).size)
    }

    @Test
    fun buildPageImgTag_usesFullWidthNotFixedPixels() {
        val tag = PdfReaderContent.buildPageImgTag("book-asset://pdf_page_001.png", 1080, 1920)
        assertTrue(tag.contains("width=\"100%\""))
        assertTrue(!tag.contains("width=\"1080\""))
    }

    @Test
    fun pageIndexForSourceOffset_alignsWithSplitToPages() {
        val body = buildString {
            append(PdfReaderContent.buildPageImgTag("book-asset://pdf_page_001.png"))
            append('\n')
            append(PdfReaderContent.buildPageImgTag("book-asset://pdf_page_002.png"))
            append('\n')
            append(PdfReaderContent.buildPageImgTag("book-asset://pdf_page_003.png"))
        }
        val pages = PdfReaderContent.splitToPages(body)
        val toc = PdfReaderContent.tocEntriesFromBody(body, context)
        assertEquals(pages.size, toc.size)
        toc.forEachIndexed { index, entry ->
            assertEquals(index, PdfReaderContent.pageIndexForSourceOffset(body, entry.sourceOffset))
            assertEquals(entry.sourceOffset, pages[index].second)
        }
    }

    @Test
    fun splitToPages_oneImagePerPage() {
        val body = buildString {
            append(PdfReaderContent.buildPageImgTag("book-asset://pdf_page_001.png"))
            append('\n')
            append(PdfReaderContent.buildPageImgTag("book-asset://pdf_page_002.png"))
        }
        val pages = PdfReaderContent.splitToPages(body)
        assertEquals(2, pages.size)
        assertTrue(pages[0].first.contains("pdf_page_001"))
        assertTrue(pages[1].first.contains("pdf_page_002"))
    }
}
