package com.example.markdownreader.importing

/**
 * 书架导入/阅读时识别的书籍格式（持久化为小写字符串写入 Room）。
 */
enum class ImportedBookFormat(val storedKey: String) {
    MARKDOWN("markdown"),
    TXT("txt"),
    EPUB("epub"),
    DOCX("docx"),
    PDF("pdf"),
    DOC("doc"),
    MOBI("mobi"),
    AZW3("azw3");

    val isPlainText: Boolean
        get() = this == MARKDOWN || this == TXT

    val hasBuiltInTextExtract: Boolean
        get() = isPlainText || this == EPUB || this == DOCX || this == MOBI || this == AZW3 || this == PDF

    /**
     * 阅读器是否用 TextView 纯文本渲染（非 Markwon）。
     *
     * 仅 TXT 用纯文本：EPUB/MOBI/AZW3/DOCX 在导入侧已经被 HtmlToMarkdownConverter 转成
     * Markdown，统一走 Markwon 渲染以保留标题/列表/表格/强调/图片/公式等结构。
     */
    val usesReaderPlainBody: Boolean
        get() = this == TXT

    companion object {
        fun fromStored(key: String?): ImportedBookFormat {
            val k = key?.lowercase()?.trim().orEmpty()
            return entries.find { it.storedKey == k } ?: MARKDOWN
        }

        fun fromFileName(name: String?): ImportedBookFormat {
            val ext = name?.substringAfterLast('.', "")?.lowercase()?.trim().orEmpty()
            return when (ext) {
                "md", "markdown", "mdown", "mkd" -> MARKDOWN
                "txt", "text", "log" -> TXT
                "epub" -> EPUB
                "docx" -> DOCX
                "pdf" -> PDF
                "doc" -> DOC
                "mobi", "prc" -> MOBI
                "azw3", "azw" -> AZW3
                else -> TXT
            }
        }

        fun fromMimeType(mime: String?): ImportedBookFormat {
            val m = mime?.lowercase()?.trim().orEmpty()
            return when {
                m.contains("epub") -> EPUB
                m.contains("pdf") -> PDF
                m.contains("wordprocessingml") || m.contains("officedocument.wordprocessingml") -> DOCX
                m.contains("msword") && !m.contains("openxml") -> DOC
                m.contains("markdown") || m.contains("x-markdown") -> MARKDOWN
                m.contains("plain") -> TXT
                m.contains("mobi") -> MOBI
                m.contains("azw") -> AZW3
                else -> TXT
            }
        }
    }
}
