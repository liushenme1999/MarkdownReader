package space.liushenme.markdownreader.markdown

import space.liushenme.markdownreader.importing.AtxMarkdownTocParser

/** 文档内标题锚点（用于 `#slug` 链接跳转）。 */
data class MarkdownAnchorIndex(
    val entries: List<Entry> = emptyList(),
) {
    data class Entry(
        val slug: String,
        val title: String,
        val sourceOffset: Int,
    )

    companion object {
        fun build(markdown: String): MarkdownAnchorIndex {
            val entries = AtxMarkdownTocParser.parse(markdown).map { h ->
                Entry(
                    slug = slugify(h.title),
                    title = h.title.trim(),
                    sourceOffset = h.sourceOffset,
                )
            }
            return MarkdownAnchorIndex(entries)
        }

        /** 与常见 GFM / 飞书等编辑器一致：去标点、去空白。 */
        fun slugify(title: String): String {
            return title
                .trim()
                .lowercase()
                .replace(Regex("\\s+"), "")
                .replace(
                    Regex("""[、，,。.!！?？:：'\"“”‘’（）()\[\]【】《》<>#\-—_·]"""),
                    "",
                )
        }
    }
}
