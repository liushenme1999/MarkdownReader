package space.liushenme.markdownreader.ui.screens.reader.anchor

import space.liushenme.markdownreader.ui.screens.reader.MarkdownTocEntry
import space.liushenme.markdownreader.ui.screens.reader.closestSnippetIndexNear
import kotlin.math.abs
import kotlin.math.min

/**
 * 在源码上恢复划线或书签的区间。
 * 文档没变、或保存位置所在块的文本还在时，直接用已保存的区间。
 * 渲染引文和源码切片可以不一样（中间夹着加粗、链接）。
 * 块已经对不上时，再按块哈希、上下文引文、仅引文、章节内模糊回退。
 * 最高分不足 0.5，或与第二名相差不足 0.1，都不返回可上色的结果。
 */
internal object AnnotationAnchorResolver {
    private const val ACCEPT_SCORE = 0.5
    private const val AMBIGUITY_GAP = 0.1
    private const val MAX_HITS = 32
    private const val FUZZY_MIN_CHARS = 8
    private const val FUZZY_LONG_QUOTE = 500
    private const val FUZZY_EDGE_CHARS = 80
    private const val DISTANCE_BONUS = 0.08
    private const val DISTANCE_SCALE = 20_000.0
    private const val CONTEXT_BONUS = 0.05
    private const val BLOCK_SCORE = 0.98
    private const val CONTEXT_SCORE = 0.90
    private const val EXACT_SCORE = 0.65
    private const val FUZZY_WEIGHT = 0.4

    fun resolve(
        query: AnchorQuery,
        document: String,
        blocks: TextBlockIndex,
        currentDocHash: String,
        toc: List<MarkdownTocEntry>,
    ): AnchorResolveResult {
        if (document.isEmpty()) return AnchorResolveResult.Unresolved
        if (isLegacy(query)) return resolveLegacy(query, document)
        fastPath(query, document, currentDocHash)?.let { return it }
        stableSavedRange(query, document, blocks)?.let { return it }

        val stages = listOf(
            blockCandidates(query, document, blocks),
            contextCandidates(query, document, blocks, toc),
            exactCandidates(query, document, blocks, toc),
            fuzzyCandidates(query, document, blocks, toc),
        )
        var fallback: AnchorResolveResult.Ambiguous? = null
        for (hits in stages) {
            if (hits.isEmpty()) continue
            when (val picked = pick(hits, query, document)) {
                is AnchorResolveResult.Unique -> return picked
                is AnchorResolveResult.Ambiguous -> if (fallback == null) fallback = picked
                AnchorResolveResult.Unresolved -> Unit
            }
        }
        return fallback ?: AnchorResolveResult.Unresolved
    }

    fun resolveHighlight(
        highlight: space.liushenme.markdownreader.data.local.entity.HighlightEntity,
        document: String,
        blocks: TextBlockIndex,
        currentDocHash: String,
        toc: List<MarkdownTocEntry>,
    ): AnchorResolveResult = resolve(highlight.toAnchorQuery(), document, blocks, currentDocHash, toc)

    fun resolveBookmark(
        bookmark: space.liushenme.markdownreader.data.local.entity.BookmarkEntity,
        document: String,
        blocks: TextBlockIndex,
        currentDocHash: String,
        toc: List<MarkdownTocEntry>,
    ): AnchorResolveResult = resolve(bookmark.toAnchorQuery(), document, blocks, currentDocHash, toc)

    private fun isLegacy(query: AnchorQuery): Boolean =
        query.blockHash == null && query.quotePrefix == null && query.quoteSuffix == null

    private fun fastPath(
        query: AnchorQuery,
        document: String,
        currentDocHash: String,
    ): AnchorResolveResult? {
        if (query.docHash.isNullOrEmpty() || query.docHash != currentDocHash) return null
        if (query.point) {
            if (!pointAnchoredAt(document, query.docStart, query)) return null
            return AnchorResolveResult.Unique(query.docStart, query.docStart, 1.0)
        }
        val start = query.docStart
        val end = query.docEnd
        if (start < 0 || end > document.length || end <= start) return null
        if (!sourceSliceMatchesQuote(document.substring(start, end), query.exact)) return null
        return AnchorResolveResult.Unique(start, end, 1.0)
    }

