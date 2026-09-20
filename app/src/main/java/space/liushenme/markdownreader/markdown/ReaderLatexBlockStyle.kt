package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import io.noties.markwon.ext.latex.JLatexMathTheme
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.max
import kotlin.math.min

/** 块级 / 行内 LaTeX 与行内代码的圆角底色；公式带描边，行内代码无边框。 */
internal object ReaderLatexBlockStyle {

    /** 行内公式左右留白（阴影）相对旧值 6dp 减半，避免多公式/列表项里底色抢位。 */
    const val INLINE_LATEX_PAD_H_DP = 3f
    const val INLINE_LATEX_PAD_V_DP = 4f

    /**
     * 行内代码底色：铺到与两侧邻字间隙的 [INLINE_CODE_SIDE_GAP_FRACTION]。
     * 行首/行尾没有邻字时，退回 [INLINE_CODE_PAD_H_DP]。
     */
    const val INLINE_CODE_PAD_H_DP = 2f
    const val INLINE_CODE_PAD_V_DP = 2f
    const val INLINE_CODE_CORNER_RADIUS_DP = 3f
    const val INLINE_CODE_SIDE_GAP_FRACTION = 1f / 3f

    fun inlinePadHPx(density: Float): Int = (INLINE_LATEX_PAD_H_DP * density + 0.5f).toInt()

    fun inlinePadVPx(density: Float): Int = (INLINE_LATEX_PAD_V_DP * density + 0.5f).toInt()

    fun inlineCodePadHPx(density: Float): Int = (INLINE_CODE_PAD_H_DP * density + 0.5f).toInt()

    fun inlineCodePadVPx(density: Float): Int = (INLINE_CODE_PAD_V_DP * density + 0.5f).toInt()

    /**
     * 按正文字号中线扩行高，不覆盖同行其它公式已经抬高的 [fm]。
     * 多个 ReplacementSpan 共用同一 [Paint.FontMetricsInt] 时，后者覆盖前者会导致阴影错位。
     */
    fun expandInlineLatexFontMetrics(
        fm: Paint.FontMetricsInt,
        paint: Paint,
        contentHeightPx: Int,
        padVPx: Int = 0,
    ) {
        val targetH = (contentHeightPx + padVPx * 2).coerceAtLeast(1)
        val center = ((paint.ascent() + paint.descent()) / 2f).toInt()
        val newAscent = center - targetH / 2
        val newDescent = center + targetH / 2
        fm.ascent = min(fm.ascent, newAscent)
        fm.descent = max(fm.descent, newDescent)
        fm.top = min(fm.top, fm.ascent)
        fm.bottom = max(fm.bottom, fm.descent)
    }

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
        val padH = inlinePadHPx(density)
        val padV = inlinePadVPx(density)
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
    ): Drawable = highlightBackground(
        context,
        paperColorArgb,
        cornerRadiusDp = INLINE_CODE_CORNER_RADIUS_DP,
        bordered = false,
    )

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
            if (bordered) {
                setStroke(strokePx, stroke)
            }
        }
    }
}
