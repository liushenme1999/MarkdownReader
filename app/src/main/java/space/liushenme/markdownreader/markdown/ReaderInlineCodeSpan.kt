package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.text.style.ReplacementSpan
import io.noties.markwon.core.MarkwonTheme

/** 行内 `` `code` ``：圆角底色，仅包裹代码本身（非整行）。 */
internal class ReaderInlineCodeSpan(
    private val theme: MarkwonTheme,
    private val context: Context,
) : ReplacementSpan() {

    private val density = context.resources.displayMetrics.density
    private val padH = (3f * density + 0.5f).toInt()
    private val padV = (2f * density + 0.5f).toInt()

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val codePaint = codePaint(paint)
        if (fm != null) {
            codePaint.getFontMetricsInt(fm)
            fm.ascent -= padV
            fm.descent += padV
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        return (codePaint.measureText(text, start, end) + padH * 2 + 0.5f).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val codePaint = codePaint(paint)
        val textWidth = codePaint.measureText(text, start, end)
        val fm = codePaint.fontMetricsInt
        val textTop = y + fm.ascent
        val textBottom = y + fm.descent

        val background = ReaderLatexBlockStyle.inlineCodeBackground(context).mutate()
        background.setBounds(
            x.toInt(),
            textTop - padV,
            (x + textWidth + padH * 2).toInt(),
            textBottom + padV,
        )
        background.draw(canvas)
        canvas.drawText(text, start, end, x + padH, y.toFloat(), codePaint)
    }

    private fun codePaint(base: Paint): TextPaint =
        TextPaint(base).apply {
            theme.applyCodeTextStyle(this)
            bgColor = Color.TRANSPARENT
        }
}
