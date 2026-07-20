package space.liushenme.markdownreader.markdown

import android.graphics.Paint
import android.text.style.ReplacementSpan
import androidx.annotation.IntRange
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.ext.tables.TableTheme

/**
 * TextView [android.widget.TextView.setLineSpacing] 会按倍数放大每一行高度；
 * 表格偶数行无底色，被撑高后就像行与行之间插入了空白行。
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
        val mult = ReaderTableSpacing.lineSpacingMultiplier
        if (mult <= 1f) return size
        val height = fm.descent - fm.ascent
        if (height <= 0) return size
        val target = (height / mult).toInt().coerceAtLeast(1)
        val ratio = target.toFloat() / height
        fm.ascent = (fm.ascent * ratio).toInt()
        fm.descent = (fm.descent * ratio).toInt()
        fm.top = fm.ascent
        fm.bottom = fm.descent
        return size
    }
}
