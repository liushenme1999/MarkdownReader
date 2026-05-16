package com.example.markdownreader.ui.screens.reader

/**
 * 目录项：对应 Markdown 源码中的一行 ATX 标题（`#` … `######`）。
 *
 * @param sourceOffset 标题行在整篇 Markdown 字符串中的起始字符下标（与阅读进度、书签的「源码坐标」一致，用于跳转滚动）。
 */
data class MarkdownTocEntry(
    val level: Int,
    val title: String,
    val sourceOffset: Int
)

/**
 * 从 Markdown 源码解析目录（仅 ATX 风格 `#` … `######`）。
 * 实现与导入侧 [com.example.markdownreader.importing.AtxMarkdownTocParser] 一致。
 */
fun parseMarkdownToc(markdown: String): List<MarkdownTocEntry> =
    com.example.markdownreader.importing.AtxMarkdownTocParser.parse(markdown).map { e ->
        MarkdownTocEntry(level = e.level, title = e.title, sourceOffset = e.sourceOffset)
    }

/**
 * 从纯文本（如 .txt 小说）按「独占一行」的常见章节样式解析目录。
 * 与 [parseMarkdownToc] 共用 [MarkdownTocEntry]：level 固定为 1；[sourceOffset] 为该行在全文中的起始下标。
 */
fun parsePlainTextToc(text: String): List<MarkdownTocEntry> {
    if (text.isEmpty()) return emptyList()
    val lines = text.split('\n')
    val out = mutableListOf<MarkdownTocEntry>()
    var offset = 0
    lines.forEachIndexed { index, line ->
        val lineStart = offset
        val trimmed = line.trim()
        if (trimmed.isNotEmpty()) {
            val level = plainTextTocLevel(trimmed)
            if (level != null) {
                out += MarkdownTocEntry(
                    level = level,
                    title = trimmed,
                    sourceOffset = lineStart
                )
            }
        }
        offset += line.length
        if (index < lines.lastIndex) offset += 1
    }
    return out
}

private val CN_NUM = "0-9０-９〇一二三四五六七八九十百千万亿两"

/** 第…章 / 节 / 回 / 卷（含阿拉伯数字与中文数字） */
private val RE_DI_ZHANG = Regex("^第[$CN_NUM]+章.*$")
private val RE_DI_JIE = Regex("^第[$CN_NUM]+节.*$")
private val RE_DI_HUI = Regex("^第[$CN_NUM]+回.*$")
private val RE_DI_JUAN = Regex("^第[$CN_NUM]+卷.*$")

private val RE_SPECIAL_HEAD = Regex(
    "^(楔子|序章|序言|引子|前言|后记|尾声|跋|番外篇?|[上下中]卷).*$"
)

private val RE_CHAPTER_EN = Regex("(?i)^Chapter\\s+[0-9IVXLC]+\\b.*$")
private val RE_PART_EN = Regex("(?i)^Part\\s+[0-9IVXLC]+\\b.*$")

private fun plainTextTocLevel(trimmedLine: String): Int? {
    if (RE_DI_JUAN.matches(trimmedLine)) return 1
    if (RE_SPECIAL_HEAD.matches(trimmedLine)) return 1
    if (RE_DI_ZHANG.matches(trimmedLine)) return 1
    if (RE_DI_HUI.matches(trimmedLine)) return 1
    if (RE_DI_JIE.matches(trimmedLine)) return 1
    if (RE_CHAPTER_EN.matches(trimmedLine)) return 1
    if (RE_PART_EN.matches(trimmedLine)) return 1
    return null
}

/**
 * 根据阅读进度（0～1）与全书字符数，取当前所处章节标题（最后一个 [sourceOffset] 不大于当前位置的目录项）。
 */
fun currentChapterTitleForProgress(
    tocEntries: List<MarkdownTocEntry>,
    progress: Float,
    totalChars: Int
): String? {
    if (tocEntries.isEmpty() || totalChars <= 0) return null
    val pos = (progress * totalChars).toInt().coerceIn(0, totalChars)
    return tocEntries.lastOrNull { it.sourceOffset <= pos }?.title
}
