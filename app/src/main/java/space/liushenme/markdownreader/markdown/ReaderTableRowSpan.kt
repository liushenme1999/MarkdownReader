package space.liushenme.markdownreader.markdown

import android.graphics.Paint
import android.text.style.ReplacementSpan
import androidx.annotation.IntRange
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.ext.tables.TableTheme

/**
 * TextView [android.widget.TextView.setLineSpacing] 会按倍数放大每一行高度；
 * 表格偶数行无底色，被撑高后就像行与行之间插入了空白行。
 *
 * 宽屏 Pad 上单元格常排成单行，行高接近正文字号；旧逻辑用 `textSize` 阈值会误判为
 * 「尚未 layout」而跳过补偿，大屏反而出现假空行，手机窄屏换行后行高更大反而不触发。
 */
internal class ReaderTableRowSpan(
    theme: TableTheme,
    cells: List<TableRowSpan.Cell>,
    header: Boolean,
    odd: Boolean,
) : TableRowSpan(theme, cells, header, odd) {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        @IntRange(from = 0) start: Int,
        @IntRange(from = 0) end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val size = super.getSize(paint, text, start, end, fm)
        if (fm == null) return size
        if (!isTableRowMetrics(fm)) return size
        ReaderTableSpacing.compensateLineSpacing(fm, ReaderTableSpacing.lineSpacingMultiplier)
        return size
    }

    /** [TableRowSpan] layout 完成后固定 `descent=0` 且 `ascent<0`。 */
    private fun isTableRowMetrics(fm: Paint.FontMetricsInt): Boolean =
        fm.descent == 0 && fm.ascent < 0
}
