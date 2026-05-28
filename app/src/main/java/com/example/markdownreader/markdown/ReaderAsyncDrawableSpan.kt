package com.example.markdownreader.markdown

import android.graphics.Canvas
import android.graphics.Paint
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.utils.SpanUtils

/**
 * 图片行顶对齐：TextView [android.widget.TextView.setLineSpacing] 放大行高时，
 * Markwon 默认 [AsyncDrawableSpan.ALIGN_BOTTOM] 会把图片贴在行框底部，在行首留下大块空白。
 */
internal class ReaderAsyncDrawableSpan(
    theme: MarkwonTheme,
    drawable: AsyncDrawable,
    replacementTextIsLink: Boolean,
) : AsyncDrawableSpan(
    theme,
    drawable,
    AsyncDrawableSpan.ALIGN_BOTTOM,
    replacementTextIsLink,
) {

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
        val async = drawable
        if (async.hasResult()) {
            val save = canvas.save()
            try {
                val lineW = SpanUtils.width(canvas, text)
                val intrinsicW = async.intrinsicWidth.coerceAtLeast(1)
                val aspect = async.intrinsicHeight.toFloat() / intrinsicW
                async.initWithKnownDimensions(lineW, aspect)
                val dW = async.bounds.width().toFloat()
                val dx = if (dW in 1f..lineW.toFloat()) (lineW - dW) / 2f else 0f
                canvas.translate(x + dx, top.toFloat())
                async.draw(canvas)
            } finally {
                canvas.restoreToCount(save)
            }
        } else {
            super.draw(canvas, text, start, end, x, top, y, bottom, paint)
        }
    }
}
