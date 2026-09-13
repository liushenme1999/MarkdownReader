package io.noties.markwon.ext.latex

import android.content.Context
import io.noties.markwon.AbstractMarkwonPlugin
import ru.noties.jlatexmath.JLatexMathDrawable
import space.liushenme.markdownreader.markdown.ReaderHighlightSurface
import space.liushenme.markdownreader.markdown.ReaderLatexBlockStyle
import space.liushenme.markdownreader.markdown.ReaderLatexPreprocessor

/** 组装 JLatex 块级 + 行内（垂直居中）插件。 */
object ReaderLatexPlugins {

    fun create(
        context: Context,
        latexTextSize: Float,
        density: Float,
        paperColorArgb: Int = ReaderHighlightSurface.DEFAULT_PAPER_ARGB,
    ): List<AbstractMarkwonPlugin> {
        val appContext = context.applicationContext
        val builder = JLatexMathPlugin.builder(latexTextSize)
            .inlinesEnabled(true)
            .blocksEnabled(true)
            .errorHandler { latex, _ ->
                val fixed = ReaderLatexPreprocessor.preprocess(latex)
                if (fixed == latex) return@errorHandler null
                runCatching {
                    JLatexMathDrawable.builder(fixed)
                        .textSize(latexTextSize)
                        .build()
                }.getOrNull()
            }
        ReaderLatexBlockStyle.configureBlockTheme(
            context = appContext,
            themeBuilder = builder.theme(),
            density = density,
            paperColorArgb = paperColorArgb,
        )
        ReaderLatexBlockStyle.configureInlineTheme(
            context = appContext,
            themeBuilder = builder.theme(),
            density = density,
            paperColorArgb = paperColorArgb,
        )
        val config = builder.build()
        return listOf(
            JLatexMathPlugin.create(config),
            ReaderInlineLatexAlignPlugin(config, appContext, paperColorArgb),
        )
    }
}
