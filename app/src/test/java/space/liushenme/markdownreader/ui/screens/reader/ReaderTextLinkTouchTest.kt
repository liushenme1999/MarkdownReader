package space.liushenme.markdownreader.ui.screens.reader

import android.text.SpannableString
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Markdown 链接的点击命中范围：只有点在链接文字的字形范围内才跳转，
 * 点同一行其他位置（行首、行尾空白、行下方空白）都不能触发。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderTextLinkTouchTest {

    private val padding = 12
    private val viewWidth = 1000
    private val viewHeight = 600

    private inner class Fixture(val tv: TextView, val span: URLSpan, val layout: android.text.Layout) {
        fun xOf(offset: Int): Float = padding.toFloat() + layout.getPrimaryHorizontal(offset)

        fun centerXOf(offset: Int): Float =
            padding.toFloat() + (layout.getPrimaryHorizontal(offset) +
                layout.getPrimaryHorizontal(offset + 1)) / 2f

        fun centerYOfLine(line: Int): Float =
            padding.toFloat() + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
    }

    private fun build(text: String, linkStart: Int, linkEnd: Int): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val body = SpannableString(text)
        val span = URLSpan("https://www.example.com")
        body.setSpan(span, linkStart, linkEnd, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        val tv = TextView(context).apply {
            this.text = body
            textSize = 16f
            setPadding(padding, padding, padding, padding)
            measure(
                View.MeasureSpec.makeMeasureSpec(viewWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(viewHeight, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
        return Fixture(tv, span, requireNotNull(tv.layout))
    }

    private fun singleLineFixture(): Fixture {
        // 一行内：链接前有正文、链接后有正文，便于验证左右两侧都不命中。
        val text = "前言说明文字 before 点击这里 after 后续正文更多内容结尾"
        val start = text.indexOf("点击这里")
        return build(text, start, start + "点击这里".length)
    }

    @Test
    fun tapOnLinkText_hits() {
        val f = singleLineFixture()
        val mid = f.layout.getLineForOffset(f.tv.text.indexOf("点击"))
        val spanStart = f.tv.text.indexOf("点击这里")
        val found = ReaderTextLinkTouch.findClickableSpanAt(
            f.tv,
            f.centerXOf(spanStart + 1),
            f.centerYOfLine(mid),
        )
        assertNotNull(found)
        assertTrue(found is URLSpan)
    }

    @Test
    fun tapBeforeLinkOnSameLine_doesNotHit() {
        val f = singleLineFixture()
        val line = f.layout.getLineForOffset(0)
        // 行首第一个字：同属一行，但不是链接文字
        assertNull(
            ReaderTextLinkTouch.findClickableSpanAt(f.tv, f.centerXOf(0), f.centerYOfLine(line)),
        )
    }

    @Test
    fun tapAfterLinkOnSameLine_doesNotHit() {
        val f = singleLineFixture()
        val end = f.tv.text.length - 1
        val line = f.layout.getLineForOffset(end)
        assertNull(
            ReaderTextLinkTouch.findClickableSpanAt(f.tv, f.centerXOf(end), f.centerYOfLine(line)),
        )
    }

    @Test
    fun tapOnLinkLineButBesideText_doesNotHit() {
        val f = singleLineFixture()
        val line = f.layout.getLineForOffset(0)
        val right = padding + f.layout.getLineRight(line)
        assertNull(ReaderTextLinkTouch.findClickableSpanAt(f.tv, right - 1f, f.centerYOfLine(line)))
    }

    @Test
    fun tapOnAnotherLine_doesNotHit() {
        val text = "第一行有链接 点击这里 的文字\n第二行同样有正文，但没有任何链接"
        val start = text.indexOf("点击这里")
        val f = build(text, start, start + 4)
        val secondLine = f.layout.getLineForOffset(text.indexOf("第二行"))
        assertTrue(secondLine > f.layout.getLineForOffset(0))
        // 第二行中间点按：不能命中第一行的链接
        val x = padding + (f.layout.getLineLeft(secondLine) + f.layout.getLineRight(secondLine)) / 2f
        assertNull(ReaderTextLinkTouch.findClickableSpanAt(f.tv, x, f.centerYOfLine(secondLine)))
    }

    @Test
    fun tapBelowText_doesNotHit() {
        val f = singleLineFixture()
        val lastLine = f.layout.lineCount - 1
        val belowY = padding + f.layout.getLineBottom(lastLine) + 60f
        assertTrue(belowY < viewHeight)
        assertNull(ReaderTextLinkTouch.findClickableSpanAt(f.tv, f.viewMidX(), belowY))
    }

    private fun Fixture.viewMidX(): Float = viewWidth / 2f
}
