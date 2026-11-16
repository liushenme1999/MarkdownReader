package space.liushenme.markdownreader.markdown

/**
 * 折叠后的代码文本，以及「显示行 ↔ 源码行」「显示偏移 ↔ 源码偏移」的双向映射。
 *
 * 约定：
 * - 行号从 1 开始；字符偏移从 0 开始。
 * - 行数按编辑器语义统计：文本以换行结尾时不会多出一个空行（与 [CodeDefinition.endLine]
 *   的计数一致）。因此 [displayLineStarts] / [sourceLineStarts] 的长度是「行数 + 1」，
 *   前「行数」项是每行行首，最后一项是整段文本的长度，作为越过最后一行的哨兵。
 * - 显示行里被折叠的函数体只剩一行占位符，[text] 中原样保留各行的 `\r`，
 *   用 `\n` 连接，所以 CRLF 源码中未被折叠的行仍是 `\r\n`。
 */
data class FoldedCodeSource(
    val text: String,
    val displayLineToSourceLine: IntArray,
    val sourceLineToDisplayLine: IntArray,
    val displayLineStarts: IntArray,
    val sourceLineStarts: IntArray,
) {

    /** 源码偏移 -> 显示偏移；被折叠函数体内的偏移统一落到占位符行首。 */
    fun sourceOffsetToDisplayOffset(sourceOffset: Int): Int {
        if (sourceLineToDisplayLine.isEmpty()) return 0
        val safe = sourceOffset.coerceIn(0, sourceLineStarts.last())
        val sourceIndex = lineIndexAt(sourceLineStarts, sourceLineToDisplayLine.size, safe)
        val displayLine = sourceLineToDisplayLine[sourceIndex]
        if (displayLine !in 1..displayLineToSourceLine.size) return 0
        val displayStart = displayLineStarts[displayLine - 1]
        // 被折叠的行映射到占位符行首；声明行/普通行映射到行首 + 行内偏移。
        if (isPlaceholderLine(displayLine - 1)) return displayStart.coerceIn(0, text.length)
        val inLine = safe - sourceLineStarts[sourceIndex]
        return (displayStart + inLine).coerceIn(0, text.length)
    }

    /** 显示偏移 -> 源码偏移；占位符行统一映射到对应 declaration 的 startLine 行首。 */
    fun displayOffsetToSourceOffset(displayOffset: Int): Int {
        if (displayLineToSourceLine.isEmpty()) return 0
        val safe = displayOffset.coerceIn(0, text.length)
        val displayIndex = lineIndexAt(displayLineStarts, displayLineToSourceLine.size, safe)
        val sourceLine = displayLineToSourceLine[displayIndex]
        if (sourceLine !in 1..sourceLineToDisplayLine.size) return 0
        val sourceStart = sourceLineStarts[sourceLine - 1]
        if (isPlaceholderLine(displayIndex)) return sourceStart.coerceIn(0, sourceLineStarts.last())
        val inLine = safe - displayLineStarts[displayIndex]
        return (sourceStart + inLine).coerceIn(0, sourceLineStarts.last())
    }

    /**
     * 占位符行的结构特征：它紧跟在 declaration 行之后，且两者映射到同一个源码行。
     * 正常行映射到的源码行严格递增，所以不会误判；这样 [FoldedCodeSource] 无需额外字段。
     */
    private fun isPlaceholderLine(displayIndex: Int): Boolean =
        displayIndex > 0 &&
            displayLineToSourceLine[displayIndex] == displayLineToSourceLine[displayIndex - 1]

    /** 二分查找 [offset] 落在哪一行（0-based），[starts] 的前 [lineCount] 项是行首。 */
    private fun lineIndexAt(starts: IntArray, lineCount: Int, offset: Int): Int {
        var low = 0
        var high = lineCount - 1
        var line = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= offset) {
                line = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return line
    }
}

/**
 * 把代码文本中「被折叠函数」的函数体替换成一行占位符，并给出显示坐标与源码坐标的映射。
 *
 * 折叠规则：
 * - 只折叠 `startLine in collapsedStartLines && endLine > startLine` 的定义；
 * - 保留声明行，随后插入 `<缩进>⋯ 已折叠 <行数> 行`，再跳过 `startLine + 1 .. endLine`；
 * - 折叠区间按 startLine 升序处理；同一 startLine 取 endLine 最大者；完全落在已折叠区间内的
 *   内层定义会被忽略（同时 startLine 已落在折叠体内的定义也一并忽略）。
 *
 * 没有任何可折叠定义时原样返回，映射为恒等映射。
 */
object CodeFoldTransformer {

    const val FOLD_PLACEHOLDER_PREFIX = "⋯ 已折叠"

    fun transform(
        source: String,
        outline: CodeOutline,
        collapsedStartLines: Set<Int>,
    ): FoldedCodeSource {
        val lines = CodeLines.parse(source)
        val folds = resolveFolds(outline.definitions, collapsedStartLines, lines.count)
        return if (folds.isEmpty()) identity(source, lines) else fold(lines, folds)
    }

    private data class Fold(val startLine: Int, val endLine: Int)

    private fun identity(source: String, lines: CodeLines): FoldedCodeSource {
        val identityLines = IntArray(lines.count) { it + 1 }
        return FoldedCodeSource(
            text = source,
            displayLineToSourceLine = identityLines,
            sourceLineToDisplayLine = identityLines.copyOf(),
            displayLineStarts = lines.starts.copyOf(),
            sourceLineStarts = lines.starts.copyOf(),
        )
    }

