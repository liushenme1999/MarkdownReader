package io.noties.markwon.ext.latex

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.ImageSizeResolver
import io.noties.markwon.utils.SpanUtils
import ru.noties.jlatexmath.JLatexMathDrawable
import ru.noties.jlatexmath.awt.Color

/**
 * 行内公式与汉字垂直居中对齐（默认实现偏下，且在阅读行距倍数下更明显）。
 */
internal class ReaderInlineLatexSpan(
    theme: MarkwonTheme,
    drawable: JLatextAsyncDrawable,
    color: Int,
) : JLatexInlineAsyncDrawableSpan(theme, drawable, color) {

    private var appliedTextColor = color != 0

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val async = drawable()
        if (!async.hasResult()) {
            return super.getSize(paint, text, start, end, fm)
        }
        val height = spanHeight(async)
        if (fm != null) {
            applyFontMetrics(fm, paint, height)
        }
        return spanWidth(async)
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
        ensureTextColor(paint)
        val async = drawable()
        if (!async.hasResult()) {
            super.draw(canvas, text, start, end, x, top, y, bottom, paint)
            return
        }
        async.initWithKnownDimensions(resolveCanvasWidth(canvas, text), paint.textSize)
        ensureDrawableBounds(async)
        val h = spanHeight(async).toFloat()
        val centerY = y + (paint.ascent() + paint.descent()) / 2f
        val save = canvas.save()
        try {
            canvas.translate(x, centerY - h / 2f)
            async.draw(canvas)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    private fun ensureTextColor(paint: Paint) {
        if (appliedTextColor) return
        val result = drawable().result
        if (result is JLatexMathDrawable) {
            result.icon().setForeground(Color(paint.color))
            appliedTextColor = true
        }
    }

    private fun applyFontMetrics(fm: Paint.FontMetricsInt, paint: Paint, height: Int) {
        val targetH = height.coerceAtLeast(1)
        val center = ((paint.ascent() + paint.descent()) / 2f).toInt()
        fm.ascent = center - targetH / 2
        fm.descent = center + targetH / 2
        fm.top = fm.ascent
        fm.bottom = fm.descent
    }

    private fun spanWidth(async: JLatextAsyncDrawable): Int {
        val bounds = async.bounds
        if (bounds.width() > 0) return bounds.width()
        return async.intrinsicWidth.coerceAtLeast(1)
    }

    private fun spanHeight(async: JLatextAsyncDrawable): Int {
        val bounds = async.bounds
        if (bounds.height() > 0) return bounds.height()
        return async.intrinsicHeight.coerceAtLeast(1)
    }

    private fun resolveCanvasWidth(canvas: Canvas, text: CharSequence): Int {
        val measured = SpanUtils.width(canvas, text)
        if (measured > 0) return measured
        return canvas.width.coerceAtLeast(1)
    }

    private fun ensureDrawableBounds(async: JLatextAsyncDrawable) {
        if (async.bounds.width() > 0 && async.bounds.height() > 0) return
        val w = async.intrinsicWidth.coerceAtLeast(1)
        val h = async.intrinsicHeight.coerceAtLeast(1)
        async.setBounds(0, 0, w, h)
        async.result?.setBounds(0, 0, w, h)
    }
}

internal class ReaderInlineImageSizeResolver : ImageSizeResolver() {
    override fun resolveImageSize(drawable: AsyncDrawable): Rect {
        val imageBounds = drawable.result.bounds
        val canvasWidth = drawable.lastKnownCanvasWidth
        if (canvasWidth <= 0) {
            return imageBounds
        }
        val w = imageBounds.width()
        if (w > canvasWidth) {
            val ratio = w.toFloat() / imageBounds.height()
            val h = (canvasWidth / ratio + 0.5f).toInt()
            return Rect(0, 0, canvasWidth, h)
        }
        return imageBounds
    }
}
