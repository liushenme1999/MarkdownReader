package com.example.markdownreader.ui.screens.reader

import android.graphics.drawable.Drawable
import android.text.Spanned
import android.view.Gravity
import android.view.ViewTreeObserver
import android.widget.TextView
import io.noties.markwon.image.AsyncDrawableSpan

/**
 * PDF 页图贴边：将 Markwon 图片 span 拉满内容区宽度；
 * 横向翻页时在可视区内垂直居中（过高时缩小以完整显示并居中）。
 */
internal object PdfImageLayoutHelper {

    private const val TAG_PDF_LAYOUT_STATE = 0x4d445f50 // "MD_P"

    private data class PdfLayoutState(
        val basePaddingTop: Int,
        val basePaddingBottom: Int,
        val centerVertically: Boolean,
    )

    fun clearLayoutState(textView: TextView) {
        textView.setTag(TAG_PDF_LAYOUT_STATE, null)
    }

    fun applyPagedPdfTextGravity(textView: TextView, centerVertically: Boolean) {
        textView.gravity = if (centerVertically) {
            Gravity.CENTER_VERTICAL or Gravity.START
        } else {
            Gravity.TOP or Gravity.START
        }
    }

    fun scheduleApplyPdfPageLayout(textView: TextView, centerVertically: Boolean) {
        captureBasePadding(textView, centerVertically)
        applyPagedPdfTextGravity(textView, centerVertically)
        val apply = Runnable { applyPdfPageLayout(textView) }
        textView.post(apply)
        textView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width > 0 && v.height > 0) applyPdfPageLayout(v as TextView)
        }
        val observer = textView.viewTreeObserver
        if (observer.isAlive) {
            observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (textView.width > 0 && textView.height > 0) {
                        applyPdfPageLayout(textView)
                    }
                    if (shouldStopPreDraw(textView)) {
                        textView.viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    return true
                }
            })
        }
    }

    private fun shouldStopPreDraw(textView: TextView): Boolean {
        val state = layoutState(textView) ?: return true
        if (!state.centerVertically) return true
        val layout = textView.layout ?: return false
        val innerHeight = textView.height - state.basePaddingTop - state.basePaddingBottom
        if (innerHeight <= 0) return false
        val contentHeight = imageContentHeight(textView)
        return contentHeight > 0 && layout.lineCount > 0
    }

    fun applyPdfPageLayout(textView: TextView) {
        val state = layoutState(textView) ?: return
        applyPagedPdfTextGravity(textView, state.centerVertically)
        restoreBaseVerticalPadding(textView, state)
        applyFullWidthImages(textView, state)
    }

    private fun layoutState(textView: TextView): PdfLayoutState? =
        textView.getTag(TAG_PDF_LAYOUT_STATE) as? PdfLayoutState

    private fun captureBasePadding(textView: TextView, centerVertically: Boolean) {
        val existing = layoutState(textView)
        if (existing == null) {
            textView.setTag(
                TAG_PDF_LAYOUT_STATE,
                PdfLayoutState(
                    basePaddingTop = textView.paddingTop,
                    basePaddingBottom = textView.paddingBottom,
                    centerVertically = centerVertically,
                ),
            )
        } else {
            textView.setTag(TAG_PDF_LAYOUT_STATE, existing.copy(centerVertically = centerVertically))
        }
    }

    private fun restoreBaseVerticalPadding(textView: TextView, state: PdfLayoutState) {
        if (textView.paddingTop != state.basePaddingTop || textView.paddingBottom != state.basePaddingBottom) {
            textView.setPadding(
                textView.paddingLeft,
                state.basePaddingTop,
                textView.paddingRight,
                state.basePaddingBottom,
            )
        }
    }

    private fun imageContentHeight(textView: TextView): Int {
        val text = textView.text as? Spanned ?: return 0
        val spans = text.getSpans(0, text.length, AsyncDrawableSpan::class.java)
        if (spans.isEmpty()) return 0
        val drawableHeight = spans.maxOfOrNull { it.drawable.bounds.height() } ?: 0
        if (drawableHeight > 0) return drawableHeight
        val layout = textView.layout ?: return 0
        return layout.height.coerceAtLeast(0)
    }

    private fun applyFullWidthImages(textView: TextView, state: PdfLayoutState) {
        val baseTop = state.basePaddingTop
        val baseBottom = state.basePaddingBottom
        val contentWidth = textView.width - textView.paddingLeft - textView.paddingRight
        if (contentWidth <= 0) return
        val innerHeight = (textView.height - baseTop - baseBottom).coerceAtLeast(0)
        val text = textView.text as? Spanned ?: return
        val spans = text.getSpans(0, text.length, AsyncDrawableSpan::class.java)
        if (spans.isEmpty()) return

        var changed = false
        for (span in spans) {
            val drawable = span.drawable
            val intrinsicW = drawable.intrinsicWidth.coerceAtLeast(1)
            val intrinsicH = drawable.intrinsicHeight.coerceAtLeast(1)
            var targetW = contentWidth
            var targetH = (contentWidth.toFloat() * intrinsicH / intrinsicW).toInt().coerceAtLeast(1)
            if (state.centerVertically && innerHeight > 0 && targetH > innerHeight) {
                targetH = innerHeight
                targetW = (innerHeight.toFloat() * intrinsicW / intrinsicH).toInt().coerceAtLeast(1)
            }
            if (drawable.bounds.width() != targetW || drawable.bounds.height() != targetH) {
                drawable.setBounds(0, 0, targetW, targetH)
                drawable.initWithKnownDimensions(targetW, intrinsicH.toFloat() / intrinsicW)
                changed = true
            }
            installDrawableRelayoutCallback(textView, drawable)
        }
        if (changed) {
            textView.invalidate()
            textView.requestLayout()
        }
    }

    private fun installDrawableRelayoutCallback(textView: TextView, drawable: Drawable) {
        val previous = drawable.callback
        if (previous is RelayoutDrawableCallback && previous.textView === textView) return
        drawable.callback = RelayoutDrawableCallback(textView, previous)
    }

    private class RelayoutDrawableCallback(
        val textView: TextView,
        private val delegate: Drawable.Callback?,
    ) : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
            delegate?.invalidateDrawable(who)
            textView.post { applyPdfPageLayout(textView) }
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            delegate?.scheduleDrawable(who, what, `when`) ?: who.scheduleSelf(what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            delegate?.unscheduleDrawable(who, what) ?: who.unscheduleSelf(what)
        }
    }
}
