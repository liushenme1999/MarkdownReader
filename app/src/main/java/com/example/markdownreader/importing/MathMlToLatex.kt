package com.example.markdownreader.importing

/**
 * 把 [HtmlToMarkdownConverter.MathFrame] 表示的 MathML 子树渲染成 LaTeX 字符串。
 *
 * 支持的元素：
 * - `mn` / `mi` / `mtext` —— 直接输出
 * - `mo` —— 运算符；常见 Unicode 字符（如 ≤、≥、×、÷、∑、∫）映射到 LaTeX 命令
 * - `mrow` / `mstyle` / `mpadded` / `mphantom` —— 透明容器
 * - `mfrac` —— `\frac{}{}`
 * - `msqrt` —— `\sqrt{}`
 * - `mroot` —— `\sqrt[n]{x}`
 * - `msup` / `msub` / `msubsup` —— 上下标
 * - `munder` / `mover` / `munderover` —— underset / overset / 上下标
 * - `mfenced` —— 用 `\left( … \right)` 包裹，分隔符 `,` 可由 `separators` 属性覆盖
 *
 * 不识别的元素会按 `mrow` 一样把子节点串起来作为容错。复杂场景（mtable / 矩阵）
 * 暂不支持，会被打平成空白分隔的文本，肉眼仍可读。
 */
internal object MathMlToLatex {

    fun convert(root: HtmlToMarkdownConverter.MathFrame): String =
        render(root).trim()

    private fun render(f: HtmlToMarkdownConverter.MathFrame): String {
        val name = f.name.lowercase().substringAfter(':')
        return when (name) {
            "mn", "mi" -> renderTextLeaf(f, italic = name == "mi" && f.text.toString().trim().length == 1)
            "mtext" -> renderTextLeaf(f, italic = false)
            "ms" -> "\\text{${renderTextLeaf(f, italic = false)}}"
            "mo" -> renderOperator(f)
            "math", "mrow", "mstyle", "mpadded", "mphantom" ->
                f.children.joinToString(" ") { render(it) }
            "mfrac" -> {
                val (a, b) = pickTwo(f)
                "\\frac{${a}}{${b}}"
            }
            "msqrt" -> "\\sqrt{${f.children.joinToString(" ") { render(it) }}}"
            "mroot" -> {
                val (x, n) = pickTwo(f)
                "\\sqrt[$n]{$x}"
            }
            "msup" -> {
                val (b, s) = pickTwo(f)
                "${wrapAtom(b)}^{${s}}"
            }
            "msub" -> {
                val (b, s) = pickTwo(f)
                "${wrapAtom(b)}_{${s}}"
            }
            "msubsup" -> {
                val (b, sub, sup) = pickThree(f)
                "${wrapAtom(b)}_{${sub}}^{${sup}}"
            }
            "munder" -> {
                val (b, u) = pickTwo(f)
                "\\underset{${u}}{${b}}"
            }
            "mover" -> {
                val (b, o) = pickTwo(f)
                "\\overset{${o}}{${b}}"
            }
            "munderover" -> {
                val (b, u, o) = pickThree(f)
                "${wrapAtom(b)}_{${u}}^{${o}}"
            }
            "mfenced" -> {
                val open = f.attrs["open"] ?: "("
                val close = f.attrs["close"] ?: ")"
                val sep = f.attrs["separators"]?.firstOrNull()?.toString() ?: ","
                val body = f.children.joinToString("$sep ") { render(it) }
                "\\left${escapeFence(open)} $body \\right${escapeFence(close)}"
            }
            else -> f.children.joinToString(" ") { render(it) }
        }
    }

    private fun renderTextLeaf(f: HtmlToMarkdownConverter.MathFrame, italic: Boolean): String {
        val t = f.text.toString().trim()
        if (t.isEmpty()) return ""
        // 单个变量字母 MathML 默认是斜体，jlatexmath 默认也是斜体，所以直接输出
        return if (italic) t else if (containsAlphabetic(t) && t.length > 1) "\\mathrm{${escapeText(t)}}" else escapeText(t)
    }

    private fun renderOperator(f: HtmlToMarkdownConverter.MathFrame): String {
        val raw = f.text.toString().trim()
        if (raw.isEmpty()) return ""
        return raw.codePoints().toArray()
            .joinToString("") { mapOperatorCodePoint(it) }
    }

    /**
     * 取 [f] 的前两个子节点的 LaTeX；不足时用占位的空 `{}` 兜底，保证语法合法。
     */
    private fun pickTwo(f: HtmlToMarkdownConverter.MathFrame): Pair<String, String> {
        val a = f.children.getOrNull(0)?.let { render(it) } ?: ""
        val b = f.children.getOrNull(1)?.let { render(it) } ?: ""
        return a to b
    }

    private fun pickThree(f: HtmlToMarkdownConverter.MathFrame): Triple<String, String, String> {
        val a = f.children.getOrNull(0)?.let { render(it) } ?: ""
        val b = f.children.getOrNull(1)?.let { render(it) } ?: ""
        val c = f.children.getOrNull(2)?.let { render(it) } ?: ""
        return Triple(a, b, c)
    }

