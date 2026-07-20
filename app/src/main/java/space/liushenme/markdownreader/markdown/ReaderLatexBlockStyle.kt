package space.liushenme.markdownreader.markdown

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import io.noties.markwon.ext.latex.JLatexMathTheme
import ru.noties.jlatexmath.JLatexMathDrawable

/** 块级 / 行内 LaTeX 与行内代码的圆角底色；公式带描边，行内代码无边框。 */
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

    fun configureInlineTheme(
        context: Context,
        themeBuilder: JLatexMathTheme.Builder,
        density: Float,
    ) {
        val padH = (6f * density + 0.5f).toInt()
        val padV = (4f * density + 0.5f).toInt()
        themeBuilder
            .inlineBackgroundProvider { inlineLatexBackground(context) }
            .inlinePadding(JLatexMathTheme.Padding.of(padH, padV, padH, padV))
    }

    fun blockBackground(context: Context): Drawable =
        highlightBackground(context, cornerRadiusDp = 10f, bordered = true)

    fun inlineLatexBackground(context: Context): Drawable =
        highlightBackground(context, cornerRadiusDp = 6f, bordered = true)

    fun inlineCodeBackground(context: Context): Drawable =
        highlightBackground(context, cornerRadiusDp = 6f, bordered = false)

    fun highlightBackground(
        context: Context,
        cornerRadiusDp: Float,
        bordered: Boolean,
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val radius = cornerRadiusDp * density
        val strokePx = (1f * density + 0.5f).toInt().coerceAtLeast(1)
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val fill = if (isDark) Color.parseColor("#2A3140") else Color.parseColor("#F3F6FA")
        val stroke = if (bordered) {
            if (isDark) Color.parseColor("#536179") else Color.parseColor("#C8D3E0")
        } else {
            fill
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            setStroke(strokePx, stroke)
        }
    }
}
