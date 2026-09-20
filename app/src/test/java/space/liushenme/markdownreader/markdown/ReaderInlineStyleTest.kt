package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.LineBackgroundSpan
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import android.widget.TextView
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderInlineStyleTest {

    @Test
    fun inlineCodeBackground_isRoundedDrawable() {
        val context: Context = RuntimeEnvironment.getApplication()
        assertTrue(ReaderLatexBlockStyle.inlineCodeBackground(context) is GradientDrawable)
    }

    @Test
    fun inlineLatexBackground_isRoundedDrawable() {
        val context: Context = RuntimeEnvironment.getApplication()
        assertTrue(ReaderLatexBlockStyle.inlineLatexBackground(context) is GradientDrawable)
    }

    @Test
    fun inlineCode_usesWrappableMetricSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md = "遵循`Pre-train -> Fine-tune`的流程。"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md, context).text)
        val codeSpans = rendered.getSpans(0, rendered.length, ReaderInlineCodeSpan::class.java)
        assertTrue(
            "expected ReaderInlineCodeSpan, got ${
                rendered.getSpans(0, rendered.length, Any::class.java).map { it.javaClass.simpleName }
            }",
            codeSpans.isNotEmpty(),
        )
        assertTrue(codeSpans[0] is MetricAffectingSpan)
        assertTrue(codeSpans[0] is LineBackgroundSpan)
        assertTrue(rendered.contains("Pre-train"))
    }

    @Test
    fun inlineCode_longJson_keepsFullTextWithWrappableSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md =
            "函数执行完毕后，会返回一个结果（例如，`{\"temperature\": 32, \"condition\": \"sunny\"}`）。"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md, context).text)
        val text = rendered.toString()
        assertTrue(text.contains("\"temperature\""))
        assertTrue(text.contains("sunny"))
        val codeSpans = rendered.getSpans(0, rendered.length, ReaderInlineCodeSpan::class.java)
        assertEquals(1, codeSpans.size)
        // ReplacementSpan 会把整段代码当成原子块，窄屏无法换行而裁切；必须可拆分
        assertTrue(codeSpans[0] is MetricAffectingSpan)
        assertEquals(
            0,
            rendered.getSpans(0, rendered.length, ReplacementSpan::class.java).size,
        )
    }

    @Test
    fun inlineCode_insideListItem_stillUsesWrappableSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md =
            "* 结果（例如，`{\"temperature\": 32, \"condition\": \"sunny\"}`）。"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md, context).text)
        assertTrue(
            rendered.getSpans(0, rendered.length, ReaderInlineCodeSpan::class.java).isNotEmpty(),
        )
        assertTrue(
            rendered.getSpans(0, rendered.length, android.text.style.LeadingMarginSpan::class.java)
                .isNotEmpty(),
        )
    }

    @Test
    fun measureStyledTextWidth_countsReplacementSpanInPrefix() {
        val paint = TextPaint().apply { textSize = 40f }
        val text = SpannableString("aXb")
        text.setSpan(
            object : ReplacementSpan() {
                override fun getSize(
                    paint: android.graphics.Paint,
                    text: CharSequence,
                    start: Int,
                    end: Int,
                    fm: android.graphics.Paint.FontMetricsInt?,
                ): Int = 100

                override fun draw(
                    canvas: android.graphics.Canvas,
                    text: CharSequence,
                    start: Int,
                    end: Int,
                    x: Float,
                    top: Int,
                    y: Int,
                    bottom: Int,
                    paint: android.graphics.Paint,
                ) = Unit
            },
            1,
            2,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val styled = measureStyledTextWidth(text, 0, 3, paint)
        val expected = paint.measureText(text, 0, 1) + 100f + paint.measureText(text, 2, 3)
        assertEquals(expected, styled, 0.5f)
    }

    @Test
    fun twoInlineCodesOnSameLine_secondBackgroundAlignsWithSecondCode() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("有类型 `type` 和列表 `list`")
        val spans = rendered.getSpans(0, rendered.length, ReaderInlineCodeSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
        assertEquals(2, spans.size)

        val tv = TextView(context).apply {
            textSize = 16f
            text = rendered
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 900, 200)
        }
        val layout = tv.layout
        val second = spans[1]
        val secondStart = rendered.getSpanStart(second)
        val firstEnd = rendered.getSpanEnd(spans[0])
        val line = layout.getLineForOffset(secondStart)
        val bounds = second.computeBackgroundBounds(
            tv.paint,
            0,
            layout.getLineBaseline(line),
            rendered,
            layout.getLineStart(line),
            layout.getLineEnd(line),
        )
        requireNotNull(bounds)
        val firstRight = layout.getPrimaryHorizontal(firstEnd)
        assertTrue(
            "second box must not cover the first code, firstRight=$firstRight secondLeft=${bounds.left}",
            bounds.left >= firstRight - 1f,
        )
    }

    @Test
    fun inlineCodeBackground_fillsOneThirdOfGapToNeighborText() {
        val paint = TextPaint().apply { textSize = 48f }
        val text = "A type B"
        val start = text.indexOf("type")
        val end = start + 4
        val origin = paint.measureText(text, 0, start)
        val (bgLeft, bgRight) = inlineCodeBackgroundHorizontal(
            codePaint = paint,
            basePaint = paint,
            text = text,
            lineStart = 0,
            lineEnd = text.length,
            start = start,
            end = end,
            originX = origin,
            edgePadPx = 6,
        )
        val codeInk = android.graphics.Rect()
        paint.getTextBounds("type", 0, 4, codeInk)
        val codeLeft = if (codeInk.width() > 0) origin + codeInk.left else origin
        val codeRight = if (codeInk.width() > 0) {
            origin + codeInk.right
        } else {
            origin + paint.measureText("type")
        }

        val leftInk = android.graphics.Rect()
        paint.getTextBounds("A", 0, 1, leftInk)
        val leftRight = if (leftInk.width() > 0) leftInk.right.toFloat() else paint.measureText("A")

        val rightOrigin = paint.measureText(text, 0, text.indexOf('B'))
        val rightInk = android.graphics.Rect()
        paint.getTextBounds("B", 0, 1, rightInk)
        val rightLeft = if (rightInk.width() > 0) {
            rightOrigin + rightInk.left
        } else {
            rightOrigin
        }

        val leftGap = (codeLeft - leftRight).coerceAtLeast(0f)
        val rightGap = (rightLeft - codeRight).coerceAtLeast(0f)
        assertEquals(codeLeft - leftGap / 3f, bgLeft.toFloat(), 1.5f)
        assertEquals(codeRight + rightGap / 3f, bgRight.toFloat(), 1.5f)
        assertTrue("must extend into the side gap, leftGap=$leftGap", bgLeft < codeLeft - 0.5f || leftGap < 1.5f)
    }

    @Test
    fun inlineCodeBackground_usesEdgePadWhenNoNeighbor() {
        val paint = TextPaint().apply { textSize = 48f }
        val word = "type"
        val origin = 100f
        val (left, right) = inlineCodeBackgroundHorizontal(
            codePaint = paint,
            basePaint = paint,
            text = word,
            lineStart = 0,
            lineEnd = word.length,
            start = 0,
            end = word.length,
            originX = origin,
            edgePadPx = 5,
        )
        val ink = android.graphics.Rect()
        paint.getTextBounds(word, 0, word.length, ink)
        val expectedLeft = if (ink.width() > 0) origin + ink.left - 5 else origin - 5
        val expectedRight = if (ink.width() > 0) origin + ink.right + 5 else origin + paint.measureText(word) + 5
        assertEquals(expectedLeft.toInt(), left)
        assertEquals(expectedRight.toInt(), right)
    }

    @Test
    fun twoInlineCodesInsideListItem_secondBackgroundAlignsWithSecondCode() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("* 示例2：有类型 `type` 和列表 `list`")
        val spans = rendered.getSpans(0, rendered.length, ReaderInlineCodeSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
        assertEquals(2, spans.size)
        val tv = TextView(context).apply {
            textSize = 16f
            text = rendered
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 900, 200)
        }
        val layout = tv.layout
        val second = spans[1]
        val secondStart = rendered.getSpanStart(second)
        val line = layout.getLineForOffset(secondStart)
        val bounds = second.computeBackgroundBounds(
            tv.paint,
            0,
            layout.getLineBaseline(line),
            rendered,
            layout.getLineStart(line),
            layout.getLineEnd(line),
        )
        requireNotNull(bounds)
        val firstRight = layout.getPrimaryHorizontal(rendered.getSpanEnd(spans[0]))
        assertTrue(
            "list item second box must not cover the first code, firstRight=$firstRight secondLeft=${bounds.left}",
            bounds.left >= firstRight - 1f,
        )
    }

    @Test
    fun inlineLatex_usesVerticallyCenteredSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val d = "$"
        val md = "共 ${d}N${d} 个头"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md, context).text)
        val spans = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java)
        assertTrue(
            "expected ReaderInlineLatexSpan, got ${spans.map { it.javaClass.name }}",
            spans.any { it.javaClass.simpleName == "ReaderInlineLatexSpan" },
        )
    }
}
