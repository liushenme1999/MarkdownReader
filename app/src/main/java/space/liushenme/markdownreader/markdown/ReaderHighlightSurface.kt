package space.liushenme.markdownreader.markdown

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

/** 代码块 / 公式底色：按阅读纸面亮度向黑或白插值，只要和正文区分即可。 */
internal object ReaderHighlightSurface {

    /** 与 [space.liushenme.markdownreader.ui.theme.ReadingTheme.Paper] 背景一致。 */
    const val DEFAULT_PAPER_ARGB = 0xFFF5F0E1.toInt()

    private const val LIGHT_FILL_TOWARD_BLACK = 0.08f
    private const val LIGHT_STROKE_TOWARD_BLACK = 0.16f
    private const val DARK_FILL_TOWARD_WHITE = 0.14f
    private const val DARK_STROKE_TOWARD_WHITE = 0.24f

    fun isDarkPaper(paperArgb: Int): Boolean =
        ColorUtils.calculateLuminance(paperArgb) < 0.5

    fun fill(paperArgb: Int): Int {
        val dark = isDarkPaper(paperArgb)
        val target = if (dark) Color.WHITE else Color.BLACK
        val amount = if (dark) DARK_FILL_TOWARD_WHITE else LIGHT_FILL_TOWARD_BLACK
        return lerpRgb(paperArgb, target, amount)
    }

    fun stroke(paperArgb: Int): Int {
        val dark = isDarkPaper(paperArgb)
        val target = if (dark) Color.WHITE else Color.BLACK
        val amount = if (dark) DARK_STROKE_TOWARD_WHITE else LIGHT_STROKE_TOWARD_BLACK
        return lerpRgb(paperArgb, target, amount)
    }

    private fun lerpRgb(from: Int, to: Int, t: Float): Int {
        val r = Color.red(from) + ((Color.red(to) - Color.red(from)) * t).roundToInt()
        val g = Color.green(from) + ((Color.green(to) - Color.green(from)) * t).roundToInt()
        val b = Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * t).roundToInt()
        return Color.argb(
            0xFF,
            r.coerceIn(0, 255),
            g.coerceIn(0, 255),
            b.coerceIn(0, 255),
        )
    }
}
