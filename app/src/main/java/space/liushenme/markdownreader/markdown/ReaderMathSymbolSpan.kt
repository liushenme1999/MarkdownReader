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
    private val padH = ReaderLatexBlockStyle.inlinePadHPx(density)
    private val padV = ReaderLatexBlockStyle.inlinePadVPx(density)
    private val symbolText = symbol.toString()

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val symbolPaint = symbolPaint(paint)
        val symbolFm = symbolPaint.fontMetricsInt
        val glyphH = (symbolFm.descent - symbolFm.ascent).coerceAtLeast(1)
        if (fm != null) {
            ReaderLatexBlockStyle.expandInlineLatexFontMetrics(fm, paint, glyphH, padV)
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
        val glyphH = symbolPaint.descent() - symbolPaint.ascent()
        val centerY = y + (paint.ascent() + paint.descent()) / 2f
        val boxHalf = glyphH / 2f + padV

        val background = ReaderLatexBlockStyle.inlineLatexBackground(context, paperColorArgb).mutate()
        background.setBounds(
            x.toInt(),
            (centerY - boxHalf).toInt(),
            (x + textWidth + padH * 2).toInt(),
            (centerY + boxHalf).toInt(),
        )
        background.draw(canvas)
        canvas.drawText(
            symbolText,
            x + padH,
            centerY - (symbolPaint.ascent() + symbolPaint.descent()) / 2f,
            symbolPaint,
        )
    }

    private fun symbolPaint(base: Paint): TextPaint =
        TextPaint(base).apply {
            typeface = ReaderMathSymbolFont.typeface(context)
            bgColor = Color.TRANSPARENT
        }
}
