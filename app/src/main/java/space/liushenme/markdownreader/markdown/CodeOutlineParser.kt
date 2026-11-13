package space.liushenme.markdownreader.markdown

/**
 * One class/function declaration that encloses the current source line.
 *
 * [startLine] and [endLine] are 1-based and inclusive. [text] is the original
 * declaration line without leading indentation, so the reader can show it
 * verbatim in the sticky code header.
 */
data class CodeDefinition(
    val startLine: Int,
    val endLine: Int,
    val indent: Int,
    val text: String,
    val depth: Int = 0,
)

/**
 * Lightweight source outline used by the full-screen code reader.
 *
 * This is intentionally not a compiler parser. It recognizes the declarations
 * that are useful for VS Code-style sticky scroll while keeping brace matching
 * aware of comments and strings.
 */
class CodeOutline internal constructor(
    val definitions: List<CodeDefinition>,
    private val lineStarts: IntArray,
) {
    fun pathAtLine(line: Int): List<CodeDefinition> {
        if (definitions.isEmpty() || line < 1) return emptyList()
        // Definitions are already ordered by opening line (outer before inner when starts
        // match), so filtering preserves the sticky-scroll path without sorting every frame.
        return definitions
            .filter { line in it.startLine..it.endLine }
    }

    fun pathAtOffset(offset: Int): List<CodeDefinition> =
        pathAtLine(lineAtOffset(offset))

    /** Returns a 1-based line number for [offset]. */
    fun lineAtOffset(offset: Int): Int {
        if (lineStarts.isEmpty()) return 1
        val safe = offset.coerceIn(0, lineStarts.last())
        var low = 0
        var high = lineStarts.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            when {
                lineStarts[mid] <= safe -> low = mid + 1
                else -> high = mid - 1
            }
        }
        return (high + 1).coerceAtLeast(1)
    }

    companion object {
        val EMPTY = CodeOutline(emptyList(), intArrayOf(0))
    }
}

object CodeOutlineParser {

    private const val MAX_SIGNATURE_LOOKAHEAD_LINES = 12

