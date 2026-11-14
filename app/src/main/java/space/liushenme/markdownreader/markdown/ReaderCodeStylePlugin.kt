package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.RenderProps
import io.noties.markwon.SpanFactory
import io.noties.markwon.core.CoreProps
import io.noties.markwon.core.MarkwonTheme
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Node

internal object ReaderCodeStylePlugin {

    fun create(
        context: Context,
        paperColorArgb: Int = ReaderHighlightSurface.DEFAULT_PAPER_ARGB,
        codeHighlightTheme: CodeHighlightTheme = CodeHighlightTheme.DEFAULT,
        fullScreenCodeDocument: Boolean = false,
    ): MarkwonPlugin = object : AbstractMarkwonPlugin() {
        private val appContext = context.applicationContext
        private val paperArgb = paperColorArgb
        private val highlightTheme = codeHighlightTheme
        private val isFullScreenCodeDocument = fullScreenCodeDocument

        override fun configureTheme(builder: MarkwonTheme.Builder) {
            builder.codeBackgroundColor(Color.TRANSPARENT)
        }

        override fun configureVisitor(builder: MarkwonVisitor.Builder) {
            builder.on(FencedCodeBlock::class.java) { visitor, node ->
                visitCodeBlock(
                    visitor,
                    appContext,
                    highlightTheme,
                    isFullScreenCodeDocument,
                    node.info,
                    node.literal,
                    node,
                )
            }
            builder.on(IndentedCodeBlock::class.java) { visitor, node ->
                visitCodeBlock(
                    visitor,
                    appContext,
                    highlightTheme,
                    isFullScreenCodeDocument,
                    null,
                    node.literal,
                    node,
                )
            }
        }

        override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
            builder.setFactory(Code::class.java, ReaderInlineCodeSpanFactory(appContext, paperArgb))
        }
    }

    private class ReaderInlineCodeSpanFactory(
        private val context: Context,
        private val paperColorArgb: Int,
    ) : SpanFactory {
        override fun getSpans(configuration: MarkwonConfiguration, props: RenderProps): Any {
            return ReaderInlineCodeSpan(configuration.theme(), context, paperColorArgb)
        }
    }

    private fun visitCodeBlock(
        visitor: MarkwonVisitor,
        context: Context,
        highlightTheme: CodeHighlightTheme,
        fullScreenCodeDocument: Boolean,
        info: String?,
        code: String,
        node: Node,
    ) {
        visitor.blockStart(node)
        val start = visitor.length()
        val highlighted = visitor.configuration().syntaxHighlight().highlight(info, code)
        val density = context.resources.displayMetrics.density
        val span = ReaderScrollableCodeBlockSpan(
            theme = visitor.configuration().theme(),
            background = codeBlockBackground(context, highlightTheme, fullScreenCodeDocument),
            codeHighlightTheme = highlightTheme,
            fullScreenCodeDocument = fullScreenCodeDocument,
            padH = (12f * density + 0.5f).toInt(),
            padV = (8f * density + 0.5f).toInt(),
            content = highlighted,
            rawCode = code,
            languageInfo = info,
            wrapEnabled = ReaderCodeBlockSettings.wrapEnabled,
            showLineNumbers = ReaderCodeBlockSettings.showLineNumbers,
            density = density,
        )
        visitor.builder().append('\uFFFC')
        CoreProps.CODE_BLOCK_INFO.set(visitor.renderProps(), info)
        visitor.setSpans(start, span)
        visitor.blockEnd(node)
    }

    private fun codeBlockBackground(
        context: Context,
        highlightTheme: CodeHighlightTheme,
        fullScreenCodeDocument: Boolean,
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val strokePx = (1f * density + 0.5f).toInt().coerceAtLeast(1)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = if (fullScreenCodeDocument) 0f else 10f * density
            setColor(highlightTheme.palette.background)
            if (!fullScreenCodeDocument) {
                setStroke(strokePx, highlightTheme.palette.border)
            }
        }
    }
}
