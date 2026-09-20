package space.liushenme.markdownreader.ui.screens.reader

import space.liushenme.markdownreader.importing.AtxMarkdownTocParser
import space.liushenme.markdownreader.importing.PlainTextTocParser

/**
 * 目录项：对应 Markdown 源码中的一行 ATX 标题（`#` … `######`）。
 *
 * @param sourceOffset 标题行在整篇 Markdown 字符串中的起始字符下标（与阅读进度、书签的「源码坐标」一致，用于跳转滚动）。
 */
data class MarkdownTocEntry(
    val level: Int,
    /** 纯文本标题（无 HTML），用于跳转匹配与搜索。 */
    val title: String,
    val sourceOffset: Int,
    /** 标题行原文，用于目录/顶栏 inline HTML 样式。 */
    val rawTitle: String = title,
)

fun parseMarkdownToc(markdown: String): List<MarkdownTocEntry> =
    AtxMarkdownTocParser.parse(markdown).map { e ->
        MarkdownTocEntry(
            level = e.level,
            title = e.title,
            sourceOffset = e.sourceOffset,
            rawTitle = e.rawTitle,
        )
    }

/**
 * 从纯文本（如 .txt 小说）按「独占一行」的常见章节样式解析目录。
 */
fun parsePlainTextToc(text: String): List<MarkdownTocEntry> =
    PlainTextTocParser.parse(text).map { e ->
        MarkdownTocEntry(
            level = e.level,
            title = e.title,
            sourceOffset = e.sourceOffset,
            rawTitle = e.rawTitle,
        )
    }

fun currentChapterEntryForProgress(
    tocEntries: List<MarkdownTocEntry>,
    progress: Float,
    totalChars: Int,
): MarkdownTocEntry? {
    if (tocEntries.isEmpty() || totalChars <= 0) return null
    val pos = (progress * totalChars).toInt().coerceIn(0, totalChars)
    return currentChapterEntryForViewport(tocEntries, pos, pos)
}

/**
 * 小标题栏 / 目录高亮：优先用视口里正在显示的标题（最靠上的那条），
 * 没有可见标题时再退回视口顶之上最近的一节。
 *
 * 只按「视口顶字符」取上一节时，页面中部已经出现的新标题会对不上。
 */
fun currentChapterEntryForViewport(
    tocEntries: List<MarkdownTocEntry>,
    topChar: Int,
    bottomChar: Int = topChar,
): MarkdownTocEntry? {
    if (tocEntries.isEmpty()) return null
    val top = topChar.coerceAtLeast(0)
    val bottom = bottomChar.coerceAtLeast(top)
    tocEntries.firstOrNull { it.sourceOffset in top..bottom }?.let { return it }
    return tocEntries.lastOrNull { it.sourceOffset <= top }
}

/**
 * 用渲染层 [HeadingSpan] 起点对齐小标题栏：视口里露出来的标题优先于源码坐标估算。
 *
 * [headingStarts] 与窗口内目录项按出现顺序对应；对不上时再按标题文本匹配。
 */
fun currentChapterEntryFromHeadingStarts(
    tocEntries: List<MarkdownTocEntry>,
    headingStarts: List<Int>,
    viewportTop: Int,
    viewportBottom: Int,
    windowStart: Int,
    windowEnd: Int,
    displayedText: String,
): MarkdownTocEntry? {
    if (tocEntries.isEmpty() || headingStarts.isEmpty()) return null
    val top = viewportTop.coerceAtLeast(0)
    val bottom = viewportBottom.coerceAtLeast(top)
    val winStart = windowStart.coerceAtLeast(0)
    val winEnd = windowEnd.coerceAtLeast(winStart)
    val windowHeadings = tocEntries.filter { it.sourceOffset in winStart until winEnd }
        .ifEmpty { tocEntries }

    fun entryAt(index: Int): MarkdownTocEntry? {
        val start = headingStarts.getOrNull(index) ?: return null
        val visibleTitle = lineAt(displayedText, start)
        return matchTocByVisibleTitle(windowHeadings, visibleTitle)
            ?: matchTocByVisibleTitle(tocEntries, visibleTitle)
            ?: windowHeadings.getOrNull(index)
    }

    headingStarts.indexOfFirst { it in top..bottom }.takeIf { it >= 0 }?.let { return entryAt(it) }
    headingStarts.indexOfLast { it < top }.takeIf { it >= 0 }?.let { return entryAt(it) }
    return null
}

internal fun matchTocByVisibleTitle(
    entries: List<MarkdownTocEntry>,
    visibleTitle: String,
): MarkdownTocEntry? {
    val needle = normalizeVisibleHeadingTitle(visibleTitle)
    if (needle.isEmpty()) return null
    return entries.firstOrNull { normalizeVisibleHeadingTitle(it.title) == needle }
        ?: entries.firstOrNull { normalizeVisibleHeadingTitle(it.rawTitle) == needle }
}

private fun normalizeVisibleHeadingTitle(title: String): String =
    title.trim().replace(Regex("\\s+"), " ")

/**
 * 根据阅读进度（0～1）与全书字符数，取当前所处章节标题（最后一个 [sourceOffset] 不大于当前位置的目录项）。
 */
fun currentChapterTitleForProgress(
    tocEntries: List<MarkdownTocEntry>,
    progress: Float,
    totalChars: Int
): String? = currentChapterEntryForProgress(tocEntries, progress, totalChars)?.title
