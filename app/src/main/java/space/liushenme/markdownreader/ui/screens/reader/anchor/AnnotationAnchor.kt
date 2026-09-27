package space.liushenme.markdownreader.ui.screens.reader.anchor

import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.ui.screens.reader.MarkdownTocEntry
import space.liushenme.markdownreader.ui.screens.reader.closestSnippetIndexNear
import space.liushenme.markdownreader.ui.screens.reader.locateLooseSnippetNear
import kotlin.math.abs

/** 保存划线或书签时从源码上截下的锚点。 */
internal data class CapturedTextAnchor(
    val quotePrefix: String,
    val quoteSuffix: String,
    val blockIndex: Int?,
    val blockOffsetStart: Int?,
    val blockOffsetEnd: Int?,
    val endBlockIndex: Int?,
    val endBlockHash: String?,
    val blockHash: String?,
    val docHash: String,
    val headingPath: String,
)

/** 解析时用的锚点。旧数据的上下文字段为 null。 */
internal data class AnchorQuery(
    val exact: String,
    val docStart: Int,
    val docEnd: Int,
    val quotePrefix: String?,
    val quoteSuffix: String?,
    val blockIndex: Int?,
    val blockOffsetStart: Int?,
    val blockOffsetEnd: Int?,
    val endBlockIndex: Int?,
    val endBlockHash: String?,
    val blockHash: String?,
    val docHash: String?,
    val headingPath: String?,
    val point: Boolean,
)

/** 源码上的定位结果。只有 [Unique] 可以上色或自动跳转。 */
internal sealed class AnchorResolveResult {
    abstract val score: Double

    data class Unique(
        val start: Int,
        val end: Int,
        override val score: Double,
    ) : AnchorResolveResult()

    /** 有候选，但分数不够或前两名太接近。点击列表时跳到 [start]，正文不上色。 */
    data class Ambiguous(
        val start: Int,
        val end: Int,
        override val score: Double,
    ) : AnchorResolveResult()

    data object Unresolved : AnchorResolveResult() {
        override val score: Double = 0.0
    }
}

internal val AnchorResolveResult.canPaint: Boolean
    get() = this is AnchorResolveResult.Unique

/** 列表点击用的源码位置。没有候选时返回 null，调用方改用保存的提示位置。 */
internal val AnchorResolveResult.jumpStart: Int?
    get() = when (this) {
        is AnchorResolveResult.Unique -> start
        is AnchorResolveResult.Ambiguous -> start
        AnchorResolveResult.Unresolved -> null
    }

internal fun captureTextAnchor(
    document: String,
    plainText: Boolean,
    start: Int,
    end: Int,
    toc: List<MarkdownTocEntry>,
    docHash: String,
): CapturedTextAnchor {
    val safeStart = start.coerceIn(0, document.length)
    val safeEnd = end.coerceIn(safeStart, document.length)
    val prefixFrom = (safeStart - TextBlockIndex.QUOTE_CONTEXT_CHARS).coerceAtLeast(0)
    val suffixTo = (safeEnd + TextBlockIndex.QUOTE_CONTEXT_CHARS).coerceAtMost(document.length)
    val blocks = TextBlockIndex.build(document, plainText)
    val startBlock = blocks.blockCovering(safeStart)
    val endProbe = if (safeEnd > safeStart) safeEnd - 1 else safeStart
    val endBlock = blocks.blockCovering(endProbe)
    val crossBlock = startBlock != null && endBlock != null && startBlock.index != endBlock.index
    return CapturedTextAnchor(
        quotePrefix = document.substring(prefixFrom, safeStart),
        quoteSuffix = document.substring(safeEnd, suffixTo),
        blockIndex = startBlock?.index,
        blockOffsetStart = startBlock?.let { safeStart - it.start },
        blockOffsetEnd = when {
            startBlock == null -> null
            crossBlock -> safeEnd - endBlock.start
            else -> safeEnd - startBlock.start
        },
        endBlockIndex = if (crossBlock) endBlock.index else null,
        endBlockHash = if (crossBlock) endBlock.hash else null,
        blockHash = startBlock?.hash,
        docHash = docHash,
        headingPath = TextBlockIndex.headingPath(toc, safeStart),
    )
}

/**
 * 把阅读器里选中的文字对回源码半开区间。
 * 先认连续切片；对不上时跳过加粗、链接、换行这些渲染时会被吃掉的标记。
 * 提示位置落在某段匹配里时优先用那段，避免旁边另一处纯文本把加粗原文抢走。
 */
