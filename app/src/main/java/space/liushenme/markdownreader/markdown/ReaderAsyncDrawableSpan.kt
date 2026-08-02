package space.liushenme.markdownreader.markdown

import android.graphics.Canvas
import android.graphics.Paint
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.utils.SpanUtils

/**
 * 图片行顶对齐，并抵消 [android.widget.TextView.setLineSpacing] 对行高的放大。
 *
 * Markwon 默认底对齐时，多余行高会出现在图片上方；仅改为顶对齐绘制后，
 * 若不在 [getSize] 里补偿，多余高度会落到图片下方形成大块空白。
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

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val size = super.getSize(paint, text, start, end, fm)
        if (fm != null) {
            ReaderTableSpacing.compensateLineSpacing(
                fm,
                ReaderTableSpacing.lineSpacingMultiplier,
            )
        }
        return size
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
        val async = drawable
        if (async.hasResult()) {
            val save = canvas.save()
            try {
                val lineW = SpanUtils.width(canvas, text)
                async.initWithKnownDimensions(lineW, paint.textSize)
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
