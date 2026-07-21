package space.liushenme.markdownreader.markdown

import android.graphics.Paint
import android.text.Spanned
import android.text.style.LineHeightSpan

/**
 * 表格行行末常跟 `\n`：它会把 [TableRowSpan] 设好的 `descent=0` 重新撑开，
 * 再被 [android.widget.TextView.setLineSpacing] 放大，看起来像行间空白。
 *
 * 在整行 metrics 汇总之后收敛：清掉 `\n` 的 descent，再抵消行距倍数。
 */
internal class ReaderTableRowLineHeightSpan : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: Paint.FontMetricsInt,
    ) {
        val beforeA = fm.ascent
        val beforeD = fm.descent
        val spanned = text as? Spanned
        if (spanned == null) {
            ReaderTableDebug.logChooseHeight(start, end, "notSpanned", beforeA, beforeD, null, null, false)
            return
        }
        val row = spanned.getSpans(start, end, ReaderTableRowSpan::class.java).firstOrNull()
        if (row == null) {
            ReaderTableDebug.logChooseHeight(start, end, "noRowSpan", beforeA, beforeD, null, null, false)
            return
        }
        if (!row.metricsReady) {
            ReaderTableDebug.logChooseHeight(start, end, "metricsNotReady", beforeA, beforeD, null, null, false)
            return
        }
        if (fm.ascent >= 0) {
            ReaderTableDebug.logChooseHeight(start, end, "ascent>=0", beforeA, beforeD, null, null, true)
            return
        }

        fm.descent = 0
        fm.bottom = 0
        fm.top = fm.ascent
        ReaderTableSpacing.compensateLineSpacing(fm, ReaderTableSpacing.lineSpacingMultiplier)
        ReaderTableDebug.logChooseHeight(
            start = start,
            end = end,
            skipped = null,
            beforeAscent = beforeA,
            beforeDescent = beforeD,
            afterAscent = fm.ascent,
            afterDescent = fm.descent,
            metricsReady = true,
        )
    }
}