    /**
     * 保存位置仍落在当时那一块里，说明用户划下的区间还在。
     * 不要求渲染文字原样出现在源码切片中。
     */
    private fun stableSavedRange(
        query: AnchorQuery,
        document: String,
        blocks: TextBlockIndex,
    ): AnchorResolveResult? {
        val hash = query.blockHash ?: return null
        val start = query.docStart
        if (start !in 0..document.length) return null
        val probe = if (start >= document.length) (document.length - 1).coerceAtLeast(0) else start
        val startBlock = blocks.blockCovering(probe) ?: return null
        if (startBlock.hash != hash) return null
        val startOffset = query.blockOffsetStart ?: return null
        if (!offsetInBlock(startBlock, startOffset) || startBlock.start + startOffset != start) return null
        if (query.point) return AnchorResolveResult.Unique(start, start, 1.0)
        val end = query.docEnd
        if (end <= start || end > document.length) return null
        val endHash = query.endBlockHash
        if (endHash.isNullOrEmpty()) {
            val endOffset = query.blockOffsetEnd ?: return null
            if (!offsetInBlock(startBlock, endOffset) || endOffset <= startOffset) return null
            if (startBlock.start + endOffset != end) return null
            return AnchorResolveResult.Unique(start, end, 1.0)
        }
        val endOffset = query.blockOffsetEnd ?: return null
        val endBlock = blocks.blockCovering((end - 1).coerceAtLeast(0)) ?: return null
        if (endBlock.hash != endHash || !offsetInBlock(endBlock, endOffset)) return null
        if (endBlock.start + endOffset != end) return null
        return AnchorResolveResult.Unique(start, end, 1.0)
    }

    private fun offsetInBlock(block: SourceBlock, offset: Int): Boolean {
        val length = block.end - block.start
        return offset in 0..length
    }

    private fun resolveLegacy(query: AnchorQuery, document: String): AnchorResolveResult {
        val exact = query.exact
        if (!query.point &&
            query.docStart >= 0 &&
            query.docEnd <= document.length &&
            query.docEnd > query.docStart &&
            sourceSliceMatchesQuote(document.substring(query.docStart, query.docEnd), exact) &&
            document.substring(query.docStart, query.docEnd) == exact
        ) {
            return AnchorResolveResult.Unique(query.docStart, query.docEnd, 1.0)
        }
        if (exact.isEmpty()) return AnchorResolveResult.Unresolved
        val radius = maxOf(exact.length * 8, 200)
        closestSnippetIndexNear(document, exact, query.docStart, radius)?.let { index ->
            val end = if (query.point) index else index + exact.length
            return AnchorResolveResult.Unique(index, end, 0.65)
        }
        val searchStart = (query.docStart - radius).coerceAtLeast(0)
        val searchEnd = (query.docStart + radius + exact.length).coerceAtMost(document.length)
        val aligned = sourceSpanForRenderedQuote(
            source = document,
            rendered = exact,
            hint = query.docStart,
            searchStart = searchStart,
            searchEnd = searchEnd,
        ) ?: return AnchorResolveResult.Unresolved
        if (abs(aligned.first - query.docStart) > radius) return AnchorResolveResult.Unresolved
        return AnchorResolveResult.Unique(
            start = aligned.first,
            end = if (query.point) aligned.first else aligned.second,
            score = 0.60,
        )
    }

    private fun blockCandidates(
        query: AnchorQuery,
        document: String,
        blocks: TextBlockIndex,
    ): List<Hit> {
        val hash = query.blockHash ?: return emptyList()
        val startBlocks = blocks.blocks.filter { it.hash == hash }
        if (startBlocks.isEmpty()) return emptyList()
        val startOffset = query.blockOffsetStart ?: return emptyList()
        val hits = mutableListOf<Hit>()
        val endHash = query.endBlockHash
        if (!endHash.isNullOrEmpty()) {
            val endOffset = query.blockOffsetEnd ?: return emptyList()
            val endBlocks = blocks.blocks.filter { it.hash == endHash }
            for (startBlock in startBlocks) {
                if (!offsetInBlock(startBlock, startOffset)) continue
                val start = startBlock.start + startOffset
                for (endBlock in endBlocks) {
                    if (!offsetInBlock(endBlock, endOffset)) continue
                    val end = endBlock.start + endOffset
                    if (end > document.length || end <= start) continue
                    hits += Hit(start, end, BLOCK_SCORE)
                }
            }
            return hits
        }
        for (startBlock in startBlocks) {
            if (!offsetInBlock(startBlock, startOffset)) continue
            val start = startBlock.start + startOffset
            if (query.point) {
                // 块文本没变时，块内偏移仍然有效。只有重复块才再用前后文区分。
                if (start in 0..document.length &&
                    (startBlocks.size == 1 || pointAnchoredAt(document, start, query))
                ) {
                    hits += Hit(start, start, BLOCK_SCORE)
                }
                continue
            }
            val endOffset = query.blockOffsetEnd
            val end = when {
                endOffset != null && offsetInBlock(startBlock, endOffset) -> startBlock.start + endOffset
                else -> start + query.exact.length
            }
            if (end > document.length || end <= start) continue
            if (endOffset == null &&
                !sourceSliceMatchesQuote(document.substring(start, end), query.exact)
            ) {
                continue
            }
            if (endOffset != null && end > startBlock.end) continue
            hits += Hit(start, end, BLOCK_SCORE)
        }
        return hits
    }

