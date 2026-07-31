package space.liushenme.markdownreader.ui.screens.reader

import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.ReplacementSpan
import io.noties.markwon.ext.latex.ReaderCompoundInlineLatexSpan
import io.noties.markwon.image.AsyncDrawableSpan
import space.liushenme.markdownreader.markdown.DiagramSchemeHandler

/**
 * 从阅读正文选区提取可复制 / 可划线的纯文本。
 * 行内公式、图片等 [ReplacementSpan] 底层常为 `\uFFFC`，直接 [CharSequence.subSequence]
 * 会得到「空」内容，表现为复制空白、划线无响应。
 */
internal fun extractReaderSelectionText(body: CharSequence, start: Int, end: Int): String {
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
            val replacement = replacementPlainText(body, index, spanEnd)
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
internal fun readerSelectionHasActionableText(body: CharSequence, start: Int, end: Int): Boolean =
    extractReaderSelectionText(body, start, end).isNotBlank()

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

private fun replacementPlainText(body: Spanned, start: Int, end: Int): String? {
    body.getSpans(start, end, ReaderCompoundInlineLatexSpan::class.java)
        .firstOrNull { body.getSpanStart(it) == start && body.getSpanEnd(it) == end }
        ?.sourceLatex
        ?.let { latex -> return formatInlineLatexSelectionText(latex) }

    body.getSpans(start, end, AsyncDrawableSpan::class.java)
        .firstOrNull { body.getSpanStart(it) == start && body.getSpanEnd(it) == end }
        ?.let { span ->
            val dest = span.drawable.destination
            return when {
                dest.startsWith("${DiagramSchemeHandler.SCHEME}://") -> "[图表]"
                dest.isNotBlank() && !dest.all { it == '\uFFFC' } &&
                    !dest.startsWith("http", ignoreCase = true) &&
                    !dest.startsWith("file", ignoreCase = true) &&
                    !dest.startsWith("content", ignoreCase = true) ->
                    formatInlineLatexSelectionText(dest)
                else -> "[图片]"
            }
        }

    body.getSpans(start, end, ImageSpan::class.java)
        .firstOrNull { body.getSpanStart(it) == start && body.getSpanEnd(it) == end }
        ?.let { return "[图片]" }

    val raw = body.subSequence(start, end).toString()
    if (raw.isNotEmpty() && raw.all { it == '\uFFFC' }) return null
    return raw.takeIf { it.isNotEmpty() }
}

internal fun formatInlineLatexSelectionText(latex: String): String {
    val body = latex.replace('\n', ' ').trim()
    if (body.isEmpty()) return ""
    if (body.length >= 2 && body.startsWith('$') && body.endsWith('$')) return body
    return "\$${body}\$"
}
