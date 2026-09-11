package space.liushenme.markdownreader.markdown

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
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

    fun create(context: Context): MarkwonPlugin = object : AbstractMarkwonPlugin() {
        private val appContext = context.applicationContext

        override fun configureTheme(builder: MarkwonTheme.Builder) {
            builder.codeBackgroundColor(Color.TRANSPARENT)
        }

        override fun configureVisitor(builder: MarkwonVisitor.Builder) {
            builder.on(FencedCodeBlock::class.java) { visitor, node ->
                visitCodeBlock(visitor, appContext, node.info, node.literal, node)
            }
            builder.on(IndentedCodeBlock::class.java) { visitor, node ->
                visitCodeBlock(visitor, appContext, null, node.literal, node)
            }
        }

        override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
            builder.setFactory(Code::class.java, ReaderInlineCodeSpanFactory(appContext))
        }
    }

    private class ReaderInlineCodeSpanFactory(
        private val context: Context,
    ) : SpanFactory {
        override fun getSpans(configuration: MarkwonConfiguration, props: RenderProps): Any {
            return ReaderInlineCodeSpan(configuration.theme(), context)
        }
    }

    private fun visitCodeBlock(
        visitor: MarkwonVisitor,
        context: Context,
        info: String?,
        code: String,
        node: Node,
    ) {
        visitor.blockStart(node)
        val start = visitor.length()
        val highlighted = visitor.configuration().syntaxHighlight().highlight(info, code)
        val density = context.resources.displayMetrics.density
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val span = ReaderScrollableCodeBlockSpan(
            theme = visitor.configuration().theme(),
            background = ReaderLatexBlockStyle.blockBackground(context).mutate(),
            padH = (12f * density + 0.5f).toInt(),
            padV = (8f * density + 0.5f).toInt(),
            content = highlighted,
            rawCode = code.trimEnd('\n', '\r'),
            languageInfo = info,
            wrapEnabled = ReaderCodeBlockSettings.wrapEnabled,
            density = density,
            isDark = isDark,
        )
        visitor.builder().append('\uFFFC')
        CoreProps.CODE_BLOCK_INFO.set(visitor.renderProps(), info)
        visitor.setSpans(start, span)
        visitor.blockEnd(node)
    }
}
