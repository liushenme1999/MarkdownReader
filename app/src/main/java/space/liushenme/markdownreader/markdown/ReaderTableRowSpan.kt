package space.liushenme.markdownreader.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.IntRange
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.utils.ColorUtils
import io.noties.markwon.utils.SpanUtils

/**
 * TextView [android.widget.TextView.setLineSpacing] 会按倍数放大每一行高度；
 * 表格偶数行无底色，被撑高后就像行与行之间插入了空白行。
 *
 * 宽屏 Pad 上单元格常排成单行，行高接近正文字号；旧逻辑用 `textSize` 阈值会误判为
 * 「尚未 layout」而跳过补偿，大屏反而出现假空行，手机窄屏换行后行高更大反而不触发。
 *
 * 另外 Markwon 默认每行是 `NBSP` + `\n`：满宽 ReplacementSpan 会在 MIUI 上把 `\n` 挤成
 * 独立空行（日志里 EMPTY h=72）。表内行改为连续 NBSP、靠满宽自行换行；行高由
 * [ReaderTableRowLineHeightSpan] 收敛。
 *
 * Markwon 默认表头/偶数行背景透明，宽屏上透明行看起来像「夹在灰行之间的空白行」；
 * [draw] 用当前文字色补一层浅底，避免这种假空行。
 */
internal class ReaderTableRowSpan(
    theme: TableTheme,
    cells: List<TableRowSpan.Cell>,
    private val rowHeader: Boolean,
    private val rowOdd: Boolean,
) : TableRowSpan(theme, cells, rowHeader, rowOdd) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillRect = Rect()

    /** 内部 StaticLayout 已建立，[getSize] 已写入表格行 metrics。 */
    @Volatile
    var metricsReady: Boolean = false
        private set

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        @IntRange(from = 0) start: Int,
        @IntRange(from = 0) end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val size = super.getSize(paint, text, start, end, fm)
        if (fm != null && isTableRowMetrics(fm)) {
            metricsReady = true
        }
        // 表内行之间已不再插入 `\n`，必须返回满宽，让连续 NBSP 各自独占一行。
        ReaderTableDebug.logGetSize(
            start = start,
            end = end,
            rawSize = size,
            returnedSize = size,
            metricsReady = metricsReady,
            fmAscent = fm?.ascent,
            fmDescent = fm?.descent,
        )
        return size
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
        // 表头/偶数行默认透明 → 纸色露出来像空行；奇数行仍走 Markwon 自带斑马纹。
        if (rowHeader || !rowOdd) {
            val alpha = if (rowHeader) HEADER_BG_ALPHA else EVEN_BG_ALPHA
            fillPaint.color = ColorUtils.applyAlpha(paint.color, alpha)
            fillPaint.style = Paint.Style.FILL
            val w = SpanUtils.width(canvas, text).coerceAtLeast(0)
            val save = canvas.save()
            try {
                fillRect.set(0, 0, w, bottom - top)
                canvas.translate(x, top.toFloat())
                canvas.drawRect(fillRect, fillPaint)
            } finally {
                canvas.restoreToCount(save)
            }
        }
        super.draw(canvas, text, start, end, x, top, y, bottom, paint)
    }

    /** [TableRowSpan] layout 完成后固定 `descent=0` 且 `ascent<0`。 */
    private fun isTableRowMetrics(fm: Paint.FontMetricsInt): Boolean =
        fm.descent == 0 && fm.ascent < 0

    private companion object {
        const val HEADER_BG_ALPHA = 30
        const val EVEN_BG_ALPHA = 12
    }
}
