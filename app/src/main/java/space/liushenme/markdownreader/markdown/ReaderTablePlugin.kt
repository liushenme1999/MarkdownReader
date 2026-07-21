package space.liushenme.markdownreader.markdown

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.SpannableBuilder
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.ext.tables.TableSpan
import io.noties.markwon.ext.tables.TableTheme
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.Node
import org.commonmark.parser.Parser

/**
 * 表格渲染基于 Markwon [TablePlugin]（保留其 TextView 生命周期），
 * 行 span 改用 [ReaderTableRowSpan] + [ReaderTableRowLineHeightSpan]：
 * - 行间 `\n` 覆盖 [ReaderTableRowBreakSpan]，避免 MIUI 满宽挤出的空行占高度
 * - 抵消行距倍数；给表头/偶数行补浅底
 */
internal object ReaderTablePlugin {

    fun create(context: Context): AbstractMarkwonPlugin {
        val theme = TableTheme.create(context)
        return ReaderTablePluginImpl(TablePlugin.create(theme), theme)
    }

    private class ReaderTablePluginImpl(
        private val delegate: TablePlugin,
        private val theme: TableTheme,
    ) : AbstractMarkwonPlugin() {

        private val visitor = TableVisitor(theme)

        override fun configureParser(builder: Parser.Builder) {
            delegate.configureParser(builder)
        }

        override fun configureVisitor(builder: MarkwonVisitor.Builder) {
            visitor.configure(builder)
        }

        override fun beforeRender(node: Node) {
            visitor.clear()
        }

        override fun beforeSetText(textView: TextView, markdown: Spanned) {
            delegate.beforeSetText(textView, markdown)
        }

        override fun afterSetText(textView: TextView) {
            delegate.afterSetText(textView)
            ReaderTableDebug.scheduleDump(textView, reason = "afterSetText")
        }
    }

    private class TableVisitor(
        private val tableTheme: TableTheme,
    ) {
        private var pendingTableRow: MutableList<TableRowSpan.Cell>? = null
        private var tableRowIsHeader = false
        private var tableRows = 0
        private var lastWasTableRow = false

        fun clear() {
            pendingTableRow = null
            tableRowIsHeader = false
            tableRows = 0
            lastWasTableRow = false
        }

        fun configure(builder: MarkwonVisitor.Builder) {
            builder
                .on(TableBlock::class.java) { visitor, tableBlock ->
                    lastWasTableRow = false
                    visitor.blockStart(tableBlock)
                    val length = visitor.length()
                    visitor.visitChildren(tableBlock)
                    visitor.setSpans(length, TableSpan())
                    visitor.blockEnd(tableBlock)
                    lastWasTableRow = false
                }
                .on(TableBody::class.java) { visitor, tableBody ->
                    visitor.visitChildren(tableBody)
                    tableRows = 0
                }
                .on(TableRow::class.java) { visitor, tableRow ->
                    visitRow(visitor, tableRow)
                }
                .on(TableHead::class.java) { visitor, tableHead ->
                    visitRow(visitor, tableHead)
                }
                .on(TableCell::class.java) { visitor, tableCell ->
                    val length = visitor.length()
                    visitor.visitChildren(tableCell)
                    if (pendingTableRow == null) {
                        pendingTableRow = ArrayList(2)
                    }
                    pendingTableRow!!.add(
                        TableRowSpan.Cell(
                            tableCellAlignment(tableCell.alignment),
                            visitor.builder().removeFromEnd(length),
                        ),
                    )
                    tableRowIsHeader = tableCell.isHeader
                }
        }

        private fun visitRow(visitor: MarkwonVisitor, node: Node) {
            visitor.visitChildren(node)
            val row = pendingTableRow ?: return
            val builder: SpannableBuilder = visitor.builder()
            if (lastWasTableRow) {
                // 行间换行必须保留（让下一行 NBSP 另起一行），但用零高度 span 吃掉 MIUI 挤出的空行。
                val breakStart = visitor.length()
                builder.append('\n')
                visitor.setSpans(breakStart, ReaderTableRowBreakSpan())
            } else if (builder.length > 0 && builder.lastChar() != '\n') {
                visitor.forceNewLine()
            }
            val spanStart = visitor.length()
            builder.append('\u00a0')
            val span = ReaderTableRowSpan(
                theme = tableTheme,
                cells = row,
                rowHeader = tableRowIsHeader,
                rowOdd = tableRows % 2 == 1,
            )
            tableRows = if (tableRowIsHeader) 0 else tableRows + 1
            visitor.setSpans(spanStart, span)
            visitor.setSpans(spanStart, ReaderTableRowLineHeightSpan())
            pendingTableRow = null
            lastWasTableRow = true
        }

        private fun tableCellAlignment(alignment: TableCell.Alignment?): Int {
            return when (alignment) {
                TableCell.Alignment.CENTER -> TableRowSpan.ALIGN_CENTER
                TableCell.Alignment.RIGHT -> TableRowSpan.ALIGN_RIGHT
                else -> TableRowSpan.ALIGN_LEFT
            }
        }
    }
}
