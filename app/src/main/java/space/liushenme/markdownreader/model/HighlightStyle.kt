package space.liushenme.markdownreader.model

/**
 * 阅读划线样式。
 * - [Background]：仅背景色
 * - [Underline]：下方直线
 * - [Wavy]：下方波浪线
 */
enum class HighlightStyle(val storageKey: String) {
    Background("background"),
    Underline("underline"),
    Wavy("wavy");

    companion object {
        val DEFAULT = Background

        fun fromStorageKey(raw: String?): HighlightStyle =
            entries.find { it.storageKey == raw } ?: DEFAULT
    }
}
