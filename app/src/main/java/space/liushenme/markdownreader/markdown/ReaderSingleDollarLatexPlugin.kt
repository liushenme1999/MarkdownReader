package space.liushenme.markdownreader.markdown

import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.inlineparser.InlineProcessor
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.ext.latex.JLatexMathNode
import org.commonmark.node.Node
import java.util.regex.Pattern

/**
 * Markwon 自带 [io.noties.markwon.ext.latex.JLatexMathInlineProcessor] 仅匹配 `$$...$$`；
 * 本处理器补充 GFM 常见的行内 `$...$`（如 `$x^2$`、`$y_1$`），同样输出 [JLatexMathNode]。
 */
internal class ReaderSingleDollarLatexInlineProcessor : InlineProcessor() {

    override fun specialCharacter(): Char = '$'

    override fun parse(): Node? {
        val matched = match(RE) ?: return null
        if (matched.length < 3) return null
        val body = matched.substring(1, matched.length - 1).trim()
        if (body.isEmpty()) return null
        return JLatexMathNode().apply { latex(body) }
    }

    companion object {
        private val RE = Pattern.compile("""(?<!\$)\$([^$\n]+?)\$(?!\$)""")
    }
}

internal object ReaderSingleDollarLatexPlugin {

    fun create(): AbstractMarkwonPlugin = object : AbstractMarkwonPlugin() {
        override fun configure(registry: MarkwonPlugin.Registry) {
            registry.require(MarkwonInlineParserPlugin::class.java)
                .factoryBuilder()
                .addInlineProcessor(ReaderSingleDollarLatexInlineProcessor())
        }
    }
}
