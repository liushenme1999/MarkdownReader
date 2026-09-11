package space.liushenme.markdownreader.ui.screens.reader

import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.widget.TextView
import space.liushenme.markdownreader.markdown.ReaderLinkResolver
import io.noties.markwon.image.AsyncDrawableSpan

/** 在 [TextView] 可编辑/可选中模式下，显式命中 [ClickableSpan]（含 Markwon 链接与图片外链）。 */
internal object ReaderTextLinkTouch {

    fun dispatchClickableSpan(textView: TextView, span: ClickableSpan) {
        if (span is URLSpan) {
            ReaderLinkResolver().resolve(textView, span.url)
        } else {
            span.onClick(textView)
        }
    }

    fun findClickableSpanAt(textView: TextView, x: Float, y: Float): ClickableSpan? {
        val spannable = textView.text as? Spanned ?: return null
        val length = spannable.length
        if (length == 0) return null

        findLinkedDrawableClickableSpan(textView, spannable, x, y)?.let { return it }

        val offset = textView.getOffsetForPosition(x, y).coerceIn(0, length - 1)
        spanAtOffset(spannable, offset)?.let { return it }

        val layout = textView.layout ?: return null
        val contentY = (y + textView.scrollY - textView.totalPaddingTop).toInt().coerceAtLeast(0)
        val line = layout.getLineForVertical(contentY).coerceIn(0, layout.lineCount - 1)
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line).coerceAtMost(length)
        return spannable.getSpans(lineStart, lineEnd, ClickableSpan::class.java)
            .asSequence()
            .filter { span ->
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                start < lineEnd && end > lineStart
            }
            .maxByOrNull { spannable.getSpanEnd(it) - spannable.getSpanStart(it) }
    }

    /**
     * 按可视区域命中 [AsyncDrawableSpan]，避免 `\uFFFC` 的 getOffsetForPosition 偏到行尾。
     */
    fun findAsyncDrawableSpanAt(textView: TextView, x: Float, y: Float): AsyncDrawableSpan? {
        val spannable = textView.text as? Spanned ?: return null
        val layout = textView.layout ?: return null
        if (spannable.isEmpty()) return null
        val contentX = x - textView.totalPaddingLeft
        if (contentX < 0f) return null
        val maxContentX = (textView.width - textView.totalPaddingLeft - textView.totalPaddingRight).toFloat()
        if (maxContentX > 0f && contentX > maxContentX) return null
        val contentY = (y + textView.scrollY - textView.totalPaddingTop).toInt().coerceAtLeast(0)
        val line = layout.getLineForVertical(contentY).coerceIn(0, layout.lineCount - 1)
        val lineStart = layout.getLineStart(line).coerceIn(0, spannable.length)
        val lineEnd = layout.getLineEnd(line).coerceAtMost(spannable.length)
        return spannable.getSpans(lineStart, lineEnd, AsyncDrawableSpan::class.java)
            .firstOrNull { drawableSpan ->
                val spanStart = spannable.getSpanStart(drawableSpan)
                val spanEnd = spannable.getSpanEnd(drawableSpan)
                spanStart >= 0 && spanEnd > spanStart &&
                    isTouchInDrawableSpanBounds(textView, layout, drawableSpan, spanStart, spanEnd, x, y)
            }
    }

    /**
     * 图片占位符 `\uFFFC` 的 getOffsetForPosition 常偏到行尾；按 AsyncDrawableSpan / ImageSpan 可视区域命中外层链接。
     */
    private fun findLinkedDrawableClickableSpan(
        textView: TextView,
        spannable: Spanned,
        x: Float,
        y: Float,
    ): ClickableSpan? {
        val layout = textView.layout ?: return null
        val contentX = x - textView.totalPaddingLeft
        if (contentX < 0f) return null
        val maxContentX = (textView.width - textView.totalPaddingLeft - textView.totalPaddingRight).toFloat()
        if (maxContentX > 0f && contentX > maxContentX) return null
        val contentY = (y + textView.scrollY - textView.totalPaddingTop).toInt().coerceAtLeast(0)
        val line = layout.getLineForVertical(contentY).coerceIn(0, layout.lineCount - 1)
        val lineStart = layout.getLineStart(line).coerceIn(0, spannable.length)
        val lineEnd = layout.getLineEnd(line).coerceAtMost(spannable.length)

        spannable.getSpans(lineStart, lineEnd, AsyncDrawableSpan::class.java).forEach { drawableSpan ->
            clickableSpanOnDrawable(textView, spannable, layout, drawableSpan, x, y)?.let { return it }
        }
        spannable.getSpans(lineStart, lineEnd, ImageSpan::class.java).forEach { imageSpan ->
            clickableSpanOnDrawable(textView, spannable, layout, imageSpan, x, y)?.let { return it }
        }
        return null
    }

    private fun clickableSpanOnDrawable(
        textView: TextView,
        spannable: Spanned,
        layout: Layout,
        drawableSpan: Any,
        x: Float,
        y: Float,
    ): ClickableSpan? {
        val spanStart = spannable.getSpanStart(drawableSpan)
        val spanEnd = spannable.getSpanEnd(drawableSpan)
        if (spanStart < 0 || spanEnd <= spanStart) return null
        if (!isTouchInDrawableSpanBounds(textView, layout, drawableSpan, spanStart, spanEnd, x, y)) {
            return null
        }
        return spannable.getSpans(spanStart, spanEnd, ClickableSpan::class.java)
            .maxByOrNull { spannable.getSpanEnd(it) - spannable.getSpanStart(it) }
    }

    private fun isTouchInDrawableSpanBounds(
        textView: TextView,
        layout: Layout,
        drawableSpan: Any,
        spanStart: Int,
        spanEnd: Int,
        x: Float,
        y: Float,
    ): Boolean {
        val contentX = x - textView.totalPaddingLeft
        val contentY = y + textView.scrollY - textView.totalPaddingTop
        val line = layout.getLineForVertical(contentY.toInt().coerceAtLeast(0))
        val lineTop = layout.getLineTop(line).toFloat()
        val lineBottom = layout.getLineBottom(line).toFloat()
        if (contentY < lineTop || contentY > lineBottom) return false

        val left = layout.getPrimaryHorizontal(spanStart)
        val right = when (drawableSpan) {
            is AsyncDrawableSpan -> {
                val drawable = drawableSpan.drawable
                if (drawable.hasResult()) {
                    val dw = drawable.bounds.width().toFloat()
                    val lineW = (textView.width - textView.totalPaddingLeft - textView.totalPaddingRight)
                        .toFloat()
                    // ReaderAsyncDrawableSpan 会把窄图水平居中
                    if (drawableSpan is space.liushenme.markdownreader.markdown.ReaderAsyncDrawableSpan &&
                        dw in 1f..lineW
                    ) {
                        val centeredLeft = left + (lineW - dw) / 2f
                        return contentX >= centeredLeft && contentX <= centeredLeft + dw
                    }
                    left + dw
                } else {
                    layout.getPrimaryHorizontal(spanEnd.coerceAtMost(textView.text.length))
                }
            }
            is ImageSpan -> {
                val drawable = drawableSpan.drawable
                left + drawable.bounds.width().coerceAtLeast(drawable.intrinsicWidth.coerceAtLeast(1))
            }
            else -> layout.getPrimaryHorizontal((spanEnd - 1).coerceAtLeast(spanStart))
        }
        return contentX >= left && contentX <= right
    }

    private fun spanAtOffset(spannable: Spanned, offset: Int): ClickableSpan? {
        return spannable.getSpans(offset, offset, ClickableSpan::class.java)
            .asSequence()
            .filter { offset >= spannable.getSpanStart(it) && offset < spannable.getSpanEnd(it) }
            .maxByOrNull { spannable.getSpanEnd(it) - spannable.getSpanStart(it) }
    }
}

/**
 * 所有链接（含 HTML `<a><img></a>` 与 Linkify 生成的 [URLSpan]）统一走 [ReaderLinkResolver]，
 * 避免系统 [URLSpan.onClick] 直接拉起浏览器并跳过阅读进度落盘。
 */
internal class ReaderLinkMovementMethod : LinkMovementMethod() {

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val span = ReaderTextLinkTouch.findClickableSpanAt(widget, event.x, event.y)
            if (span != null) {
                ReaderTextLinkTouch.dispatchClickableSpan(widget, span)
                return true
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    companion object {
        private val instance = ReaderLinkMovementMethod()

        @JvmStatic
        fun getInstance(): ReaderLinkMovementMethod = instance
    }
}