    private val structuralDeclaration = Regex(
        """(?:^|\s)(?:class|interface|enum|record|struct|union|trait|impl|object|namespace|protocol|extension|module|constructor)\b""",
    )
    private val functionDeclaration = Regex(
        """(?:^|\s)(?:async\s+)?(?:fun|function|func|fn|def)\b""",
    )
    private val arrowFunctionDeclaration = Regex(
        """(?:^|\s)(?:const|let|var)\s+[A-Za-z_$][\w$]*[^=]*=\s*(?:async\s+)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)\s*=>""",
    )
    private val pythonDefinition = Regex(
        """^\s*(?:async\s+def|def|class)\s+[A-Za-z_]\w*""",
    )
    private val rubyDefinition = Regex(
        """^\s*(?:def|class|module)\s+(?:self\.)?[A-Za-z_]\w*[!?=]?""",
    )
    private val methodName = Regex(
        """(?:(?:[A-Za-z_$][\w$]*::)*~?[A-Za-z_$][\w$]*|~[A-Za-z_$][\w$]*)$""",
    )
    private val previousWord = Regex("""[A-Za-z_$][\w$]*$""")
    private val nonDefinitionWords = setOf(
        "if",
        "for",
        "while",
        "switch",
        "catch",
        "when",
        "with",
        "try",
        "do",
        "else",
        "case",
        "return",
        "throw",
        "new",
        "await",
        "yield",
        "assert",
        "loop",
        "match",
        "synchronized",
    )
    private val scriptOpen = Regex("""<script\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val scriptClose = Regex("""</script\s*>""", RegexOption.IGNORE_CASE)

    fun parse(source: String, languageToken: String?): CodeOutline {
        if (source.isBlank()) return CodeOutline.EMPTY
        val lineStarts = lineStarts(source)
        val token = languageToken
            ?.trim()
            ?.lowercase()
            ?.substringBefore(' ')
            ?.substringBefore('{')
            .orEmpty()
        val definitions = when (token) {
            "python" -> parseIndented(source, lineStarts, pythonDefinition)
            "ruby" -> parseIndented(source, lineStarts, rubyDefinition)
            "vue" -> parseVueScripts(source, lineStarts)
            "java",
            "kotlin",
            "javascript",
            "typescript",
            "c",
            "cpp",
            "go",
            "rust",
            "swift",
            "php",
            -> parseBraceLanguage(
                source = source,
                lineStarts = lineStarts,
                allowImplicitBraceMethods = token in setOf(
                    "java",
                    "javascript",
                    "typescript",
                    "c",
                    "cpp",
                ),
            )
            else -> emptyList()
        }
        return CodeOutline(
            definitions = assignDepth(definitions),
            lineStarts = lineStarts,
        )
    }

    private fun parseBraceLanguage(
        source: String,
        lineStarts: IntArray,
        allowImplicitBraceMethods: Boolean,
    ): List<CodeDefinition> {
        if (source.isBlank()) return emptyList()
        val scan = scanLexical(source, 0, source.length, hashComments = false)
        val definitions = ArrayList<CodeDefinition>()
        for (lineIndex in lineStarts.indices) {
            val lineStart = lineStarts[lineIndex]
            val lineEnd = lineEndExclusive(source, lineStarts, lineIndex)
            if (lineStart >= lineEnd) continue
            val original = source.substring(lineStart, lineEnd).trimEnd('\r')
            val masked = maskedLine(scan.masked, source, lineStart, lineEnd).trim()
            if (masked.isEmpty()) continue

            val isDefinition = structuralDeclaration.containsMatchIn(masked) ||
                functionDeclaration.containsMatchIn(masked) ||
                arrowFunctionDeclaration.containsMatchIn(masked) ||
                (allowImplicitBraceMethods && looksLikeBraceMethod(masked))
            if (!isDefinition) continue

            val opening = findOpeningBrace(
                source = source,
                masked = scan.masked,
                lineStarts = lineStarts,
                lineIndex = lineIndex,
                braceOpenings = scan.braceOpenings,
            ) ?: continue
            val closing = scan.bracePairs[opening] ?: (source.length - 1).coerceAtLeast(opening)
            if (closing < opening) continue
            val endLine = lineAtOffset(lineStarts, source.length, closing)
            definitions += CodeDefinition(
                startLine = lineIndex + 1,
                endLine = endLine.coerceAtLeast(lineIndex + 1),
                indent = indentWidth(original),
                text = original.trim(),
            )
        }
        return definitions
    }

    private fun parseVueScripts(
        source: String,
        lineStarts: IntArray,
    ): List<CodeDefinition> {
        val result = ArrayList<CodeDefinition>()
        var searchFrom = 0
        while (searchFrom < source.length) {
            val open = scriptOpen.find(source, searchFrom) ?: break
            val bodyStart = open.range.last + 1
            val close = scriptClose.find(source, bodyStart)
            val bodyEnd = close?.range?.first ?: source.length
            if (bodyEnd > bodyStart) {
                val body = source.substring(bodyStart, bodyEnd)
                val baseLine = lineAtOffset(lineStarts, source.length, bodyStart) - 1
                parseBraceLanguage(
                    source = body,
                    lineStarts = lineStarts(body),
                    allowImplicitBraceMethods = true,
                ).forEach { definition ->
                    result += definition.copy(
                        startLine = definition.startLine + baseLine,
                        endLine = definition.endLine + baseLine,
                    )
                }
            }
            searchFrom = close?.range?.last?.plus(1) ?: source.length
        }
        return result
    }

    private fun parseIndented(
        source: String,
        lineStarts: IntArray,
        definition: Regex,
    ): List<CodeDefinition> {
        if (source.isBlank()) return emptyList()
        val scan = scanLexical(source, 0, source.length, hashComments = true)
        val definitions = ArrayList<CodeDefinition>()
        for (lineIndex in lineStarts.indices) {
            val lineStart = lineStarts[lineIndex]
            val lineEnd = lineEndExclusive(source, lineStarts, lineIndex)
            if (lineStart >= lineEnd) continue
            val original = source.substring(lineStart, lineEnd).trimEnd('\r')
            val masked = maskedLine(scan.masked, source, lineStart, lineEnd)
            if (!definition.containsMatchIn(masked)) continue

            val startIndent = indentWidth(original)
            var endLine = lineStarts.size
            for (next in lineIndex + 1 until lineStarts.size) {
                val nextStart = lineStarts[next]
                val nextEnd = lineEndExclusive(source, lineStarts, next)
                val nextMasked = maskedLine(scan.masked, source, nextStart, nextEnd).trim()
                if (nextMasked.isEmpty()) continue
                val nextOriginal = source.substring(nextStart, nextEnd)
                if (indentWidth(nextOriginal) <= startIndent) {
                    endLine = next
                    break
                }
            }
            definitions += CodeDefinition(
                startLine = lineIndex + 1,
                endLine = endLine.coerceAtLeast(lineIndex + 1),
                indent = startIndent,
                text = original.trim(),
            )
        }
        return definitions
    }

    private fun looksLikeBraceMethod(maskedLine: String): Boolean {
        val code = maskedLine.trim()
        if (!code.contains('(')) return false
        val parameterStart = code.indexOf('(')
        if (parameterStart <= 0) return false
        val beforeParameters = code.substring(0, parameterStart).trimEnd()
        val name = methodName.find(beforeParameters)?.value ?: return false
        val simpleName = name.substringAfterLast("::").removePrefix("~")
        if (simpleName in nonDefinitionWords) return false

        val prefix = beforeParameters.removeSuffix(name).trimEnd()
        if (prefix.endsWith("=")) return false
        val word = previousWord.find(prefix)?.value
        if (word in nonDefinitionWords) return false
        return true
    }

    private fun findOpeningBrace(
        source: String,
        masked: BooleanArray,
        lineStarts: IntArray,
        lineIndex: Int,
        braceOpenings: IntArray,
    ): Int? {
        val lineStart = lineStarts[lineIndex]
        val lineEnd = lineEndExclusive(source, lineStarts, lineIndex)
        firstBraceInRange(braceOpenings, lineStart, lineEnd)?.let { return it }

        val lineCode = maskedLine(masked, source, lineStart, lineEnd)
        if (lineCode.contains(';')) return null
        val lastLookahead = minOf(lineIndex + MAX_SIGNATURE_LOOKAHEAD_LINES, lineStarts.lastIndex)
        for (next in lineIndex + 1..lastLookahead) {
            val nextStart = lineStarts[next]
            val nextEnd = lineEndExclusive(source, lineStarts, next)
            val nextCode = maskedLine(masked, source, nextStart, nextEnd)
            val brace = firstBraceInRange(braceOpenings, nextStart, nextEnd)
            val semicolon = nextCode.indexOf(';')
            if (brace != null && (semicolon < 0 || brace - nextStart < semicolon)) {
                return brace
            }
            if (semicolon >= 0) return null
        }
        return null
    }

    private fun firstBraceInRange(
        braceOpenings: IntArray,
        fromInclusive: Int,
        toExclusive: Int,
    ): Int? {
        if (braceOpenings.isEmpty() || toExclusive <= fromInclusive) return null
        var low = 0
        var high = braceOpenings.lastIndex
        var found = braceOpenings.size
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (braceOpenings[mid] < fromInclusive) {
                low = mid + 1
            } else {
                found = mid
                high = mid - 1
            }
        }
        return braceOpenings.getOrNull(found)?.takeIf { it < toExclusive }
    }

    private fun assignDepth(definitions: List<CodeDefinition>): List<CodeDefinition> {
        if (definitions.size <= 1) return definitions
        val sorted = definitions.sortedWith(
            compareBy<CodeDefinition> { it.startLine }.thenByDescending { it.endLine },
        )
        return sorted.mapIndexed { index, definition ->
            val depth = sorted.take(index).count { outer ->
                outer.startLine <= definition.startLine &&
                    outer.endLine >= definition.endLine &&
                    (outer.startLine < definition.startLine || outer.endLine > definition.endLine)
            }
            definition.copy(depth = depth)
        }
    }

    private fun maskedLine(
        masked: BooleanArray,
        source: String,
        start: Int,
        end: Int,
    ): String {
        val builder = StringBuilder((end - start).coerceAtLeast(0))
        for (index in start until end) {
            builder.append(if (masked.getOrElse(index) { false }) source[index] else ' ')
        }
        return builder.toString()
    }

    private fun indentWidth(line: String): Int {
        var width = 0
        for (char in line) {
            when (char) {
                ' ' -> width++
                '\t' -> width += 4
                else -> return width
            }
        }
        return width
    }

    private fun lineEndExclusive(source: String, lineStarts: IntArray, lineIndex: Int): Int {
        val nextStart = lineStarts.getOrNull(lineIndex + 1) ?: source.length
        return if (nextStart > 0 && source.getOrNull(nextStart - 1) == '\n') {
            (nextStart - 1).coerceAtLeast(lineStarts[lineIndex])
        } else {
            nextStart
        }
    }

    private fun lineAtOffset(lineStarts: IntArray, sourceLength: Int, offset: Int): Int {
        if (lineStarts.isEmpty()) return 1
        val safe = offset.coerceIn(0, (sourceLength - 1).coerceAtLeast(0))
        var low = 0
        var high = lineStarts.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lineStarts[mid] <= safe) low = mid + 1 else high = mid - 1
        }
        return (high + 1).coerceAtLeast(1)
    }

    private fun lineStarts(source: String): IntArray {
        if (source.isEmpty()) return intArrayOf(0)
        val starts = ArrayList<Int>()
        starts += 0
        for (index in source.indices) {
            if (source[index] == '\n' && index + 1 < source.length) {
                starts += index + 1
            }
        }
        return starts.toIntArray()
    }

    private data class LexicalScan(
        val masked: BooleanArray,
        val braceOpenings: IntArray,
        val bracePairs: Map<Int, Int>,
    )

    private enum class LexicalState {
        NORMAL,
        LINE_COMMENT,
        BLOCK_COMMENT,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        BACKTICK,
        TRIPLE_SINGLE_QUOTE,
        TRIPLE_DOUBLE_QUOTE,
    }

    private fun scanLexical(
        source: String,
        start: Int,
        end: Int,
        hashComments: Boolean,
    ): LexicalScan {
        val masked = BooleanArray(source.length)
        val openings = ArrayList<Int>()
        val pairs = HashMap<Int, Int>()
        val stack = ArrayDeque<Int>()
        var state = LexicalState.NORMAL
        var index = start.coerceAtLeast(0)
        val safeEnd = end.coerceAtMost(source.length)
        while (index < safeEnd) {
            val char = source[index]
            val next = source.getOrNull(index + 1)
            when (state) {
                LexicalState.NORMAL -> {
                    when {
                        char == '/' && next == '/' -> {
                            state = LexicalState.LINE_COMMENT
                            index += 2
                            continue
                        }
                        char == '/' && next == '*' -> {
                            state = LexicalState.BLOCK_COMMENT
                            index += 2
                            continue
                        }
                        hashComments && char == '#' -> {
                            state = LexicalState.LINE_COMMENT
                            index++
                            continue
                        }
                        source.startsWith("\"\"\"", index) -> {
                            state = LexicalState.TRIPLE_DOUBLE_QUOTE
                            index += 3
                            continue
                        }
                        source.startsWith("'''", index) -> {
                            state = LexicalState.TRIPLE_SINGLE_QUOTE
                            index += 3
                            continue
                        }
                        char == '"' -> {
                            state = LexicalState.DOUBLE_QUOTE
                            index++
                            continue
                        }
                        char == '\'' -> {
                            state = LexicalState.SINGLE_QUOTE
                            index++
                            continue
                        }
                        char == '`' -> {
                            state = LexicalState.BACKTICK
                            index++
                            continue
                        }
                        char == '{' -> {
                            masked[index] = true
                            openings += index
                            stack.addLast(index)
                            index++
                            continue
                        }
                        char == '}' -> {
                            masked[index] = true
                            stack.removeLastOrNull()?.let { opening ->
                                pairs[opening] = index
                            }
                            index++
                            continue
                        }
                        else -> masked[index] = true
                    }
                }
                LexicalState.LINE_COMMENT -> {
                    if (char == '\n' || char == '\r') {
                        masked[index] = true
                        state = LexicalState.NORMAL
                    }
                }
                LexicalState.BLOCK_COMMENT -> {
                    if (char == '*' && next == '/') {
                        index += 2
                        state = LexicalState.NORMAL
                        continue
                    }
                }
                LexicalState.SINGLE_QUOTE -> {
                    if (char == '\\') {
                        index += 2
                        continue
                    }
                    if (char == '\'' || char == '\n' || char == '\r') {
                        if (char == '\n' || char == '\r') masked[index] = true
                        state = LexicalState.NORMAL
                    }
                }
                LexicalState.DOUBLE_QUOTE -> {
                    if (char == '\\') {
                        index += 2
                        continue
                    }
                    if (char == '"' || char == '\n' || char == '\r') {
                        if (char == '\n' || char == '\r') masked[index] = true
                        state = LexicalState.NORMAL
                    }
                }
                LexicalState.BACKTICK -> {
                    if (char == '\\') {
                        index += 2
                        continue
                    }
                    if (char == '`') state = LexicalState.NORMAL
                }
                LexicalState.TRIPLE_SINGLE_QUOTE -> {
                    if (char == '\\') {
                        index += 2
                        continue
                    }
                    if (source.startsWith("'''", index)) {
                        index += 3
                        state = LexicalState.NORMAL
                        continue
                    }
                }
                LexicalState.TRIPLE_DOUBLE_QUOTE -> {
                    if (char == '\\') {
                        index += 2
                        continue
                    }
                    if (source.startsWith("\"\"\"", index)) {
                        index += 3
                        state = LexicalState.NORMAL
                        continue
                    }
                }
            }
            index++
        }
        for (opening in stack) {
            pairs[opening] = (safeEnd - 1).coerceAtLeast(opening)
        }
        return LexicalScan(
            masked = masked,
            braceOpenings = openings.toIntArray(),
            bracePairs = pairs,
        )
    }
}
