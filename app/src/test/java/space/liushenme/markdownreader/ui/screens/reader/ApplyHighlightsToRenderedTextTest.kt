package space.liushenme.markdownreader.ui.screens.reader

import android.text.SpannableString
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.model.HighlightStyle
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ApplyHighlightsToRenderedTextTest {

    @Test
    fun applyHighlights_usesPerHighlightColor() {
        val context = RuntimeEnvironment.getApplication()
        val textView = TextView(context).apply {
            setText(SpannableString("前面选中后面"), TextView.BufferType.SPANNABLE)
        }
        val green = 0xFF00FF00.toInt()
        val themeYellow = 0xFFFFFF00.toInt()
        val highlights = listOf(
            HighlightEntity(
                bookId = 1L,
                startPosition = 2,
                endPosition = 4,
                highlightedText = "选中",
                color = green,
                createTime = Date(),
            ),
        )
        applyHighlightsToRenderedText(textView, highlights, themeYellow, sourceContentLength = 6)
        val spanned = textView.text as SpannableString
        val spans = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(green, spans[0].backgroundColor)
        assertEquals(2, spanned.getSpanStart(spans[0]))
        assertEquals(4, spanned.getSpanEnd(spans[0]))
    }

    @Test
    fun applyHighlights_fallsBackToThemeColorWhenAlphaTooLow() {
        val context = RuntimeEnvironment.getApplication()
        val textView = TextView(context).apply {
            setText(SpannableString("前面选中后面"), TextView.BufferType.SPANNABLE)
        }
        val themeYellow = 0xFFFFFF00.toInt()
        val highlights = listOf(
            HighlightEntity(
                bookId = 1L,
                startPosition = 2,
                endPosition = 4,
                highlightedText = "选中",
                color = 0x00000000,
                createTime = Date(),
            ),
        )
        applyHighlightsToRenderedText(textView, highlights, themeYellow, sourceContentLength = 6)
        val spanned = textView.text as SpannableString
        val spans = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(themeYellow, spans[0].backgroundColor)
    }

    @Test
    fun applyHighlights_onlyMarksOccurrenceNearSourcePosition() {
        val context = RuntimeEnvironment.getApplication()
        // 同文两次「选中」，只应标第二处（源码相对位置 4..6）
        val body = "选中一二选中"
        val textView = TextView(context).apply {
            setText(SpannableString(body), TextView.BufferType.SPANNABLE)
        }
        val yellow = 0xFFFFFF00.toInt()
        val highlights = listOf(
            HighlightEntity(
                bookId = 1L,
                startPosition = 4,
                endPosition = 6,
                highlightedText = "选中",
                color = yellow,
                createTime = Date(),
            ),
        )
        applyHighlightsToRenderedText(
            textView = textView,
            highlights = highlights,
            highlightColorArgb = yellow,
            sourceContentLength = body.length,
        )
        val spanned = textView.text as SpannableString
        val spans = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(4, spanned.getSpanStart(spans[0]))
        assertEquals(6, spanned.getSpanEnd(spans[0]))
    }

    @Test
    fun applyHighlights_underlineStyle_usesUnderlineSpan() {
        val context = RuntimeEnvironment.getApplication()
        val textView = TextView(context).apply {
            setText(SpannableString("前面选中后面"), TextView.BufferType.SPANNABLE)
        }
        val yellow = 0xFFFFFF00.toInt()
        applyHighlightsToRenderedText(
            textView = textView,
            highlights = listOf(
                HighlightEntity(
                    bookId = 1L,
                    startPosition = 2,
                    endPosition = 4,
                    highlightedText = "选中",
                    color = yellow,
                    style = HighlightStyle.Underline.storageKey,
                    createTime = Date(),
                ),
            ),
            highlightColorArgb = yellow,
            sourceContentLength = 6,
        )
        val spanned = textView.text as SpannableString
        val underlines = spanned.getSpans(0, spanned.length, HighlightUnderlineSpan::class.java)
        val backgrounds = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(1, underlines.size)
        assertEquals(0, backgrounds.size)
    }

    @Test
    fun resolveHighlightDisplayedRange_picksClosestMatch() {
        // 三处「选中」：0、4、8；hint 靠近末尾时应命中第三处
        val displayed = "选中AA选中BB选中"
        val highlight = HighlightEntity(
            bookId = 1L,
            startPosition = 9,
            endPosition = 11,
            highlightedText = "选中",
            color = 0xFFFFFF00.toInt(),
            createTime = Date(),
        )
        val range = resolveHighlightDisplayedRange(
            displayed = displayed,
            highlight = highlight,
            sourceContentLength = displayed.length,
        )
        assertEquals(8 until 10, range)
    }

    @Test
    fun applyHighlights_attachesSnippetToCodeBlockSpan() {
        val context = RuntimeEnvironment.getApplication()
        space.liushenme.markdownreader.markdown.ReaderCodeBlockSettings.wrapEnabled = true
        space.liushenme.markdownreader.markdown.ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon = space.liushenme.markdownreader.markdown.ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```kotlin\nval hello = 1\n```")
        val textView = TextView(context).apply {
            setText(rendered, TextView.BufferType.SPANNABLE)
        }
        val yellow = 0xFFFFFF00.toInt()
        applyHighlightsToRenderedText(
            textView,
            listOf(
                HighlightEntity(
                    bookId = 1L,
                    startPosition = 0,
                    endPosition = 5,
                    highlightedText = "hello",
                    color = yellow,
                    createTime = Date(),
                ),
            ),
            highlightColorArgb = yellow,
            sourceContentLength = 40,
        )
        val span = (textView.text as android.text.Spanned).getSpans(
            0,
            textView.text.length,
            space.liushenme.markdownreader.markdown.ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val ranges = span.highlightRangesForTest()
        assertEquals(1, ranges.size)
        assertEquals("hello", span.rawCode.substring(ranges[0].start, ranges[0].end))
    }
}