    /** 上下标 base 若已含运算符 / 空格，加 `{}` 防止结合错误。 */
    private fun wrapAtom(s: String): String {
        if (s.isEmpty()) return "{}"
        if (s.length == 1) return s
        return "{$s}"
    }

    private fun containsAlphabetic(s: String): Boolean = s.any { it.isLetter() }

    /** 把 `{` `}` `\` `_` `^` `$` `#` `&` `%` `~` 等 LaTeX 元字符转义。 */
    private fun escapeText(s: String): String {
        val sb = StringBuilder(s.length + 4)
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\textbackslash{}")
                '{' -> sb.append("\\{")
                '}' -> sb.append("\\}")
                '_' -> sb.append("\\_")
                '^' -> sb.append("\\^{}")
                '$' -> sb.append("\\$")
                '#' -> sb.append("\\#")
                '&' -> sb.append("\\&")
                '%' -> sb.append("\\%")
                '~' -> sb.append("\\~{}")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun escapeFence(s: String): String = when (s) {
        "(", ")" -> s
        "[" -> "["
        "]" -> "]"
        "{" -> "\\{"
        "}" -> "\\}"
        "|" -> "|"
        "" -> "."
        else -> "."
    }

    /** 把 MathML / Unicode 运算符映射成 LaTeX。未识别的字符原样输出（避免破坏可读性）。 */
    private fun mapOperatorCodePoint(cp: Int): String {
        // 常用 ASCII 直接放回
        if (cp in 0x20..0x7e) {
            val c = cp.toChar()
            return when (c) {
                '<' -> "<"
                '>' -> ">"
                '&' -> "\\&"
                '$' -> "\\$"
                '#' -> "\\#"
                '%' -> "\\%"
                '_' -> "\\_"
                '{' -> "\\{"
                '}' -> "\\}"
                else -> c.toString()
            }
        }
        return UNICODE_OP[cp] ?: String(Character.toChars(cp))
    }

    private val UNICODE_OP = mapOf(
        0x00B1 to "\\pm",      // ±
        0x2213 to "\\mp",      // ∓
        0x00D7 to "\\times",   // ×
        0x00F7 to "\\div",     // ÷
        0x2217 to "\\ast",     // ∗
        0x22C5 to "\\cdot",    // ⋅
        0x2218 to "\\circ",    // ∘
        0x2260 to "\\neq",     // ≠
        0x2264 to "\\leq",     // ≤
        0x2265 to "\\geq",     // ≥
        0x2248 to "\\approx",  // ≈
        0x2261 to "\\equiv",   // ≡
        0x2200 to "\\forall",  // ∀
        0x2203 to "\\exists",  // ∃
        0x2205 to "\\emptyset",// ∅
        0x2208 to "\\in",      // ∈
        0x2209 to "\\notin",   // ∉
        0x2282 to "\\subset",  // ⊂
        0x2286 to "\\subseteq",// ⊆
        0x222A to "\\cup",     // ∪
        0x2229 to "\\cap",     // ∩
        0x2192 to "\\rightarrow", // →
        0x21D2 to "\\Rightarrow", // ⇒
        0x21D4 to "\\Leftrightarrow", // ⇔
        0x221E to "\\infty",   // ∞
        0x2202 to "\\partial", // ∂
        0x2207 to "\\nabla",   // ∇
        0x2211 to "\\sum",     // ∑
        0x220F to "\\prod",    // ∏
        0x222B to "\\int",     // ∫
        0x221A to "\\sqrt{}",  // √（独立出现时；通常应用 msqrt）
        0x03B1 to "\\alpha", 0x03B2 to "\\beta", 0x03B3 to "\\gamma",
        0x03B4 to "\\delta", 0x03B5 to "\\epsilon", 0x03B6 to "\\zeta",
        0x03B7 to "\\eta", 0x03B8 to "\\theta", 0x03B9 to "\\iota",
        0x03BA to "\\kappa", 0x03BB to "\\lambda", 0x03BC to "\\mu",
        0x03BD to "\\nu", 0x03BE to "\\xi", 0x03BF to "o",
        0x03C0 to "\\pi", 0x03C1 to "\\rho", 0x03C3 to "\\sigma",
        0x03C4 to "\\tau", 0x03C5 to "\\upsilon", 0x03C6 to "\\phi",
        0x03C7 to "\\chi", 0x03C8 to "\\psi", 0x03C9 to "\\omega",
        0x0393 to "\\Gamma", 0x0394 to "\\Delta", 0x0398 to "\\Theta",
        0x039B to "\\Lambda", 0x039E to "\\Xi", 0x03A0 to "\\Pi",
        0x03A3 to "\\Sigma", 0x03A6 to "\\Phi", 0x03A8 to "\\Psi",
        0x03A9 to "\\Omega"
    )
}
