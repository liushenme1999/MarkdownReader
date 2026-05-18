package com.example.markdownreader.ui.screens.reader

import android.text.Spanned
import android.widget.TextView
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan

/**
 * PDF 页图贴边：将 Markwon 图片 span 的绘制宽度拉满 TextView 内容区。
 */
internal object PdfImageLayoutHelper {

    fun scheduleApplyFullWidth(textView: TextView) {
        val apply = Runnable { applyFullWidthImages(textView) }
        textView.post(apply)
        textView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width > 0) applyFullWidthImages(v as TextView)
        }
    }

    fun applyFullWidthImages(textView: TextView) {
        val contentWidth = textView.width - textView.paddingLeft - textView.paddingRight
        if (contentWidth <= 0) return
        val text = textView.text as? Spanned ?: return
        val spans = text.getSpans(0, text.length, AsyncDrawableSpan::class.java)
        if (spans.isEmpty()) return

        var changed = false
        for (span in spans) {
            val drawable = span.drawable
            val async = drawable as? AsyncDrawable
            val intrinsicW = drawable.intrinsicWidth.coerceAtLeast(1)
            val intrinsicH = drawable.intrinsicHeight.coerceAtLeast(1)
            val targetH = (contentWidth.toFloat() * intrinsicH / intrinsicW).toInt().coerceAtLeast(1)
            if (drawable.bounds.width() != contentWidth || drawable.bounds.height() != targetH) {
                drawable.setBounds(0, 0, contentWidth, targetH)
                async?.initWithKnownDimensions(contentWidth, intrinsicH.toFloat() / intrinsicW)
                changed = true
            }
        }
        if (changed) {
            textView.invalidate()
            textView.requestLayout()
        }
    }
}
