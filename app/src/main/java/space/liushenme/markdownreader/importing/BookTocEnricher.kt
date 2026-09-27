package space.liushenme.markdownreader.importing

/**
 * 导入时补全目录（与阅读页解析规则一致），并写入解析包。
 *
 * 注意：[sourceOffset] 必须相对**最终落盘/阅读用的正文**。
 * [MarkdownPreprocessor.prepare]、[ParsedBookStorage.materializeAssetUrls] 等都会改字符下标，
 * 若在变换前解析目录，跳转会落在错误位置。
 */
object BookTocEnricher {

    fun enrichIfEmpty(format: ImportedBookFormat, extracted: ExtractedBookText): ExtractedBookText {
        if (extracted.toc.isNotEmpty()) return extracted
        return alignToBody(format, extracted)
    }

    /**
     * 按当前 [ExtractedBookText.body] 重新解析目录，丢弃可能已失效的旧偏移。
     * PDF 页图正文保留原 TOC（页码锚点，不是 ATX）。
     */
    fun alignToBody(format: ImportedBookFormat, extracted: ExtractedBookText): ExtractedBookText {
        if (extracted.body.isEmpty()) return extracted.copy(toc = emptyList())
        if (format.isPdf || PdfReaderContent.looksLikePdfBody(extracted.body)) {
            return extracted
        }
        val toc = when (format) {
            ImportedBookFormat.MARKDOWN -> AtxMarkdownTocParser.parse(extracted.body)
            ImportedBookFormat.TXT -> PlainTextTocParser.parse(extracted.body)
            ImportedBookFormat.PDF -> extracted.toc
        }
        return extracted.copy(toc = toc)
    }

    /**
     * 读盘后正文可能已 materialize / strip，按最终正文对齐目录。
     * 无 [ImportedBookFormat] 时：优先 ATX，否则纯文本；PDF 页图保留 [stored]。
     */
    fun alignToBody(body: String, stored: List<ImportedTocEntry>): List<ImportedTocEntry> {
        if (body.isEmpty()) return emptyList()
        if (PdfReaderContent.looksLikePdfBody(body)) return stored
        val fromMd = AtxMarkdownTocParser.parse(body)
        if (fromMd.isNotEmpty()) return fromMd
        val fromPlain = PlainTextTocParser.parse(body)
        if (fromPlain.isNotEmpty()) return fromPlain
        return stored
    }
}
