package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.text.style.ReplacementSpan

/** 行内数学符号：Noto Sans Math + 与行内公式一致的圆角边框底色。 */
internal class ReaderMathSymbolSpan(
    private val symbol: Char,
    private val context: Context,
    private val paperColorArgb: Int = ReaderHighlightSurface.DEFAULT_PAPER_ARGB,
) : ReplacementSpan() {

    private val density = context.resources.displayMetrics.density
    private val padH = (6f * density + 0.5f).toInt()
    private val padV = (4f * density + 0.5f).toInt()
    private val symbolText = symbol.toString()

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val symbolPaint = symbolPaint(paint)
        if (fm != null) {
            symbolPaint.getFontMetricsInt(fm)
            fm.ascent -= padV
            fm.descent += padV
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        return (symbolPaint.measureText(symbolText) + padH * 2 + 0.5f).toInt()
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
        val symbolPaint = symbolPaint(paint)
        val textWidth = symbolPaint.measureText(symbolText)
        val fm = symbolPaint.fontMetricsInt
        val textTop = y + fm.ascent
        val textBottom = y + fm.descent

        val background = ReaderLatexBlockStyle.inlineLatexBackground(context, paperColorArgb).mutate()
        background.setBounds(
            (x - padH).toInt(),
            textTop - padV,
            (x + textWidth + padH).toInt(),
            textBottom + padV,
        )
        background.draw(canvas)
        canvas.drawText(symbolText, x, y.toFloat(), symbolPaint)
    }

    private fun symbolPaint(base: Paint): TextPaint =
        TextPaint(base).apply {
            typeface = ReaderMathSymbolFont.typeface(context)
            bgColor = Color.TRANSPARENT
        }
}
