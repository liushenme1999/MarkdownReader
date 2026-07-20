package io.noties.markwon.ext.latex

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.text.style.ReplacementSpan
import ru.noties.jlatexmath.JLatexMathDrawable
import ru.noties.jlatexmath.awt.Color as JColor
import space.liushenme.markdownreader.markdown.ReaderLatexBlockStyle
import space.liushenme.markdownreader.markdown.ReaderMathSymbolFallback
import space.liushenme.markdownreader.markdown.ReaderMathSymbolFont

/**
 * 复合行内公式：JLatex 渲染文字/结构，`\otimes` 等符号用 Noto Sans Math 绘制。
 */
internal class ReaderCompoundInlineLatexSpan(
    segments: List<ReaderMathSymbolFallback.CompoundSegment>,
    private val config: JLatexMathPlugin.Config,
    private val context: Context,
    textColor: Int,
) : ReplacementSpan() {

    private val density = context.resources.displayMetrics.density
    private val padH = (6f * density + 0.5f).toInt()
    private val padV = (4f * density + 0.5f).toInt()
    private val textColorArgb = textColor
    private var appliedTextColor = textColor != 0
    private val textSizePx = config.theme.inlineTextSize()

    private sealed interface Piece {
        val width: Int
        val height: Int
        fun draw(canvas: Canvas, x: Float, centerY: Float, paint: Paint)
    }

    private class JlatexPiece(
        private val drawable: JLatexMathDrawable,
        private var colored: Boolean,
        private val colorArgb: Int,
    ) : Piece {
        override val width: Int = drawable.intrinsicWidth.coerceAtLeast(1)
        override val height: Int = drawable.intrinsicHeight.coerceAtLeast(1)

        override fun draw(canvas: Canvas, x: Float, centerY: Float, paint: Paint) {
            if (!colored && colorArgb == 0) {
                drawable.icon().setForeground(JColor(paint.color))
                colored = true
            }
            val save = canvas.save()
            try {
                canvas.translate(x, centerY - height / 2f)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
            } finally {
                canvas.restoreToCount(save)
            }
        }
    }

    private class SymbolPiece(
        private val symbol: Char,
        private val context: Context,
        private val textSizePx: Float,
        private val colorArgb: Int,
    ) : Piece {
        private val text = symbol.toString()
        override val width: Int
        override val height: Int
        private val symbolPaint = TextPaint()

        init {
            symbolPaint.typeface = ReaderMathSymbolFont.typeface(context)
            symbolPaint.textSize = textSizePx
            val fm = symbolPaint.fontMetricsInt
            width = symbolPaint.measureText(text).toInt().coerceAtLeast(1)
            height = (fm.descent - fm.ascent).coerceAtLeast(1)
        }

        override fun draw(canvas: Canvas, x: Float, centerY: Float, paint: Paint) {
            symbolPaint.color = if (colorArgb != 0) colorArgb else paint.color
            symbolPaint.bgColor = Color.TRANSPARENT
            canvas.drawText(text, x, centerY - (symbolPaint.descent() + symbolPaint.ascent()) / 2f, symbolPaint)
        }
    }

    private val pieces: List<Piece> = segments.mapNotNull { segment ->
        when (segment) {
            is ReaderMathSymbolFallback.CompoundSegment.Jlatex -> {
                val trimmed = segment.latex.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                JlatexPiece(
                    drawable = ReaderInlineLatexPlainDrawable.create(config, trimmed),
                    colored = appliedTextColor,
                    colorArgb = textColorArgb,
                )
            }
            is ReaderMathSymbolFallback.CompoundSegment.Symbol ->
                SymbolPiece(segment.char, context, textSizePx, textColorArgb)
        }
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val contentW = pieces.sumOf { it.width }
        val contentH = pieces.maxOfOrNull { it.height } ?: 0
        if (fm != null) {
            val center = ((paint.ascent() + paint.descent()) / 2f).toInt()
            val targetH = (contentH + padV * 2).coerceAtLeast(1)
            fm.ascent = center - targetH / 2
            fm.descent = center + targetH / 2
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        return contentW + padH * 2
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
        if (pieces.isEmpty()) return
        val contentW = pieces.sumOf { it.width }
        val boxTop = y + paint.ascent() - padV
        val boxBottom = y + paint.descent() + padV

        val background = ReaderLatexBlockStyle.inlineLatexBackground(context).mutate()
        background.setBounds(
            x.toInt(),
            boxTop.toInt(),
            (x + contentW + padH * 2).toInt(),
            boxBottom.toInt(),
        )
        background.draw(canvas)

        val centerY = y + (paint.ascent() + paint.descent()) / 2f
        var cursor = x + padH
        for (piece in pieces) {
            piece.draw(canvas, cursor, centerY, paint)
            cursor += piece.width
        }
    }
}

internal object ReaderInlineLatexPlainDrawable {
    fun create(config: JLatexMathPlugin.Config, latex: String): JLatexMathDrawable {
        val builder = JLatexMathDrawable.builder(latex)
            .textSize(config.theme.inlineTextSize())
        val color = config.theme.inlineTextColor()
        if (color != 0) {
            builder.color(color)
        }
        return builder.build()
    }
}
