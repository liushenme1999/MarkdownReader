package io.noties.markwon.ext.latex

import io.noties.markwon.image.AsyncDrawable
import ru.noties.jlatexmath.JLatexMathDrawable
import space.liushenme.markdownreader.markdown.ReaderLatexPreprocessor

/** 行内 JLatex  Drawable 工厂（与 Markwon 默认 inline 主题一致）。 */
internal object ReaderInlineLatexDrawableFactory {

    fun create(config: JLatexMathPlugin.Config, latex: String): JLatexMathDrawable {
        val theme = config.theme
        val builder = JLatexMathDrawable.builder(ReaderLatexPreprocessor.preprocess(latex))
            .textSize(theme.inlineTextSize())
        theme.inlineBackgroundProvider()?.let { provider ->
            builder.background(provider.provide())
        }
        theme.inlinePadding()?.let { padding ->
            builder.padding(padding.left, padding.top, padding.right, padding.bottom)
        }
        val color = theme.inlineTextColor()
        if (color != 0) {
            builder.color(color)
        }
        return builder.build()
    }
}

/**  lone 二元运算符在公式首尾时需加花括号，避免 JLatex 排版异常。 */
internal fun normalizeInlineLatex(latex: String): String {
    val trimmed = latex.trim()
    if (trimmed.matches(LONE_BIN_OPERATOR)) {
        return "{$trimmed}"
    }
    return trimmed
}

private val LONE_BIN_OPERATOR = Regex(
    """^\\(otimes|cdot|times|ast|star|pm|mp|div|cap|cup|vee|wedge|oplus|ominus|odot|oslash|circ|bullet|dagger|ddagger|ldots|cdots|vdots|dots|le|ge|ne|approx|equiv|sim|propto|mid|parallel|perp|rightarrow|leftarrow|Rightarrow|Leftarrow)(\*?)$""",
)

internal class ReaderInlineLatexLoader(
    private val config: JLatexMathPlugin.Config,
) : JLatexMathPlugin.JLatextAsyncDrawableLoader(config) {

    override fun placeholder(drawable: AsyncDrawable): android.graphics.drawable.Drawable? {
        if (drawable !is JLatextAsyncDrawable || drawable.isBlock) return null
        return runCatching {
            ReaderInlineLatexDrawableFactory.create(
                config = config,
                latex = normalizeInlineLatex(drawable.destination),
            )
        }.getOrNull()
    }
}
