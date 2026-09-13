package space.liushenme.markdownreader.markdown

import android.content.Context
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
        paperColorArgb: Int = ReaderHighlightSurface.DEFAULT_PAPER_ARGB,
    ) {
        val padH = (14f * density + 0.5f).toInt()
        val padV = (10f * density + 0.5f).toInt()
        themeBuilder
            .blockFitCanvas(true)
            .blockHorizontalAlignment(JLatexMathDrawable.ALIGN_CENTER)
            .blockPadding(JLatexMathTheme.Padding.of(padH, padV, padH, padV))
            .blockBackgroundProvider { blockBackground(context, paperColorArgb) }
    }

    fun configureInlineTheme(
        context: Context,
        themeBuilder: JLatexMathTheme.Builder,
        density: Float,
        paperColorArgb: Int = ReaderHighlightSurface.DEFAULT_PAPER_ARGB,
    ) {
        val padH = (6f * density + 0.5f).toInt()
        val padV = (4f * density + 0.5f).toInt()
        themeBuilder
            .inlineBackgroundProvider { inlineLatexBackground(context, paperColorArgb) }
            .inlinePadding(JLatexMathTheme.Padding.of(padH, padV, padH, padV))
    }

    fun blockBackground(
        context: Context,
        paperColorArgb: Int = ReaderHighlightSurface.DEFAULT_PAPER_ARGB,
    ): Drawable = highlightBackground(context, paperColorArgb, cornerRadiusDp = 10f, bordered = true)

    fun inlineLatexBackground(
        context: Context,
        paperColorArgb: Int = ReaderHighlightSurface.DEFAULT_PAPER_ARGB,
    ): Drawable = highlightBackground(context, paperColorArgb, cornerRadiusDp = 6f, bordered = true)

    fun inlineCodeBackground(
        context: Context,
        paperColorArgb: Int = ReaderHighlightSurface.DEFAULT_PAPER_ARGB,
    ): Drawable = highlightBackground(context, paperColorArgb, cornerRadiusDp = 6f, bordered = false)

    fun highlightBackground(
        context: Context,
        paperColorArgb: Int,
        cornerRadiusDp: Float,
        bordered: Boolean,
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val radius = cornerRadiusDp * density
        val strokePx = (1f * density + 0.5f).toInt().coerceAtLeast(1)
        val fill = ReaderHighlightSurface.fill(paperColorArgb)
        val stroke = if (bordered) ReaderHighlightSurface.stroke(paperColorArgb) else fill
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            setStroke(strokePx, stroke)
        }
    }
}
