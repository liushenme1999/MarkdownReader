package space.liushenme.markdownreader.importing

/**
 * 导入时补全目录（与阅读页解析规则一致），并写入解析包。
 */
object BookTocEnricher {

    fun enrichIfEmpty(format: ImportedBookFormat, extracted: ExtractedBookText): ExtractedBookText {
        if (extracted.toc.isNotEmpty()) return extracted
        val toc = when (format) {
            ImportedBookFormat.MARKDOWN -> AtxMarkdownTocParser.parse(extracted.body)
            ImportedBookFormat.TXT -> PlainTextTocParser.parse(extracted.body)
            else -> emptyList()
        }
        return if (toc.isEmpty()) extracted else extracted.copy(toc = toc)
    }
}