    private fun contextCandidates(
        query: AnchorQuery,
        document: String,
        blocks: TextBlockIndex,
        toc: List<MarkdownTocEntry>,
    ): List<Hit> {
        val prefix = query.quotePrefix.orEmpty()
        val suffix = query.quoteSuffix.orEmpty()
        if (prefix.isEmpty() && suffix.isEmpty()) return emptyList()
        val region = quoteRegion(document, blocks, toc, query)
        if (query.point) {
            val pattern = prefix + suffix
            if (pattern.isEmpty()) return emptyList()
            return findAll(document, pattern, region).map { index ->
                val position = index + prefix.length
                Hit(position, position, CONTEXT_SCORE)
            }
        }
        if (query.exact.isEmpty()) return emptyList()
        val pattern = prefix + query.exact + suffix
        val literal = findAll(document, pattern, region).map { index ->
            val start = index + prefix.length
            Hit(start, start + query.exact.length, CONTEXT_SCORE)
        }
        if (literal.isNotEmpty() || prefix.isEmpty()) return literal
        return findAll(document, prefix, region).mapNotNull { index ->
            val start = index + prefix.length
            val end = consumeRenderedQuote(document, start, query.exact) ?: return@mapNotNull null
            if (suffix.isNotEmpty() && !suffixMatches(document, end, suffix)) return@mapNotNull null
            Hit(start, end, CONTEXT_SCORE)
        }
    }

    private fun exactCandidates(
        query: AnchorQuery,
        document: String,
        blocks: TextBlockIndex,
        toc: List<MarkdownTocEntry>,
    ): List<Hit> {
        val exact = query.exact
        if (exact.isEmpty()) return emptyList()
        val region = quoteRegion(document, blocks, toc, query)
        return findAll(document, exact, region).map { index ->
            if (query.point) Hit(index, index, EXACT_SCORE) else Hit(index, index + exact.length, EXACT_SCORE)
        }
    }

    private fun fuzzyCandidates(
        query: AnchorQuery,
        document: String,
        blocks: TextBlockIndex,
        toc: List<MarkdownTocEntry>,
    ): List<Hit> {
        val exact = query.exact
        if (exact.length < FUZZY_MIN_CHARS) return emptyList()
        val region = fuzzyRegion(document, blocks, toc, query)
        val spans = if (exact.length > FUZZY_LONG_QUOTE) {
            fuzzyHeadTail(document, region, exact)
        } else {
            fuzzyNear(document, region, exact)
        }
        return spans.map { (start, end, similarity) ->
            val resolvedEnd = if (query.point) start else end
            Hit(start, resolvedEnd, FUZZY_WEIGHT * similarity)
        }
    }

    private fun pick(hits: List<Hit>, query: AnchorQuery, document: String): AnchorResolveResult {
        if (hits.isEmpty()) return AnchorResolveResult.Unresolved
        val scored = hits
            .groupBy { it.start to it.end }
            .map { (range, grouped) ->
                val base = grouped.maxOf { it.base }
                val bonus = distanceBonus(range.first, query.docStart) +
                    contextBonus(document, range.first, range.second, query)
                Scored(range.first, range.second, base + bonus)
            }
            .sortedWith(
                compareByDescending<Scored> { it.score }
                    .thenBy { abs(it.start - query.docStart) },
            )
        val best = scored.first()
        val second = scored.getOrNull(1)?.score ?: Double.NEGATIVE_INFINITY
        if (best.score >= ACCEPT_SCORE && best.score - second >= AMBIGUITY_GAP) {
            return AnchorResolveResult.Unique(best.start, best.end, best.score)
        }
        if (best.score > 0.0) {
            return AnchorResolveResult.Ambiguous(best.start, best.end, best.score)
        }
        return AnchorResolveResult.Unresolved
    }

