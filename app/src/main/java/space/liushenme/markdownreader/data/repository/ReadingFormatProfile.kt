package space.liushenme.markdownreader.data.repository

import space.liushenme.markdownreader.importing.ImportedBookFormat

/**
 * 阅读样式的「格式维度」：同一套阅读设置按文件格式分开保存与加载。
 *
 * 分档依据是阅读体验差异：
 * - Markdown / 源码 / 网页：正文排版与代码样式差异大，各自独立；
 * - 纯文本：只有基础排版；
 * - PDF：页图阅读，只关心底色与翻页方式。
 */
enum class ReadingFormatProfile(val storageKey: String) {
    MARKDOWN("markdown"),
    PLAIN_TEXT("txt"),
    HTML("html"),
    PDF("pdf"),
    CODE("code"),
    ;

    companion object {
        fun fromStorageKey(raw: String?): ReadingFormatProfile? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }

        /** 书籍导入格式 → 阅读样式档位。未知格式按纯文本处理（与阅读器渲染一致）。 */
        fun fromBookFormat(format: ImportedBookFormat): ReadingFormatProfile = when (format) {
            ImportedBookFormat.MARKDOWN -> MARKDOWN
            ImportedBookFormat.TXT -> PLAIN_TEXT
            ImportedBookFormat.HTML -> HTML
            ImportedBookFormat.PDF -> PDF
            ImportedBookFormat.CODE -> CODE
        }

        /** 阅读样式档位 → 书籍导入格式（设置界面展示与实际书籍匹配用）。 */
        fun toBookFormat(profile: ReadingFormatProfile): ImportedBookFormat = when (profile) {
            MARKDOWN -> ImportedBookFormat.MARKDOWN
            PLAIN_TEXT -> ImportedBookFormat.TXT
            HTML -> ImportedBookFormat.HTML
            PDF -> ImportedBookFormat.PDF
            CODE -> ImportedBookFormat.CODE
        }
    }

    /** 设置界面顶部 Tab 的展示文案资源。 */
    val labelRes: Int
        get() = when (this) {
            MARKDOWN -> space.liushenme.markdownreader.R.string.book_format_markdown
            PLAIN_TEXT -> space.liushenme.markdownreader.R.string.book_format_text
            HTML -> space.liushenme.markdownreader.R.string.book_format_html
            PDF -> space.liushenme.markdownreader.R.string.book_format_pdf
            CODE -> space.liushenme.markdownreader.R.string.book_format_code
        }

    /** 该格式是否使用代码样式设置（高亮主题 / 自动换行 / 行号）。 */
    val usesCodeStyle: Boolean
        get() = this == MARKDOWN || this == CODE || this == HTML

    /** 该格式是否使用字体与排版设置（字号 / 页边距 / 行距）。 */
    val usesTypography: Boolean
        get() = this != PDF
}
