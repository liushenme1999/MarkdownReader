package space.liushenme.markdownreader.markdown

/**
 * 将 mhchem 风格的 `\ce{…}` 内容改写为 JLatex 可解析的 LaTeX（子集实现）。
 */
internal object CeLatexConverter {

    fun convert(input: String): String = buildCe(input.trim())

    private fun buildCe(source: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < source.length) {
            val arrowStart = findArrowStart(source, index)
            if (arrowStart < 0) {
                out.append(convertSpeciesSide(source.substring(index)))
                break
            }
            if (arrowStart > index) {
                out.append(convertSpeciesSide(source.substring(index, arrowStart)))
            }
            val (arrowLatex, next) = parseArrow(source, arrowStart)
            out.append(' ').append(arrowLatex).append(' ')
            index = next
        }
        return out.toString()
    }

    private fun convertSpeciesSide(side: String): String {
        if (side.isBlank()) return side
        return splitTopLevel(side, '+')
            .joinToString(" + ") { convertSingleSpecies(it.trim()) }
    }

    private fun convertSingleSpecies(species: String): String {
        if (species.isEmpty()) return species
        var body = species.trim()
        var state = ""
        while (true) {
            when {
                body.endsWith(" ^") -> {
                    state = "^{\\uparrow}"
                    body = body.removeSuffix(" ^").trimEnd()
                }
                body.endsWith("^") -> {
                    state = "^{\\uparrow}"
                    body = body.removeSuffix("^").trimEnd()
                }
                body.endsWith(" _") -> {
                    state = "_{\\downarrow}"
                    body = body.removeSuffix(" _").trimEnd()
                }
                body.endsWith("_") -> {
                    state = "_{\\downarrow}"
                    body = body.removeSuffix("_").trimEnd()
                }
                else -> break
            }
        }

        var coefficient = ""
        val leadingDigits = Regex("""^(\d+)""").find(body)
        if (leadingDigits != null) {
            val after = body.getOrNull(leadingDigits.range.last + 1)
            if (after != null && after.isLetter()) {
                coefficient = leadingDigits.value
                body = body.substring(leadingDigits.range.last + 1)
            }
        }
        return coefficient + convertElementFormula(body) + state
    }

    private fun convertElementFormula(source: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < source.length) {
            when (val ch = source[index]) {
                '[', '(' -> {
                    val (inner, next) = readBalanced(source, index)
                    out.append(ch)
                    out.append(convertElementFormula(inner))
                    out.append(closingBracket(ch))
                    index = appendTrailingSubscriptDigits(source, next, out)
                }
                '{' -> {
                    val (inner, next) = readBalanced(source, index)
                    out.append('{').append(inner).append('}')
                    index = next
                }
                '\\' -> {
                    val (command, next) = readControlSequence(source, index)
                    out.append(command)
                    index = next
                }
                '^' -> {
                    val (sup, next) = readSupSub(source, index, '^')
                    out.append(sup)
                    index = next
                }
                '_' -> {
                    val (sub, next) = readSupSub(source, index, '_')
                    out.append(sub)
                    index = next
                }
                else -> {
                    if (ch.isUpperCase()) {
                        out.append(ch)
                        index++
                        if (index < source.length && source[index].isLowerCase()) {
                            out.append(source[index])
                            index++
                        }
                        val digitStart = index
                        while (index < source.length && source[index].isDigit()) {
                            index++
                        }
                        if (index > digitStart) {
                            out.append("_{")
                            out.append(source, digitStart, index)
                            out.append('}')
                        }
                    } else {
                        out.append(ch)
                        index++
                    }
                }
            }
        }
        return out.toString()
    }

    private fun parseArrow(source: String, start: Int): Pair<String, Int> {
        val arrow = when {
            source.startsWith("<=>", start) -> "<=>"
            source.startsWith("<->", start) -> "<->"
            source.startsWith("->", start) -> "->"
            source.startsWith("<-", start) -> "<-"
            source.startsWith("=", start) -> "="
            else -> error("unsupported arrow at $start")
        }
        var index = start + arrow.length
        val conditions = mutableListOf<String>()
        while (index < source.length && source[index] == '[') {
            val (condition, next) = readBracket(source, index)
            conditions += condition
            index = next
        }
        return arrowToLatex(arrow, conditions) to index
    }

    private fun arrowToLatex(arrow: String, conditions: List<String>): String {
        val above = conditions.getOrNull(0)?.let(::formatCondition)
        val below = conditions.getOrNull(1)?.let(::formatCondition)
        return when (arrow) {
            "->" -> when {
                above != null && below != null -> "\\xrightarrow[$below]$above"
                above != null -> "\\xrightarrow$above"
                else -> "\\rightarrow"
            }
            "<-" -> when {
                above != null && below != null -> "\\xleftarrow[$below]$above"
                above != null -> "\\xleftarrow$above"
                else -> "\\leftarrow"
            }
            "<=>" -> when {
                above != null -> "\\stackrel$above{\\rightleftharpoons}"
                else -> "\\rightleftharpoons"
            }
            "<->" -> "\\leftrightarrow"
            "=" -> "="
            else -> arrow
        }
    }

    private fun formatCondition(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.contains('\\')) {
            return "{$trimmed}"
        }
        val body = convertElementFormula(trimmed)
        return "{\\text{$body}}"
    }

    private fun findArrowStart(source: String, from: Int): Int {
        var index = from
        var depth = 0
        while (index < source.length) {
            when (source[index]) {
                '[', '(', '{' -> depth++
                ']', ')', '}' -> if (depth > 0) depth--
                else -> if (depth == 0) {
                    if (source.startsWith("<=>", index) ||
                        source.startsWith("<->", index) ||
                        source.startsWith("->", index) ||
                        source.startsWith("<-", index) ||
                        (source[index] == '=' && isReactionEquals(source, index))
                    ) {
                        return index
                    }
                }
            }
            index++
        }
        return -1
    }

    /** 排除有机分子式中的双键 `=`（如 `CH2=CH2`）。 */
    private fun isReactionEquals(source: String, index: Int): Boolean {
        val prev = source.getOrNull(index - 1)
        val next = source.getOrNull(index + 1)
        if (prev != null && next != null &&
            (prev.isLetterOrDigit() || prev == ')' || prev == ']') &&
            (next.isLetterOrDigit() || next == '(' || next == '[')
        ) {
            return false
        }
        return true
    }

    private fun splitTopLevel(source: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (ch in source) {
            when (ch) {
                '[', '(', '{' -> {
                    depth++
                    current.append(ch)
                }
                ']', ')', '}' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    current.append(ch)
                }
                delimiter -> if (depth == 0) {
                    parts += current.toString()
                    current.clear()
                } else {
                    current.append(ch)
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }

    private fun readBalanced(source: String, openIndex: Int): Pair<String, Int> {
        val open = source[openIndex]
        val close = closingBracket(open)
        var depth = 0
        val start = openIndex + 1
        for (index in openIndex until source.length) {
            when (source[index]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(start, index) to index + 1
                    }
                }
            }
        }
        error("unbalanced group starting at $openIndex")
    }

    private fun readBracket(source: String, openIndex: Int): Pair<String, Int> {
        val (inner, next) = readBalanced(source, openIndex)
        return inner to next
    }

    private fun readSupSub(source: String, index: Int, marker: Char): Pair<String, Int> {
        if (index + 1 < source.length && source[index + 1] == '{') {
            val (inner, next) = readBalanced(source, index + 1)
            return "$marker{$inner}" to next
        }
        if (index + 1 < source.length && source[index + 1] == '\\') {
            val (command, next) = readControlSequence(source, index + 1)
            return "$marker$command" to next
        }
        if (index + 1 >= source.length) {
            return marker.toString() to index + 1
        }
        val next = index + 2
        return "$marker{${source[index + 1]}}" to next
    }

    private fun readControlSequence(source: String, index: Int): Pair<String, Int> {
        if (index >= source.length || source[index] != '\\') {
            return source[index].toString() to index + 1
        }
        var end = index + 1
        while (end < source.length && source[end].isLetter()) {
            end++
        }
        if (end < source.length && source[end] == '{') {
            val (inner, next) = readBalanced(source, end)
            return source.substring(index, end) + "{$inner}" to next
        }
        if (end < source.length && !source[end].isWhitespace()) {
            end++
        }
        return source.substring(index, end) to end
    }

    private fun appendTrailingSubscriptDigits(source: String, index: Int, out: StringBuilder): Int {
        var cursor = index
        val digitStart = cursor
        while (cursor < source.length && source[cursor].isDigit()) {
            cursor++
        }
        if (cursor > digitStart) {
            out.append("_{")
            out.append(source, digitStart, cursor)
            out.append('}')
        }
        return cursor
    }

    private fun closingBracket(open: Char): Char = when (open) {
        '[' -> ']'
        '(' -> ')'
        '{' -> '}'
        else -> error("unsupported bracket $open")
    }
}
