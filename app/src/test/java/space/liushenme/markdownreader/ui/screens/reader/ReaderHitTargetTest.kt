package space.liushenme.markdownreader.ui.screens.reader

import android.text.Spanned
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import io.noties.markwon.core.spans.HeadingSpan
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderHitTargetTest {

    @Test
    fun markdownHeading_resolvesAsSelectableHeading() {
        val context = RuntimeEnvironment.getApplication()
        val rendered = ReaderMarkwonFactory.create(context)
            .toMarkdown("## (1) 准确率奖励\n\n正文内容") as Spanned
        val heading = rendered.getSpans(0, rendered.length, HeadingSpan::class.java).single()
        val start = rendered.getSpanStart(heading) + 1
        val view = textView(rendered, 600, 240)
        val point = pointForOffset(view, start)

        val target = resolveReaderHitTarget(view, point.first, point.second)

        assertEquals(ReaderHitKind.HEADING, target.kind)
        assertTrue(target.canSelect)
        assertTrue(target.canHighlight)
        assertTrue(target.displayedEnd > target.displayedStart)
    }

    @Test
    fun ordinaryText_resolvesAsSelectableText() {
        val context = RuntimeEnvironment.getApplication()
        val view = textView(SpannableString("普通正文内容"), 500, 180)
        val point = pointForOffset(view, 2)

        val target = resolveReaderHitTarget(view, point.first, point.second)

        assertEquals(ReaderHitKind.TEXT, target.kind)
        assertTrue(target.canSelect)
        assertTrue(target.canHighlight)
    }

    @Test
    fun link_resolvesAsClickableAndSelectable() {
        val body = SpannableString("前文 链接文字 后文")
        val start = body.indexOf("链接文字")
        body.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) = Unit
            },
            start,
            start + 4,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val view = textView(body, 500, 180)
        val point = pointForOffset(view, start + 1)

        val target = resolveReaderHitTarget(view, point.first, point.second)

        assertEquals(ReaderHitKind.LINK, target.kind)
        assertTrue(target.canClick)
        assertTrue(target.canSelect)
        assertTrue(target.canHighlight)
    }

    @Test
    fun existingHighlight_resolvesToItsCompleteRange() {
        val body = SpannableString("前文 已划线内容 后文")
        val start = body.indexOf("已划线内容")
        val view = textView(body, 500, 180)
        applyHighlightDecorationAtRange(
            textView = view,
            start = 0,
            end = body.length,
            colorArgb = 0xFFFFFF00.toInt(),
            style = space.liushenme.markdownreader.model.HighlightStyle.Background,
            highlightId = 42L,
        )
        val rendered = view.text as Spanned
        assertTrue(rendered.getSpans(0, 1, HighlightBackgroundSpan::class.java).isNotEmpty())
        val layout = view.layout ?: error("layout missing")
        val line = layout.getLineForOffset(start)
        val y = layout.getLineBaseline(line).toFloat() + view.totalPaddingTop - 2f
        val targets = (view.totalPaddingLeft until view.width)
            .map { x -> resolveReaderHitTarget(view, x.toFloat(), y) }
        val target = targets.firstOrNull { it.kind == ReaderHitKind.EXISTING_HIGHLIGHT }
            ?: error("no touch point mapped onto the highlight: ${targets.groupingBy { it.kind }.eachCount()}")

        assertEquals(ReaderHitKind.EXISTING_HIGHLIGHT, target.kind)
        assertEquals(0, target.displayedStart)
        assertEquals(body.length, target.displayedEnd)
        assertTrue(target.canSelect)
        assertTrue(target.canHighlight)
    }

    @Test
    fun headingRange_doesNotIncludeParagraphBreak() {
        val context = RuntimeEnvironment.getApplication()
        val rendered = ReaderMarkwonFactory.create(context)
            .toMarkdown("## 标题\n\n正文") as Spanned
        val heading = rendered.getSpans(0, rendered.length, HeadingSpan::class.java).single()
        val view = textView(rendered, 500, 180)
        val titleEnd = rendered.toString().indexOf("标题") + "标题".length
        val point = pointForOffset(view, titleEnd - 1)

        val target = resolveReaderHitTarget(view, point.first, point.second)

        assertEquals(ReaderHitKind.HEADING, target.kind)
        assertEquals(rendered.getSpanStart(heading), target.displayedStart)
        assertTrue(target.displayedEnd <= titleEnd + 1)
        assertTrue(target.displayedEnd == titleEnd || rendered[target.displayedEnd - 1] != '\n')
    }

    private fun textView(body: CharSequence, width: Int, height: Int): SafeReaderTextView {
        val context = RuntimeEnvironment.getApplication()
        val view = SafeReaderTextView(context).apply {
            setText(body, TextView.BufferType.SPANNABLE)
            textSize = 20f
            setPadding(8, 8, 8, 8)
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, width, height)
        }
        FrameLayout(context).apply {
            addView(view)
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, width, height)
        }
        return view
    }

    private fun pointForOffset(view: TextView, offset: Int): Pair<Float, Float> {
        val layout = view.layout ?: error("layout missing")
        val line = layout.getLineForOffset(offset)
        return (
            layout.getPrimaryHorizontal(offset) + view.totalPaddingLeft
        ) to (
            (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f + view.totalPaddingTop
        )
    }

}
