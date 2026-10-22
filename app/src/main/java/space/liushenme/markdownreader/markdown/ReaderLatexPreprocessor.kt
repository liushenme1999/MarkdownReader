package space.liushenme.markdownreader.markdown

/**
 * 将 JLatex 排版差异改写为可解析的 LaTeX（如 `cases` 列间距、mhchem `\ce`、cancel 包）。
 */
object ReaderLatexPreprocessor {

    private val CE_COMMAND = Regex("""\\ce\s*\{""")

    /** JLatex 的 `cases` 会忽略列分隔符后的普通空格，补齐第二列与左值的间距。 */
    private val CASES_ENV = Regex("""\\begin\{cases\}[\s\S]*?\\end\{cases\}""")
    private val CASES_COLUMN = Regex("""&(?!\s*\\quad\b)""")

    /** `\cancel`/`\bcancel`/`\xcancel` → JLatex 内置 `\st`（水平删除线）。 */
    private val CANCEL_COMMANDS = Regex("""\\(?:x|b)?cancel\s*\{""")

    /** JLatex 未内置的积分算子（esint 包）→ 可渲染的等价写法。 */
    private val COMMAND_ALIASES: List<Pair<Regex, String>> = listOf(
        Regex("""\\oiiint(?![A-Za-z])""") to """\\mathop{\\oint\\!\\!\\iiint}""",
        Regex("""\\oiint(?![A-Za-z])""") to """\\mathop{\\oint\\!\\!\\iint}""",
    )

    fun preprocess(latex: String): String {
        if (latex.isEmpty()) return latex
        var out = insertCasesColumnSpacing(latex)
        for ((pattern, replacement) in COMMAND_ALIASES) {
            out = out.replace(pattern, replacement)
        }
        if (CANCEL_COMMANDS.containsMatchIn(out)) {
            out = replaceBalancedCommand(out, CANCEL_COMMANDS) { body ->
                "\\st{$body}"
            }
        }
        if (CE_COMMAND.containsMatchIn(out)) {
            out = replaceBalancedCommand(out, CE_COMMAND) { body ->
                CeLatexConverter.convert(body)
            }
        }
        return out
    }

    /** 改写 `$$…$$` 块内正文（不含定界符）。 */
    internal fun preprocessBlockBody(body: String): String = preprocess(body.trim())

    /**
     * 在 markdown 中预处理所有块级 `$$…$$` 正文。
     * 行内 `$…$` 由 [io.noties.markwon.ext.latex.ReaderInlineLatexAlignPlugin] 处理。
     */
    internal fun preprocessBlockLatexInMarkdown(markdown: String): String {
        if (!markdown.contains("$$")) return markdown
        val lines = insertCasesColumnSpacing(markdown).split('\n')
        val out = ArrayList<String>(lines.size)
        var inBlock = false
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed == "$$" && !inBlock -> {
                    inBlock = true
                    out.add(line)
                }
                trimmed == "$$" && inBlock -> {
                    inBlock = false
                    out.add(line)
                }
                inBlock -> out.add(preprocessBlockBody(line))
                else -> {
                    val single = SINGLE_LINE_BLOCK_LATEX.matchEntire(trimmed)
                    if (single != null) {
                        val body = preprocessBlockBody(single.groupValues[1])
                        out.add("$$")
                        out.add(body)
                        out.add("$$")
                    } else {
                        out.add(line)
                    }
                }
            }
        }
        return out.joinToString("\n")
    }

    private fun insertCasesColumnSpacing(latex: String): String {
        if (!latex.contains("\\begin{cases}")) return latex
        return CASES_ENV.replace(latex) { match ->
            match.value.replace(CASES_COLUMN) { "&" + "\\quad" }
        }
    }

    private val SINGLE_LINE_BLOCK_LATEX = Regex("""^\$\$(.+)\$\$$""")

    private fun replaceBalancedCommand(
        input: String,
        opener: Regex,
        transform: (String) -> String,
    ): String {
        var remaining = input
        val out = StringBuilder()
        while (true) {
            val match = opener.find(remaining) ?: break
            val openBrace = match.range.last
            val extracted = extractBalancedGroup(remaining, openBrace) ?: break
            out.append(remaining, 0, match.range.first)
            out.append(transform(extracted.first))
            remaining = remaining.substring(extracted.second)
        }
        out.append(remaining)
        return out.toString()
    }

    private fun extractBalancedGroup(input: String, openBraceIndex: Int): Pair<String, Int>? {
        if (openBraceIndex >= input.length || input[openBraceIndex] != '{') return null
        var depth = 0
        val start = openBraceIndex + 1
        for (i in openBraceIndex until input.length) {
            when (input[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return input.substring(start, i) to (i + 1)
                    }
                }
            }
        }
        return null
    }
}
