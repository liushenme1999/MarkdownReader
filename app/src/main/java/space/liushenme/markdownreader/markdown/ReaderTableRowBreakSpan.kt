package space.liushenme.markdownreader.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spanned
import android.text.style.LineHeightSpan
import android.text.style.ReplacementSpan
import androidx.annotation.IntRange

/**
 * 盖住表格行之间的 `\n`：MIUI 上满宽 [ReaderTableRowSpan] 会把 `\n` 挤到下一行，
 * 若不处理会留下 h≈正文字号的空白行。
 *
 * - [ReplacementSpan]：度量宽度为 0
 * - [LineHeightSpan]：仅当该行没有表格行 span 时把行高压成 0
 *   （与 NBSP 同行时绝不动，避免把表格行一并压扁）
 */
internal class ReaderTableRowBreakSpan : ReplacementSpan(), LineHeightSpan {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        @IntRange(from = 0) start: Int,
        @IntRange(from = 0) end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        // 与表格行同行时不要在 getSize 里写 0 metrics（会压扁整行）；
        // 单独成行时靠 chooseHeight 清零。
        return 0
    }

    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: Paint.FontMetricsInt,
    ) {
        val spanned = text as? Spanned ?: return
        // 本行若还有表格行 NBSP，说明 \n 尚未被挤出，不能清零。
        if (spanned.getSpans(start, end, ReaderTableRowSpan::class.java).isNotEmpty()) {
            return
        }
        fm.ascent = 0
        fm.descent = 0
        fm.top = 0
        fm.bottom = 0
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        @IntRange(from = 0) start: Int,
        @IntRange(from = 0) end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        // no-op
    }
}
