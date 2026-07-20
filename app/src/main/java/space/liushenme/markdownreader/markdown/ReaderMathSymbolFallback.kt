package space.liushenme.markdownreader.markdown

/** JLatex 渲染不稳定时，改用 [ReaderMathSymbolSpan] / [ReaderCompoundInlineLatexSpan] + Noto 字体。 */
internal object ReaderMathSymbolFallback {

    private val commandToSymbol: Map<String, Char> = mapOf(
        "otimes" to '\u2297',
        "odot" to '\u2299',
        "oplus" to '\u2295',
        "ominus" to '\u2296',
        "oslash" to '\u2298',
    )

    private val latexToSymbol: Map<String, Char> = buildMap {
        commandToSymbol.forEach { (cmd, ch) ->
            put("\\$cmd", ch)
            put("{\\$cmd}", ch)
        }
    }

    private val symbolCommandPattern =
        Regex("""\\(otimes|odot|oplus|ominus|oslash)(?![A-Za-z])""")

    sealed interface CompoundSegment {
        data class Jlatex(val latex: String) : CompoundSegment
        data class Symbol(val char: Char) : CompoundSegment
    }

    fun symbolFor(latex: String): Char? = latexToSymbol[latex.trim()]

    /** 含 `\\otimes` 等命令的复合行内公式；纯命令由 [symbolFor] 处理。 */
    fun parseCompound(latex: String): List<CompoundSegment>? {
        if (!symbolCommandPattern.containsMatchIn(latex)) return null
        val segments = mutableListOf<CompoundSegment>()
        var cursor = 0
        for (match in symbolCommandPattern.findAll(latex)) {
            if (match.range.first > cursor) {
                segments += CompoundSegment.Jlatex(latex.substring(cursor, match.range.first))
            }
            val symbol = commandToSymbol[match.groupValues[1]] ?: continue
            segments += CompoundSegment.Symbol(symbol)
            cursor = match.range.last + 1
        }
        if (cursor < latex.length) {
            segments += CompoundSegment.Jlatex(latex.substring(cursor))
        }
        if (segments.none { it is CompoundSegment.Symbol }) return null
        if (segments.size == 1 && segments[0] is CompoundSegment.Symbol) return null
        return segments
    }
}
