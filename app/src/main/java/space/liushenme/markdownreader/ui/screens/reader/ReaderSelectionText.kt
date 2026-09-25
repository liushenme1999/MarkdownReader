package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
import android.text.GetChars
import android.text.Spannable
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.ReplacementSpan
import io.noties.markwon.ext.latex.ReaderCompoundInlineLatexSpan
import io.noties.markwon.image.AsyncDrawableSpan
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.markdown.DiagramSchemeHandler
import space.liushenme.markdownreader.markdown.ReaderScrollableCodeBlockSpan

/**
 * 从阅读正文选区提取可复制 / 可划线的纯文本。
 * 行内公式、图片等 [ReplacementSpan] 底层常为 `\uFFFC`，直接 [CharSequence.subSequence]
 * 会得到「空」内容，表现为复制空白、划线无响应。
 */
internal fun extractReaderSelectionText(
    body: CharSequence,
    start: Int,
    end: Int,
    context: Context,
): String {
    if (start >= end) return ""
    val from = start.coerceAtLeast(0)
    val to = end.coerceAtMost(body.length)
    if (from >= to) return ""
    if (body !is Spanned) {
        return body.subSequence(from, to).toString().replace("\uFFFC", "")
    }
    val sb = StringBuilder(to - from)
    var index = from
    while (index < to) {
        val spanEnd = resolvedReplacementEnd(body, index, to)
        if (spanEnd != null) {
            val replacement = replacementPlainText(body, index, spanEnd, context)
            if (replacement != null) {
                sb.append(replacement)
                index = spanEnd
                continue
            }
        }
        val ch = body[index]
        if (ch != '\uFFFC') {
            sb.append(ch)
        }
        index++
    }
    return sb.toString()
}

/** 选区是否具备可操作的文本（排除纯空白 / 纯对象替换符）。 */
internal fun readerSelectionHasActionableText(
    body: CharSequence,
    start: Int,
    end: Int,
    context: Context,
): Boolean =
    extractReaderSelectionText(body, start, end, context).isNotBlank()

private fun resolvedReplacementEnd(body: Spanned, index: Int, limit: Int): Int? {
    val replacements = body.getSpans(index, index + 1, ReplacementSpan::class.java)
    var bestEnd = -1
    for (span in replacements) {
        val spanStart = body.getSpanStart(span)
        val spanEnd = body.getSpanEnd(span)
        if (spanStart != index) continue
        if (spanEnd <= index || spanEnd > limit) continue
        if (spanEnd > bestEnd) bestEnd = spanEnd
    }
    return bestEnd.takeIf { it > index }
}

private fun replacementPlainText(
    body: Spanned,
    start: Int,
    end: Int,
    context: Context,
): String? {
    body.getSpans(start, end, ReaderScrollableCodeBlockSpan::class.java)
        .firstOrNull { body.getSpanStart(it) == start && body.getSpanEnd(it) == end }
        ?.let { span ->
            return if (span.hasSelection()) span.selectedText() else span.rawCode
        }

    body.getSpans(start, end, ReaderCompoundInlineLatexSpan::class.java)
        .firstOrNull { body.getSpanStart(it) == start && body.getSpanEnd(it) == end }
        ?.sourceLatex
        ?.let { latex -> return formatInlineLatexSelectionText(latex) }

    val diagramPlaceholder = context.getString(R.string.selection_placeholder_diagram)
    val imagePlaceholder = context.getString(R.string.selection_placeholder_image)

    body.getSpans(start, end, AsyncDrawableSpan::class.java)
        .firstOrNull { body.getSpanStart(it) == start && body.getSpanEnd(it) == end }
        ?.let { span ->
            val dest = span.drawable.destination
            return when {
                dest.startsWith("${DiagramSchemeHandler.SCHEME}://") -> diagramPlaceholder
                dest.isNotBlank() && !dest.all { it == '\uFFFC' } &&
                    !dest.startsWith("http", ignoreCase = true) &&
                    !dest.startsWith("file", ignoreCase = true) &&
                    !dest.startsWith("content", ignoreCase = true) ->
                    formatInlineLatexSelectionText(dest)
                else -> imagePlaceholder
            }
        }

    body.getSpans(start, end, ImageSpan::class.java)
        .firstOrNull { body.getSpanStart(it) == start && body.getSpanEnd(it) == end }
        ?.let { return imagePlaceholder }

    val raw = body.subSequence(start, end).toString()
    if (raw.isNotEmpty() && raw.all { it == '\uFFFC' }) return null
    return raw.takeIf { it.isNotEmpty() }
}

/**
 * 系统翻译 / PROCESS_TEXT 会 `getText().subSequence(selStart, selEnd)`。
 * 代码块选区在 TextView 上对应整段 ReplacementSpan（常为 `\uFFFC`），这里改导出内部选中源码。
 */
internal class ProcessTextExportSpannable(
    val delegate: Spannable,
) : Spannable by delegate, GetChars {
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return exportedSelectionOrNull(startIndex, endIndex)
            ?: delegate.subSequence(startIndex, endIndex)
    }

    override fun getChars(start: Int, end: Int, dest: CharArray, destoff: Int) {
        val exported = exportedSelectionOrNull(start, end)
        if (exported != null && exported.length == end - start) {
            exported.toString().toCharArray(dest, destoff)
            return
        }
        when (val body = delegate) {
            is GetChars -> body.getChars(start, end, dest, destoff)
            else -> body.subSequence(start, end).toString().toCharArray(dest, destoff)
        }
    }

    private fun exportedSelectionOrNull(startIndex: Int, endIndex: Int): CharSequence? {
        val start = minOf(startIndex, endIndex)
        val end = maxOf(startIndex, endIndex)
        if (start >= end) return null
        return delegate.getSpans(start, end, ReaderScrollableCodeBlockSpan::class.java)
            .firstOrNull { span ->
                span.hasSelection() &&
                    delegate.getSpanStart(span) == start &&
                    delegate.getSpanEnd(span) == end
            }?.selectedText()
    }
}

internal fun formatInlineLatexSelectionText(latex: String): String {
    val body = latex.replace('\n', ' ').trim()
    if (body.isEmpty()) return ""
    if (body.length >= 2 && body.startsWith('$') && body.endsWith('$')) return body
    return "\$${body}\$"
}
