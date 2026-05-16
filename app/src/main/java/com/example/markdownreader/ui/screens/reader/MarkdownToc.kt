package com.example.markdownreader.ui.screens.reader

import com.example.markdownreader.importing.AtxMarkdownTocParser
import com.example.markdownreader.importing.PlainTextTocParser

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
 * 实现与导入侧 [AtxMarkdownTocParser] 一致。
 */
fun parseMarkdownToc(markdown: String): List<MarkdownTocEntry> =
    AtxMarkdownTocParser.parse(markdown).map { e ->
        MarkdownTocEntry(level = e.level, title = e.title, sourceOffset = e.sourceOffset)
    }

/**
 * 从纯文本（如 .txt 小说）按「独占一行」的常见章节样式解析目录。
 */
fun parsePlainTextToc(text: String): List<MarkdownTocEntry> =
    PlainTextTocParser.parse(text).map { e ->
        MarkdownTocEntry(level = e.level, title = e.title, sourceOffset = e.sourceOffset)
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
