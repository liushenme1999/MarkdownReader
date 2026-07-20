package space.liushenme.markdownreader.markdown

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import io.noties.markwon.ext.latex.JLatexMathTheme
import ru.noties.jlatexmath.JLatexMathDrawable

/** 块级 LaTeX（`$$…$$`）在阅读器中的居中、底色与边框。 */
internal object ReaderLatexBlockStyle {

    fun configureBlockTheme(
        context: Context,
        themeBuilder: JLatexMathTheme.Builder,
        density: Float,
    ) {
        val padH = (14f * density + 0.5f).toInt()
        val padV = (10f * density + 0.5f).toInt()
        themeBuilder
            .blockFitCanvas(true)
            .blockHorizontalAlignment(JLatexMathDrawable.ALIGN_CENTER)
            .blockPadding(JLatexMathTheme.Padding.of(padH, padV, padH, padV))
            .blockBackgroundProvider { blockBackground(context) }
    }

    fun blockBackground(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        val radius = 10f * density
        val strokePx = (1f * density + 0.5f).toInt().coerceAtLeast(1)
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val fill = if (isDark) Color.parseColor("#2A3140") else Color.parseColor("#F3F6FA")
        val stroke = if (isDark) Color.parseColor("#536179") else Color.parseColor("#C8D3E0")
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            setStroke(strokePx, stroke)
        }
    }
}
