package space.liushenme.markdownreader.markdown

import android.graphics.Paint

/** 供表格行 span 抵消 [android.widget.TextView.setLineSpacing] 对行高的放大。 */
internal object ReaderTableSpacing {
    private val multiplier = ThreadLocal.withInitial { 1.5f }

    var lineSpacingMultiplier: Float
        get() = multiplier.get() ?: 1.5f
        set(value) { multiplier.set(value) }

    /** 将 font metrics 高度设为 contentHeight，使乘以 multiplier 后接近 contentHeight。 */
    fun compensateLineSpacing(fm: Paint.FontMetricsInt, multiplier: Float) {
        if (multiplier <= 1.001f) return
        val height = fm.descent - fm.ascent
        if (height <= 0) return
        val target = (height / multiplier + 0.5f).toInt().coerceAtLeast(1)
        if (target >= height) return
        fm.ascent = -target
        fm.descent = 0
        fm.top = fm.ascent
        fm.bottom = fm.descent
    }
}