internal fun sourceSpanForRenderedQuote(
    source: String,
    rendered: String,
    hint: Int,
    searchStart: Int = 0,
    searchEnd: Int = source.length,
): Pair<Int, Int>? {
    if (rendered.isEmpty() || source.isEmpty()) return null
    val boundedStart = searchStart.coerceIn(0, source.length)
    val boundedEnd = searchEnd.coerceIn(boundedStart, source.length)
    if (boundedEnd <= boundedStart) return null
    val safeHint = hint.coerceIn(boundedStart, boundedEnd)
    if (safeHint + rendered.length <= source.length &&
        safeHint + rendered.length <= boundedEnd &&
        source.regionMatches(safeHint, rendered, 0, rendered.length)
    ) {
        return safeHint to (safeHint + rendered.length)
    }
    val radius = maxOf(rendered.length * 24, 4_000)
    val from = (safeHint - radius).coerceAtLeast(boundedStart)
    val to = (safeHint + radius).coerceAtMost(boundedEnd)
    val aligned = bestAlignedSpan(source, rendered, safeHint, from, to)
    if (aligned != null && safeHint in aligned.first until aligned.second) return aligned
    val localHaystack = source.substring(from, to)
    closestSnippetIndexNear(
        haystack = localHaystack,
        snippet = rendered,
        anchor = safeHint - from,
        radius = radius,
    )?.let { return (from + it) to (from + it + rendered.length) }
    if (aligned != null) return aligned
    val looseAt = locateLooseSnippetNear(localHaystack, rendered, safeHint - from)
        ?.let { from + it }
        ?: return null
    val looseEnd = consumeRenderedQuote(source, looseAt, rendered) ?: return null
    if (looseEnd <= looseAt) return null
    if (looseEnd > boundedEnd) return null
    return looseAt to looseEnd
}

/** 从 [start] 起跳过行内标记，吃完 [rendered] 后返回源码终点；对不上返回 null。 */
internal fun consumeRenderedQuote(source: String, start: Int, rendered: String): Int? {
    if (rendered.isEmpty() || start !in 0..source.length) return null
    var index = start
    var quote = 0
    var skipped = 0
    val maxSkip = rendered.length * 12 + 512
    while (quote < rendered.length) {
        if (index >= source.length || skipped > maxSkip) return null
        val sourceChar = source[index]
        val quoteChar = rendered[quote]
        if (sourceChar == quoteChar || (sourceChar.isWhitespace() && quoteChar.isWhitespace())) {
            index++
            quote++
            continue
        }
        if (quoteChar.isWhitespace()) {
            quote++
            continue
        }
        val filled = skipInlineMarkup(source, index)
        if (filled > index) {
            skipped += filled - index
            index = filled
            continue
        }
        if (sourceChar.isWhitespace()) {
            index++
            skipped++
            continue
        }
        return null
    }
    return index
}

private fun bestAlignedSpan(
    source: String,
    rendered: String,
    hint: Int,
    from: Int,
    to: Int,
): Pair<Int, Int>? {
    val first = rendered.firstOrNull() ?: return null
    var best: Pair<Int, Int>? = null
    var bestRank = Int.MAX_VALUE
    var cursor = from
    while (cursor < to) {
        val start = source.indexOf(first, cursor)
        if (start < 0 || start >= to) break
        val end = consumeRenderedQuote(source, start, rendered)
        if (end != null && end > start) {
            val covers = hint in start until end
            val rank = abs(start - hint) + if (covers) 0 else to - from + 1
            if (rank < bestRank) {
                bestRank = rank
                best = start to end
            }
        }
        cursor = start + 1
    }
    return best
}

/** 跳过一处行内标记。没有标记时原样返回 [index]。 */
private fun skipInlineMarkup(source: String, index: Int): Int {
    if (index >= source.length) return index
    val char = source[index]
    if (char == '*' || char == '_' || char == '`' || char == '~') {
        var next = index + 1
        while (next < source.length && next - index < 3 && source[next] == char) next++
        return next
    }
    if (char == '\\') return (index + 1).coerceAtMost(source.length)
    if (char == '!' && source.getOrNull(index + 1) == '[') return index + 1
    if (char == '[') return index + 1
    if (char == ']') {
        if (source.getOrNull(index + 1) == '(') {
            val close = source.indexOf(')', index + 2)
            if (close > index && close - index <= 2_000) return close + 1
        }
        return index + 1
    }
    if (char == '<') {
        val next = source.getOrNull(index + 1)
        if (next != null && (next.isLetter() || next == '/' || next == '!')) {
            val close = source.indexOf('>', index + 1)
            if (close > index && close - index <= 300) return close + 1
        }
    }
    return index
}

internal fun HighlightEntity.toAnchorQuery(): AnchorQuery =
    AnchorQuery(
        exact = highlightedText,
        docStart = startPosition,
        docEnd = endPosition,
        quotePrefix = quotePrefix,
        quoteSuffix = quoteSuffix,
        blockIndex = blockIndex,
        blockOffsetStart = blockOffsetStart,
        blockOffsetEnd = blockOffsetEnd,
        endBlockIndex = endBlockIndex,
        endBlockHash = endBlockHash,
        blockHash = blockHash,
        docHash = docHash,
        headingPath = headingPath,
        point = false,
    )