    private fun quoteRegion(
        document: String,
        blocks: TextBlockIndex,
        toc: List<MarkdownTocEntry>,
        query: AnchorQuery,
    ): IntRange {
        val section = TextBlockIndex.sectionRange(document.length, toc, query.headingPath, query.docStart)
        if (section != null) return section
        if (query.headingPath.isNullOrEmpty()) return 0 until document.length
        return nearbyBlockRange(document.length, blocks, query.docStart)
    }

    private fun fuzzyRegion(
        document: String,
        blocks: TextBlockIndex,
        toc: List<MarkdownTocEntry>,
        query: AnchorQuery,
    ): IntRange {
        TextBlockIndex.sectionRange(document.length, toc, query.headingPath, query.docStart)?.let { return it }
        return nearbyBlockRange(document.length, blocks, query.docStart)
    }

    private fun nearbyBlockRange(documentLength: Int, blocks: TextBlockIndex, hint: Int): IntRange {
        val block = blocks.blockCovering(hint)
        if (block == null || blocks.blocks.isEmpty()) {
            val from = (hint - 2000).coerceAtLeast(0)
            val to = (hint + 2000).coerceAtMost(documentLength)
            return from until to.coerceAtLeast(from)
        }
        val from = (block.index - 2).coerceAtLeast(0)
        val to = (block.index + 2).coerceAtMost(blocks.blocks.lastIndex)
        val start = blocks.blocks[from].start
        val end = blocks.blocks[to].end.coerceAtMost(documentLength)
        return start until end.coerceAtLeast(start)
    }

    private fun pointAnchoredAt(document: String, position: Int, query: AnchorQuery): Boolean {
        if (position !in 0..document.length) return false
        val prefix = query.quotePrefix
        val suffix = query.quoteSuffix
        val prefixOk = prefix.isNullOrEmpty() || prefixMatches(document, position, prefix)
        val suffixOk = suffix.isNullOrEmpty() || suffixMatches(document, position, suffix)
        val hasContext = !prefix.isNullOrEmpty() || !suffix.isNullOrEmpty()
        if (hasContext && prefixOk && suffixOk) return true
        val exact = query.exact
        return exact.isNotEmpty() &&
            position + exact.length <= document.length &&
            document.regionMatches(position, exact, 0, exact.length)
    }

    private fun distanceBonus(candidate: Int, hint: Int): Double {
        val distance = abs(candidate - hint).toDouble()
        return DISTANCE_BONUS * (1.0 - (distance / DISTANCE_SCALE).coerceAtMost(1.0))
    }

    private fun contextBonus(document: String, start: Int, end: Int, query: AnchorQuery): Double {
        var bonus = 0.0
        if (prefixMatches(document, start, query.quotePrefix)) bonus += CONTEXT_BONUS
        if (suffixMatches(document, end, query.quoteSuffix)) bonus += CONTEXT_BONUS
        return bonus
    }

    private fun prefixMatches(document: String, start: Int, prefix: String?): Boolean {
        if (prefix.isNullOrEmpty() || start < prefix.length) return false
        return document.regionMatches(start - prefix.length, prefix, 0, prefix.length)
    }

    private fun suffixMatches(document: String, end: Int, suffix: String?): Boolean {
        if (suffix.isNullOrEmpty() || end + suffix.length > document.length) return false
        return document.regionMatches(end, suffix, 0, suffix.length)
    }

    private fun findAll(haystack: String, needle: String, region: IntRange): List<Int> {
        if (needle.isEmpty() || region.isEmpty()) return emptyList()
        val out = ArrayList<Int>()
        var from = region.first.coerceAtLeast(0)
        val limit = (region.last.toLong() + 1L).coerceAtMost(haystack.length.toLong()).toInt()
        while (from < limit && out.size < MAX_HITS) {
            val index = haystack.indexOf(needle, from)
            if (index < 0 || index + needle.length > limit) break
            out += index
            from = index + 1
        }
        return out
    }

