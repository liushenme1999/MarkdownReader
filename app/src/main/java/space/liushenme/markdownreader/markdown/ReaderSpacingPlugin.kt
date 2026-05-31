package space.liushenme.markdownreader.markdown

import android.content.Context
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.MarkwonTheme
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.Text

/**
 * 收紧 Markwon 块级边距，减轻标题下划线（H1/H2 分割线）与后续内容之间的空隙。
 */
internal object ReaderSpacingPlugin {

    fun create(context: Context): MarkwonPlugin = object : AbstractMarkwonPlugin() {
        override fun configureTheme(builder: MarkwonTheme.Builder) {
            val dip = context.resources.displayMetrics.density
            builder
                .blockMargin((8f * dip + 0.5f).toInt())
                .headingBreakHeight((1f * dip + 0.5f).toInt())
        }

        override fun configureVisitor(builder: MarkwonVisitor.Builder) {
            builder.blockHandler(ReaderBlockHandler())
        }
    }
}

/**
 * 标题后紧跟图片时不再 [MarkwonVisitor.forceNewLine]，避免多出一行空行。
 */
private class ReaderBlockHandler : MarkwonVisitor.BlockHandler {
    override fun blockStart(visitor: MarkwonVisitor, node: Node) {
        visitor.ensureNewLine()
    }

    override fun blockEnd(visitor: MarkwonVisitor, node: Node) {
        if (!visitor.hasNext(node)) return
        if (node is Heading && headingFollowedByImage(node)) {
            visitor.ensureNewLine()
            return
        }
        visitor.ensureNewLine()
        visitor.forceNewLine()
    }

    private fun headingFollowedByImage(heading: Heading): Boolean {
        var next: Node? = heading.next
        while (next != null) {
            when (next) {
                is HtmlBlock -> {
                    val html = next.literal.orEmpty()
                    return html.contains("<img", ignoreCase = true)
                }
                is Paragraph -> {
                    if (paragraphContainsImage(next)) return true
                    if (paragraphIsEmpty(next)) {
                        next = next.next
                        continue
                    }
                    return false
                }
                else -> return false
            }
        }
        return false
    }

    private fun paragraphContainsImage(paragraph: Paragraph): Boolean {
        var child: Node? = paragraph.firstChild
        while (child != null) {
            if (child is Image) return true
            child = child.next
        }
        return false
    }

    private fun paragraphIsEmpty(paragraph: Paragraph): Boolean {
        val only = paragraph.firstChild ?: return true
        if (only.next != null) return false
        return only is Text && only.literal.isNullOrBlank()
    }
}