    private fun resolveFolds(
        definitions: List<CodeDefinition>,
        collapsedStartLines: Set<Int>,
        lineCount: Int,
    ): List<Fold> {
        if (definitions.isEmpty() || collapsedStartLines.isEmpty()) return emptyList()
        // 同一 startLine 可能对应多个定义（例如一行内 class 与 fun），取 endLine 最大者。
        val maxEndByStart = HashMap<Int, Int>()
        for (definition in definitions) {
            val startLine = definition.startLine
            if (startLine !in collapsedStartLines || startLine !in 1..lineCount) continue
            val endLine = definition.endLine.coerceAtMost(lineCount)
            if (endLine <= startLine) continue
            val known = maxEndByStart[startLine]
            if (known == null || endLine > known) maxEndByStart[startLine] = endLine
        }
        if (maxEndByStart.isEmpty()) return emptyList()

        // startLine 升序 = 外层优先；startLine 已落在折叠体内说明它是内层，直接忽略。
        val foldedLines = BooleanArray(lineCount + 1)
        val folds = ArrayList<Fold>(maxEndByStart.size)
        for (startLine in maxEndByStart.keys.sorted()) {
            if (foldedLines[startLine]) continue
            val endLine = maxEndByStart.getValue(startLine)
            folds += Fold(startLine, endLine)
            for (line in (startLine + 1)..endLine) foldedLines[line] = true
        }
        return folds
    }

    private fun fold(lines: CodeLines, folds: List<Fold>): FoldedCodeSource {
        val foldByStart = folds.associateBy { it.startLine }
        val displayContents = ArrayList<String>(lines.count + folds.size)
        val displayToSource = ArrayList<Int>(lines.count + folds.size)
        val sourceToDisplay = IntArray(lines.count)
        var sourceLine = 1
        while (sourceLine <= lines.count) {
            val declaration = lines.contentAt(sourceLine)
            displayContents += declaration
            displayToSource += sourceLine
            sourceToDisplay[sourceLine - 1] = displayContents.size
            val fold = foldByStart[sourceLine]
            if (fold == null) {
                sourceLine++
                continue
            }
            displayContents += placeholderLine(declaration, fold.endLine - fold.startLine)
            displayToSource += sourceLine
            val placeholderDisplayLine = displayContents.size
            for (line in (sourceLine + 1)..fold.endLine) {
                sourceToDisplay[line - 1] = placeholderDisplayLine
            }
            sourceLine = fold.endLine + 1
        }

        val text = buildText(displayContents, lines.endsWithNewline)
        val displayLineStarts = IntArray(displayContents.size + 1)
        var offset = 0
        for (index in displayContents.indices) {
            displayLineStarts[index] = offset
            offset += displayContents[index].length + 1
        }
        displayLineStarts[displayContents.size] = text.length
        return FoldedCodeSource(
            text = text,
            displayLineToSourceLine = displayToSource.toIntArray(),
            sourceLineToDisplayLine = sourceToDisplay,
            displayLineStarts = displayLineStarts,
            sourceLineStarts = lines.starts.copyOf(),
        )
    }

    private fun buildText(displayContents: List<String>, endsWithNewline: Boolean): String {
        val builder = StringBuilder()
        for (index in displayContents.indices) {
            if (index > 0) builder.append('\n')
            builder.append(displayContents[index])
        }
        if (endsWithNewline) builder.append('\n')
        return builder.toString()
    }

    /** 占位符沿用声明行的行首空白（空格/制表符），保证缩进层级与源码一致。 */
    private fun placeholderLine(declaration: String, foldedLineCount: Int): String {
        val indent = declaration.takeWhile { it == ' ' || it == '\t' }
        return "$indent$FOLD_PLACEHOLDER_PREFIX $foldedLineCount 行"
    }

    /**
     * 源码行模型：每行内容不含作为分隔符的 `\n`，但保留行尾 `\r`，
     * 因此用 `\n` 重新拼接即可逐字还原源码（CRLF 也原样保留）。
     */
    private class CodeLines(
        private val contents: List<String>,
        val starts: IntArray,
        val endsWithNewline: Boolean,
    ) {
        val count: Int get() = contents.size

        fun contentAt(line: Int): String = contents[line - 1]

        companion object {
            fun parse(source: String): CodeLines {
                // 与 CodeOutlineParser 的行号统计保持一致：末尾换行不额外产生空行。
                val lineStarts = ArrayList<Int>()
                lineStarts += 0
                for (index in source.indices) {
                    if (source[index] == '\n' && index + 1 < source.length) lineStarts += index + 1
                }
                val endsWithNewline = source.endsWith('\n')
                val contents = ArrayList<String>(lineStarts.size)
                for (lineIndex in lineStarts.indices) {
                    val start = lineStarts[lineIndex]
                    val next = lineStarts.getOrNull(lineIndex + 1)
                    val end = when {
                        next != null -> next - 1
                        endsWithNewline -> (source.length - 1).coerceAtLeast(start)
                        else -> source.length
                    }
                    contents += source.substring(start, end)
                }
                val starts = IntArray(lineStarts.size + 1)
                for (index in lineStarts.indices) starts[index] = lineStarts[index]
                starts[lineStarts.size] = source.length
                return CodeLines(contents, starts, endsWithNewline)
            }
        }
    }
}
