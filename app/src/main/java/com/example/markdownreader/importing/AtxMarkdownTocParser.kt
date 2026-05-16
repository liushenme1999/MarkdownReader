package com.example.markdownreader.importing

/**
 * 从 Markdown 源码解析 ATX 标题目录（`#` … `######`）。
 * 与阅读页 [com.example.markdownreader.ui.screens.reader.parseMarkdownToc] 共用，
 * 保证 EPUB/MOBI 导入与阅读跳转使用同一套 [sourceOffset] 规则。
 */
object AtxMarkdownTocParser {

    private val ATX_HEADING = Regex(
        "^\\s{0,3}(#{1,6})\\s+(.+?)(?:\\s+#+)?\\s*$"
    )

    fun parse(markdown: String): List<ImportedTocEntry> {
        if (markdown.isEmpty()) return emptyList()
        val lines = markdown.split('\n')
        val out = mutableListOf<ImportedTocEntry>()
        var offset = 0
        var inFence = false
        lines.forEachIndexed { index, line ->
            val lineStart = offset
            val trimmedStart = line.trimStart()
            if (trimmedStart.startsWith("```")) {
                inFence = !inFence
            } else if (!inFence) {
                val m = ATX_HEADING.matchEntire(line)
                if (m != null) {
                    val hashes = m.groupValues[1]
                    val rawTitle = m.groupValues[2].trim()
                    if (rawTitle.isNotEmpty()) {
                        out += ImportedTocEntry(
                            level = hashes.length,
                            title = rawTitle,
                            sourceOffset = lineStart
                        )
                    }
                }
            }
            offset += line.length
            if (index < lines.lastIndex) offset += 1
        }
        return out
    }
}
