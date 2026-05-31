package space.liushenme.markdownreader.importing

/**
 * 从纯文本（如 .txt 小说）按常见章节标题样式解析目录。
 * 阅读页与导入侧共用，保证 [sourceOffset] 规则一致。
 */
object PlainTextTocParser {

    private val CN_NUM = "0-9０-９〇一二三四五六七八九十百千万亿两"
    private val RE_DI_ZHANG = Regex("^第[$CN_NUM]+章.*$")
    private val RE_DI_JIE = Regex("^第[$CN_NUM]+节.*$")
    private val RE_DI_HUI = Regex("^第[$CN_NUM]+回.*$")
    private val RE_DI_JUAN = Regex("^第[$CN_NUM]+卷.*$")
    private val RE_SPECIAL_HEAD = Regex(
        "^(楔子|序章|序言|引子|前言|后记|尾声|跋|番外篇?|[上下中]卷).*$"
    )
    private val RE_CHAPTER_EN = Regex("(?i)^Chapter\\s+[0-9IVXLC]+\\b.*$")
    private val RE_PART_EN = Regex("(?i)^Part\\s+[0-9IVXLC]+\\b.*$")

    fun parse(text: String): List<ImportedTocEntry> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<ImportedTocEntry>()
        var offset = 0
        while (offset <= text.length) {
            val lineStart = offset
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, lineEnd)
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                val level = levelForLine(trimmed)
                if (level != null) {
                    val titleStart = lineStart + line.indexOf(trimmed)
                    out += ImportedTocEntry(
                        level = level,
                        title = trimmed,
                        sourceOffset = titleStart.coerceIn(lineStart, lineEnd)
                    )
                }
            }
            if (lineEnd >= text.length) break
            offset = lineEnd + 1
        }
        return out
    }

    internal fun levelForLine(trimmedLine: String): Int? {
        if (RE_DI_JUAN.matches(trimmedLine)) return 1
        if (RE_SPECIAL_HEAD.matches(trimmedLine)) return 1
        if (RE_DI_ZHANG.matches(trimmedLine)) return 1
        if (RE_DI_HUI.matches(trimmedLine)) return 1
        if (RE_DI_JIE.matches(trimmedLine)) return 1
        if (RE_CHAPTER_EN.matches(trimmedLine)) return 1
        if (RE_PART_EN.matches(trimmedLine)) return 1
        return null
    }
}