internal fun BookmarkEntity.toAnchorQuery(): AnchorQuery =
    AnchorQuery(
        exact = previewText,
        docStart = position,
        docEnd = position,
        quotePrefix = quotePrefix,
        quoteSuffix = quoteSuffix,
        blockIndex = blockIndex,
        blockOffsetStart = blockOffsetStart,
        blockOffsetEnd = blockOffsetEnd,
        endBlockIndex = endBlockIndex,
        endBlockHash = endBlockHash,
        blockHash = blockHash,
        docHash = docHash,
        headingPath = headingPath,
        point = true,
    )

internal fun HighlightEntity.withCapturedAnchor(anchor: CapturedTextAnchor): HighlightEntity =
    copy(
        quotePrefix = anchor.quotePrefix,
        quoteSuffix = anchor.quoteSuffix,
        blockIndex = anchor.blockIndex,
        blockOffsetStart = anchor.blockOffsetStart,
        blockOffsetEnd = anchor.blockOffsetEnd,
        endBlockIndex = anchor.endBlockIndex,
        endBlockHash = anchor.endBlockHash,
        blockHash = anchor.blockHash,
        docHash = anchor.docHash,
        headingPath = anchor.headingPath.ifEmpty { null },
    )

internal fun BookmarkEntity.withCapturedAnchor(anchor: CapturedTextAnchor): BookmarkEntity =
    copy(
        quotePrefix = anchor.quotePrefix,
        quoteSuffix = anchor.quoteSuffix,
        blockIndex = anchor.blockIndex,
        blockOffsetStart = anchor.blockOffsetStart,
        blockOffsetEnd = anchor.blockOffsetEnd,
        endBlockIndex = anchor.endBlockIndex,
        endBlockHash = anchor.endBlockHash,
        blockHash = anchor.blockHash,
        docHash = anchor.docHash,
        headingPath = anchor.headingPath.ifEmpty { null },
    )

internal data class AnnotationPaintPlan(
    val paintHighlights: List<HighlightEntity>,
    val pendingHighlightIds: Set<Long>,
    val pendingBookmarkIds: Set<Long>,
    val highlightJump: Map<Long, Int>,
    val bookmarkJump: Map<Long, Int>,
)

/** 在全文源码上解析划线和书签。上色只用唯一区间，待确认项仍留在列表里。 */
internal fun planAnnotationAnchors(
    document: String,
    plainText: Boolean,
    docHash: String,
    toc: List<MarkdownTocEntry>,
    highlights: List<HighlightEntity>,
    bookmarks: List<BookmarkEntity>,
): AnnotationPaintPlan {
    if (document.isEmpty()) {
        return AnnotationPaintPlan(
            paintHighlights = highlights,
            pendingHighlightIds = emptySet(),
            pendingBookmarkIds = emptySet(),
            highlightJump = emptyMap(),
            bookmarkJump = emptyMap(),
        )
    }
    val blocks = TextBlockIndex.build(document, plainText)
    val paint = ArrayList<HighlightEntity>(highlights.size)
    val pendingHighlights = LinkedHashSet<Long>()
    val pendingBookmarks = LinkedHashSet<Long>()
    val highlightJump = HashMap<Long, Int>(highlights.size)
    val bookmarkJump = HashMap<Long, Int>(bookmarks.size)
    for (highlight in highlights) {
        val result = AnnotationAnchorResolver.resolve(
            query = highlight.toAnchorQuery(),
            document = document,
            blocks = blocks,
            currentDocHash = docHash,
            toc = toc,
        )
        when (result) {
            is AnchorResolveResult.Unique -> {
                paint += highlight.copy(startPosition = result.start, endPosition = result.end)
                highlightJump[highlight.id] = result.start
            }
            is AnchorResolveResult.Ambiguous -> {
                pendingHighlights += highlight.id
                highlightJump[highlight.id] = result.start
            }
            AnchorResolveResult.Unresolved -> {
                pendingHighlights += highlight.id
                highlightJump[highlight.id] = highlight.startPosition
            }
        }
    }
    for (bookmark in bookmarks) {
        val result = AnnotationAnchorResolver.resolve(
            query = bookmark.toAnchorQuery(),
            document = document,
            blocks = blocks,
            currentDocHash = docHash,
            toc = toc,
        )
        when (result) {
            is AnchorResolveResult.Unique -> bookmarkJump[bookmark.id] = result.start
            is AnchorResolveResult.Ambiguous -> {
                pendingBookmarks += bookmark.id
                bookmarkJump[bookmark.id] = result.start
            }
            AnchorResolveResult.Unresolved -> {
                pendingBookmarks += bookmark.id
                bookmarkJump[bookmark.id] = bookmark.position
            }
        }
    }
    return AnnotationPaintPlan(
        paintHighlights = paint,
        pendingHighlightIds = pendingHighlights,
        pendingBookmarkIds = pendingBookmarks,
        highlightJump = highlightJump,
        bookmarkJump = bookmarkJump,
    )
}
