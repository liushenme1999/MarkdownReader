package com.example.markdownreader.ui.screens.reader

import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.widget.TextView
import com.example.markdownreader.markdown.ReaderLinkResolver

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

    private fun spanAtOffset(spannable: Spanned, offset: Int): ClickableSpan? {
        return spannable.getSpans(offset, offset, ClickableSpan::class.java)
            .asSequence()
            .filter { offset >= spannable.getSpanStart(it) && offset < spannable.getSpanEnd(it) }
            .maxByOrNull { spannable.getSpanEnd(it) - spannable.getSpanStart(it) }
    }
}
