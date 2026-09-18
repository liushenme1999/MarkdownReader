package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.importing.PdfReaderContent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReadingPreviewTextTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun normalizeReadingPreviewText_stripsObjectReplacementChars() {
        assertEquals("", normalizeReadingPreviewText("\uFFFC\uFFFC"))
        assertEquals("hello", normalizeReadingPreviewText("\uFFFC hello \uFFFC"))
    }

    @Test
    fun resolveBookmarkPreviewText_pdfUsesPageTitleEvenIfViewportIsGarbled() {
        val body = buildString {
            append(PdfReaderContent.buildPageImgTag("book-asset://pdf_page_001.png"))
            append('\n')
            append(PdfReaderContent.buildPageImgTag("book-asset://pdf_page_002.png"))
        }
        val page2 = PdfReaderContent.splitToPages(body)[1].first
        val preview = resolveBookmarkPreviewText(
            previewForAdd = "\uFFFC\uFFFC\uFFFC",
            sourceContent = body,
            position = page2,
            context = context,
        )
        assertEquals(context.getString(R.string.pdf_page_title, 2), preview)
        assertFalse(preview.contains("\uFFFC"))
    }

    @Test
    fun resolveBookmarkPreviewText_markdownKeepsViewportSnippet() {
        val preview = resolveBookmarkPreviewText(
            previewForAdd = "可见的正文预览",
            sourceContent = "# Title\n\n可见的正文预览后面还有很多字。",
            position = 10,
            context = context,
        )
        assertEquals("可见的正文预览", preview)
    }
}
