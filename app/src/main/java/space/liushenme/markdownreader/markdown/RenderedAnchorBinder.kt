package space.liushenme.markdownreader.markdown

import android.widget.TextView
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.ui.screens.reader.lineAt
import space.liushenme.markdownreader.ui.screens.reader.markdownHeadingSpanStarts

/**
 * 将源码目录 slug 映射到 Markwon 渲染后 TextView 中的字符偏移（基于 [HeadingSpan] 顺序）。
 */
internal object RenderedAnchorBinder {

    fun bind(textView: TextView, sourceIndex: MarkdownAnchorIndex) {
        val offsets = buildRenderedOffsets(textView, sourceIndex)
        textView.setTag(R.id.markdown_anchor_rendered, offsets)
    }

    fun offsetForSlug(
        textView: TextView,
        slug: String,
        sourceIndex: MarkdownAnchorIndex,
    ): Int? {
        val rendered = textView.renderedAnchorOffsets()
        if (rendered != null) {
            val key = MarkdownAnchorIndex.slugify(slug)
            rendered[key]?.let { return it }
            rendered[slug]?.let { return it }
        }
        return buildRenderedOffsets(textView, sourceIndex)[MarkdownAnchorIndex.slugify(slug)]
            ?: buildRenderedOffsets(textView, sourceIndex)[slug]
    }

    private fun buildRenderedOffsets(
        textView: TextView,
        sourceIndex: MarkdownAnchorIndex,
    ): Map<String, Int> {
        val text = textView.text ?: return emptyMap()
        val headingStarts = markdownHeadingSpanStarts(text)
        if (headingStarts.isEmpty() || sourceIndex.entries.isEmpty()) return emptyMap()

        val out = linkedMapOf<String, Int>()
        val entries = sourceIndex.entries.sortedBy { it.sourceOffset }
        entries.forEachIndexed { index, entry ->
            val offset = when {
                index < headingStarts.size -> headingStarts[index]
                else -> findTitleLineOffset(text.toString(), entry.title) ?: -1
            }
            if (offset >= 0) {
                out[entry.slug] = offset
            }
        }
        return out
    }

    private fun findTitleLineOffset(full: String, title: String): Int? {
        val needle = title.trim()
        if (needle.isEmpty()) return null
        var from = 0
        while (from < full.length) {
            val idx = full.indexOf(needle, from)
            if (idx < 0) return null
            val line = lineAt(full, idx)
            if (line.contains(needle)) return idx
            from = idx + needle.length
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun TextView.renderedAnchorOffsets(): Map<String, Int>? =
        getTag(R.id.markdown_anchor_rendered) as? Map<String, Int>
}
