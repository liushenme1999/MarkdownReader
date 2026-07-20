package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import space.liushenme.markdownreader.R

/** 行内数学符号回退字体（Noto Sans Math，覆盖 ⊗ 等系统默认字体缺失的符号）。 */
internal object ReaderMathSymbolFont {

    @Volatile
    private var cached: Typeface? = null

    fun typeface(context: Context): Typeface {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = ResourcesCompat.getFont(context.applicationContext, R.font.noto_sans_math)
            cached = loaded ?: Typeface.DEFAULT
            return cached!!
        }
    }

    fun canDisplay(context: Context, symbol: Char, referencePaint: Paint): Boolean {
        val paint = Paint(referencePaint).apply {
            typeface = typeface(context)
        }
        return paint.measureText(symbol.toString()) > 0f
    }
}
