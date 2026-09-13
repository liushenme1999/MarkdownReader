package io.noties.markwon.ext.latex

import android.content.Context
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.ext.latex.JLatexMathPlugin
import space.liushenme.markdownreader.markdown.ReaderHighlightSurface
import space.liushenme.markdownreader.markdown.ReaderLatexPreprocessor
import space.liushenme.markdownreader.markdown.ReaderMathSymbolFallback
import space.liushenme.markdownreader.markdown.ReaderMathSymbolSpan

/** 行内 `$...$`：JLatex、[ReaderMathSymbolSpan] 或 [ReaderCompoundInlineLatexSpan]。 */
internal class ReaderInlineLatexAlignPlugin(
    private val config: JLatexMathPlugin.Config,
    private val appContext: Context,
    private val paperColorArgb: Int = ReaderHighlightSurface.DEFAULT_PAPER_ARGB,
) : AbstractMarkwonPlugin() {

    private val loader = ReaderInlineLatexLoader(config)
    private val inlineImageSizeResolver = ReaderInlineImageSizeResolver()

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        if (!config.inlinesEnabled) return
        builder.on(JLatexMathNode::class.java) { visitor, node ->
            val rawLatex = ReaderLatexPreprocessor.preprocess(node.latex())
            val latex = normalizeInlineLatex(rawLatex)
            val length = visitor.length()
            val textColor = config.theme.inlineTextColor()

            val fallbackSymbol = ReaderMathSymbolFallback.symbolFor(latex)
                ?: ReaderMathSymbolFallback.symbolFor(rawLatex.trim())
            if (fallbackSymbol != null) {
                visitor.builder().append(fallbackSymbol)
                visitor.setSpans(length, ReaderMathSymbolSpan(fallbackSymbol, appContext, paperColorArgb))
                return@on
            }

            ReaderMathSymbolFallback.parseCompound(rawLatex.trim())?.let { segments ->
                visitor.builder().append(prepareInlineLatexPlaceholder(latex))
                visitor.setSpans(
                    length,
                    ReaderCompoundInlineLatexSpan(
                        segments = segments,
                        config = config,
                        context = appContext,
                        textColor = textColor,
                        sourceLatex = latex,
                        paperColorArgb = paperColorArgb,
                    ),
                )
                return@on
            }

            visitor.builder().append(prepareInlineLatexPlaceholder(latex))
            val configuration = visitor.configuration()
            val span = ReaderInlineLatexSpan(
                theme = configuration.theme(),
                drawable = JLatextAsyncDrawable(
                    latex,
                    loader,
                    inlineImageSizeResolver,
                    null,
                    false,
                ),
                color = textColor,
            )
            visitor.setSpans(length, span)
        }
    }
}
