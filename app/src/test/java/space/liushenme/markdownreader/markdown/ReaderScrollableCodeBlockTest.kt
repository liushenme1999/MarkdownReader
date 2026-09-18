package space.liushenme.markdownreader.markdown

import android.graphics.Paint
import android.text.style.ReplacementSpan
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderScrollableCodeBlockTest {

    @Before
    fun setUp() {
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 0
    }

    @After
    fun tearDown() {
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 0
    }

    @Test
    fun wrapOn_usesWindowSpanWithLanguage() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```javascript\nfunction foo() {\n  return 1;\n}\n```")
        val spans = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals("JavaScript", spans[0].languageLabel)
        assertTrue(spans[0].rawCode.contains("function foo()"))
        assertTrue(rendered.contains('\uFFFC'))
    }

    @Test
    fun wrapOff_usesSingleObjectReplacement() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = false
        ReaderCodeBlockSettings.viewportWidthPx = 360
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```javascript\nfunction foo() {\n  return 1;\n}\n```")
        val spans = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
        assertEquals(1, spans.size)
        assertTrue(rendered.contains('\uFFFC'))
        val start = rendered.getSpanStart(spans[0])
        val end = rendered.getSpanEnd(spans[0])
        assertEquals(1, end - start)

        val paint = Paint().apply { textSize = 42f }
        val fm = Paint.FontMetricsInt()
        val width = spans[0].getSize(paint, rendered, start, end, fm)
        assertEquals(360, width)
        assertTrue(fm.descent - fm.ascent > paint.textSize)
        assertTrue(spans[0] is ReplacementSpan)
        assertEquals("JavaScript", spans[0].languageLabel)
    }

    @Test
    fun wrapOff_longLine_canScrollHorizontally() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = false
        ReaderCodeBlockSettings.viewportWidthPx = 240
        val markwon = ReaderMarkwonFactory.create(context)
        val longLine = "val x = " + "abcdefghij".repeat(40)
        val rendered = markwon.toMarkdown("```kotlin\n$longLine\n```")
        val span = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java).single()
        val paint = Paint().apply { textSize = 40f }
        span.getSize(paint, rendered, 0, 1, Paint.FontMetricsInt())
        assertTrue("expected overflow, maxScrollX=${span.maxScrollX()}", span.maxScrollX() > 50)
        val before = span.scrollX
        assertTrue(span.scrollBy(80f))
        assertTrue(span.scrollX > before)
        assertEquals("Kotlin", span.languageLabel)

        ReaderCodeBlockSettings.viewportWidthPx = 0
        span.prepareForTouch(paint, 240)
        assertTrue(span.canScrollHorizontally())
    }

    @Test
    fun wrapOn_longLine_doesNotScroll() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 240
        val markwon = ReaderMarkwonFactory.create(context)
        val longLine = "val x = " + "abcdefghij".repeat(40)
        val rendered = markwon.toMarkdown("```kotlin\n$longLine\n```")
        val span = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java).single()
        val paint = Paint().apply { textSize = 40f }
        span.getSize(paint, rendered, 0, 1, Paint.FontMetricsInt())
        assertFalse(span.canScrollHorizontally())
        assertEquals(0, span.maxScrollX())
    }

    @Test
    fun languageLabel_parsesFenceInfo() {
        assertEquals("Python", ReaderCodeBlockLanguage.label("python"))
        assertEquals("JavaScript", ReaderCodeBlockLanguage.label("js {.line-numbers}"))
        assertEquals("Java", ReaderCodeBlockLanguage.label("java"))
        assertEquals("", ReaderCodeBlockLanguage.label(null))
        assertEquals("", ReaderCodeBlockLanguage.label("  "))
    }

    @Test
    fun offsetAt_andSelectedText_useInnerLayout() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```kotlin\nval hello = 1\n```")
        val span = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java).single()
        val paint = Paint().apply { textSize = 40f }
        span.prepareForTouch(paint, 400)
        span.getSize(paint, rendered, 0, 1, Paint.FontMetricsInt())
        val y = span.headerHeightPx(paint) + 20f
        val offset = span.offsetAt(24f, y, paint, originLeft = 0f, originTop = 0f)
        assertTrue("offsetAt should hit code, got $offset", offset != null)
        val start = offset!!
        val end = (start + 1).coerceAtMost(span.codeLength())
        span.setSelection(start, end)
        assertTrue(span.hasSelection())
        val selected = span.selectedText()
        assertTrue(selected.isNotEmpty())
        assertTrue(span.rawCode.contains(selected))

        span.setSelection(0, 3)
        assertEquals(span.rawCode.take(3), span.selectedText())

        val idx = span.indexOfSnippet("hello")
        assertTrue(idx >= 0)
        span.addHighlightRange(
            CodeBlockHighlightRange(idx, idx + 5, 0xFFFFFF00.toInt()),
        )
        assertEquals(1, span.highlightRangesForTest().size)
        span.removeHighlightRangeMatching("hello")
        assertTrue(span.highlightRangesForTest().isEmpty())
    }
}