    private fun fuzzyHeadTail(
        document: String,
        region: IntRange,
        needle: String,
    ): List<Triple<Int, Int, Double>> {
        val head = needle.take(FUZZY_EDGE_CHARS)
        val tail = needle.takeLast(FUZZY_EDGE_CHARS)
        val heads = fuzzyNear(document, region, head)
        val out = mutableListOf<Triple<Int, Int, Double>>()
        val regionEnd = (region.last.toLong() + 1L).coerceAtMost(document.length.toLong()).toInt()
        for ((start, end, headSimilarity) in heads) {
            val tailFrom = (end - 20).coerceAtLeast(region.first)
            val tailTo = (start + needle.length + FUZZY_EDGE_CHARS).coerceAtMost(regionEnd)
            if (tailFrom >= tailTo) continue
            val tails = fuzzyNear(document, tailFrom until tailTo, tail)
            for ((tailStart, tailEnd, tailSimilarity) in tails) {
                if (tailStart + tail.length < end) continue
                out += Triple(start, maxOf(tailEnd, tailStart + 1), (headSimilarity + tailSimilarity) / 2.0)
            }
        }
        return out
    }

    private fun fuzzyNear(
        document: String,
        region: IntRange,
        needle: String,
    ): List<Triple<Int, Int, Double>> {
        if (needle.length < FUZZY_MIN_CHARS || region.isEmpty()) return emptyList()
        val regionEnd = (region.last.toLong() + 1L).coerceAtMost(document.length.toLong()).toInt()
        val maxEdits = (needle.length * 0.15).toInt().coerceAtLeast(1)
        val anchors = intArrayOf(0, needle.length / 2)
        val starts = LinkedHashSet<Int>()
        for (anchor in anchors) {
            val gramEnd = (anchor + 4).coerceAtMost(needle.length)
            if (gramEnd - anchor < 3) continue
            val gram = needle.substring(anchor, gramEnd)
            var from = region.first.coerceAtLeast(0)
            var seen = 0
            while (seen < 20) {
                val index = document.indexOf(gram, from)
                if (index < 0 || index >= regionEnd) break
                val aligned = index - anchor
                if (aligned < regionEnd) starts += aligned.coerceAtLeast(region.first)
                from = index + 1
                seen++
            }
        }
        val out = mutableListOf<Triple<Int, Int, Double>>()
        for (start in starts) {
            var bestSimilarity = 0.0
            var bestEnd = -1
            for (delta in -maxEdits..maxEdits) {
                val length = needle.length + delta
                if (length <= 0 || start + length > regionEnd) continue
                val slice = document.substring(start, start + length)
                val distance = editDistanceAtMost(slice, needle, maxEdits) ?: continue
                if (distance.toDouble() / needle.length > 0.15) continue
                val similarity = 1.0 - distance.toDouble() / needle.length
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestEnd = start + length
                }
            }
            if (bestEnd > start) out += Triple(start, bestEnd, bestSimilarity)
        }
        return out
    }

    private data class Hit(val start: Int, val end: Int, val base: Double)

    private data class Scored(val start: Int, val end: Int, val score: Double)
}

internal fun sourceSliceMatchesQuote(slice: String, exact: String): Boolean {
    if (exact.isEmpty()) return false
    if (slice == exact || slice.contains(exact)) return true
    val normalizedExact = TextBlockIndex.normalize(exact)
    if (normalizedExact.isEmpty()) return false
    val normalizedSlice = TextBlockIndex.normalize(slice)
    return normalizedSlice == normalizedExact || normalizedSlice.contains(normalizedExact)
}

/** 编辑距离不超过 [max]，超出则返回 null。 */
internal fun editDistanceAtMost(left: String, right: String, max: Int): Int? {
    if (abs(left.length - right.length) > max) return null
    val rows = left.length
    val cols = right.length
    var previous = IntArray(cols + 1) { it }
    var current = IntArray(cols + 1)
    for (row in 1..rows) {
        current[0] = row
        var rowMin = current[0]
        val leftChar = left[row - 1]
        for (col in 1..cols) {
            val cost = if (leftChar == right[col - 1]) 0 else 1
            current[col] = min(
                min(previous[col] + 1, current[col - 1] + 1),
                previous[col - 1] + cost,
            )
            if (current[col] < rowMin) rowMin = current[col]
        }
        if (rowMin > max) return null
        val swap = previous
        previous = current
        current = swap
    }
    val distance = previous[cols]
    return distance.takeIf { it <= max }
}
