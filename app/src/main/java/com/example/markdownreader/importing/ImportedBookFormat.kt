package com.example.markdownreader.importing

/**
 * 书架导入/阅读时识别的书籍格式（持久化为小写字符串写入 Room）。
 */
enum class ImportedBookFormat(val storedKey: String) {
    MARKDOWN("markdown"),
    TXT("txt"),
    PDF("pdf");

    val isPlainText: Boolean
        get() = this == MARKDOWN || this == TXT

    val hasBuiltInTextExtract: Boolean
        get() = isPlainText || this == PDF

    /** 阅读器是否用 TextView 纯文本渲染（非 Markwon）。 */
    val usesReaderPlainBody: Boolean
        get() = this == TXT

    val isPdf: Boolean
        get() = this == PDF

    companion object {
        /** 已移除支持的旧格式（数据库中可能仍存在）。 */
        private val REMOVED_STORED_KEYS = setOf(
            "epub", "docx", "doc", "mobi", "azw3", "azw", "prc"
        )

        fun isRemovedStoredKey(key: String?): Boolean {
            val k = key?.lowercase()?.trim().orEmpty()
            return k in REMOVED_STORED_KEYS
        }

        fun fromStored(key: String?): ImportedBookFormat {
            val k = key?.lowercase()?.trim().orEmpty()
            return entries.find { it.storedKey == k } ?: MARKDOWN
        }

        fun fromFileName(name: String?): ImportedBookFormat {
            val ext = name?.substringAfterLast('.', "")?.lowercase()?.trim().orEmpty()
            return when (ext) {
                "md", "markdown", "mdown", "mkd" -> MARKDOWN
                "txt", "text", "log" -> TXT
                "pdf" -> PDF
                else -> TXT
            }
        }

        fun fromMimeType(mime: String?): ImportedBookFormat {
            val m = mime?.lowercase()?.trim().orEmpty()
            return when {
                m.contains("pdf") -> PDF
                m.contains("markdown") || m.contains("x-markdown") -> MARKDOWN
                m.contains("plain") -> TXT
                else -> TXT
            }
        }
    }
}
