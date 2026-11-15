package space.liushenme.markdownreader.importing

/**
 * 从纯文本（如 .txt 小说）解析目录。
 *
 * 借鉴 Legado 的本地 TXT 做法（`TextFile.getTocRule / analyze`）：
 * 1. 内置一组常见章节标题正则（容忍「第 515 章」这类中间空格、全角空格、正文前缀等写法）；
 * 2. 在多行模式下逐条试匹配，选**命中最多**的那条规则作为本书目录规则；
 * 3. 用选中的规则对全文匹配，取每个匹配行首作为章节起点（[ImportedTocEntry.sourceOffset]）。
 *
 * 与旧实现的区别：不再要求「整行严格等于第X章…」，因此
 * `第 515 章 标题`、`正文 第515章`、`　　第515章`、`卷一` 等都能命中。
 */
object PlainTextTocParser {

    private val CN_NUM = "0-9０-９〇零一二两三四五六七八九十百千万亿壹贰叁肆伍陆柒捌玖拾佰仟"

    /**
     * 目录候选规则，按「越具体越靠前」排列。
     * 与 Legado 的内置规则同构，只是裁剪成语义等价的最小集合。
     */
    private val CANDIDATE_RULES: List<Regex> = listOf(
        // 第X章/节/回/卷 标题（允许数字与「章」之间 0~4 个空白，含全角空格）
        Regex(
            "^[\\s　]{0,6}第[\\s　]{0,4}[$CN_NUM]+[\\s　]{0,4}(?:章|节|回|卷|集|部|篇).*$",
            setOf(RegexOption.MULTILINE),
        ),
        // 序章/楔子/番外等特殊标题
        Regex(
            "^[\\s　]{0,6}(?:序章|序言|楔子|引子|前言|后记|尾声|跋|完结感言|番外[^\\n]{0,20}|[上下中]卷[^\\n]{0,20}).*$",
            setOf(RegexOption.MULTILINE),
        ),
        // 数字 + 分隔符 + 标题：1. 标题 / 12、标题 / 3-标题
        Regex(
            "^[\\s　]{0,6}\\d{1,5}[.、,，:：\\-_—][^\\n]{0,40}$",
            setOf(RegexOption.MULTILINE),
        ),
        // 纯数字标题行：只有编号
        Regex("^[\\s　]{0,6}\\d{1,5}[.、]?[\\s　]{0,4}$", setOf(RegexOption.MULTILINE)),
        // 中文数字 + 分隔符 + 标题：一、标题 / 一 标题
        Regex(
            "^[\\s　]{0,6}[$CN_NUM]{1,12}[.、\\-_—\\s　][^\\n，。！？；]{0,30}$",
            setOf(RegexOption.MULTILINE),
        ),
        // 正文 标题
        Regex("^[\\s　]{0,6}正文[\\s　]{1,4}[^\\n]{0,30}$", setOf(RegexOption.MULTILINE)),
        // Chapter / Part / Episode
        Regex(
            "(?i)^[\\s　]{0,6}(?:chapter|section|part|episode|no\\.?)[\\s　]{0,4}[0-9ivxlc]+[^\\n]{0,40}$",
            setOf(RegexOption.MULTILINE),
        ),
        // 【第X章】/〔第X章〕等带括号写法
        Regex(
            "^[\\s　]{0,6}[【〔〖「『〈［\\[][^\\n]{0,30}(?:第[\\s　]{0,4}[$CN_NUM]+[\\s　]{0,4}[章章节回]|[Cc]hapter)[^\\n]{0,40}$",
            setOf(RegexOption.MULTILINE),
        ),
    )

    /** 行内旧式单行判定（保留给阅读页兜底与测试）。 */
    private val RE_LINE_FALLBACK = Regex(
        "^第[\\s　]{0,4}[$CN_NUM]+[\\s　]{0,4}(?:章|节|回|卷|集|部|篇).*$",
    )

    /** 原文里「先列一行标题、紧接着再出现真正标题」的判重窗口（字符）。 */
    private const val DUPLICATE_TITLE_WINDOW = 600

    /** 两个章节标题之间的最小距离（字符），用于过滤目录页里的密集假标题。 */
    private const val MIN_TITLE_GAP = 300

    /** 选规则时只扫描开头这么多字符（大文件全文匹配代价高）。 */
    private const val RULE_SAMPLE_CHARS = 400_000

    fun parse(text: String): List<ImportedTocEntry> {
        if (text.isEmpty()) return emptyList()
        val rule = chooseRule(text)
        if (rule != null) {
            val entries = collectMatches(text, rule)
            if (entries.isNotEmpty()) return entries
        }
        return parseLineByLine(text)
    }

    /** 按命中数量挑规则；只在前 400K 字符上试，避免大文件上反复全文匹配。 */
    private fun chooseRule(text: String): Regex? {
        val sample = if (text.length > RULE_SAMPLE_CHARS) {
            text.substring(0, RULE_SAMPLE_CHARS)
        } else {
            text
        }
        var best: Regex? = null
        var bestCount = 0
        for (rule in CANDIDATE_RULES) {
            val count = countDistinctMatches(sample, rule)
            if (count > bestCount) {
                bestCount = count
                best = rule
            }
        }
        return best?.takeIf { bestCount > 0 }
    }

    private fun countDistinctMatches(sample: String, rule: Regex): Int {
        var count = 0
        var lastStart = -MIN_TITLE_GAP - 1
        for (match in rule.findAll(sample)) {
            if (match.range.first - lastStart >= MIN_TITLE_GAP || lastStart < 0) {
                count++
                lastStart = match.range.first
            }
        }
        return count
    }

    /**
     * 收章节标题。
     *
     * 不少 txt（尤其网文）会在章首**重复一次标题**：先一条「第4章 标题」，
     * 隔 20~30 字又出现真正的章节标题（常带缩进）。这里对「间隔很近且标题相同」的
     * 相邻命中做合并，保留**后面**那条（真正开始正文的标题），避免目录出现重复项。
     */
    private fun collectMatches(text: String, rule: Regex): List<ImportedTocEntry> {
        data class Hit(val start: Int, val title: String)

        val hits = ArrayList<Hit>()
        for (match in rule.findAll(text)) {
            val start = match.range.first
            val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            val title = text.substring(start, lineEnd).trim()
                .trim('　', ' ', '\t')
                .takeIf { it.isNotEmpty() } ?: continue
            hits += Hit(start, title)
        }
        if (hits.isEmpty()) return emptyList()

        val merged = ArrayList<Hit>(hits.size)
        for (hit in hits) {
            val prev = merged.lastOrNull()
            if (prev != null &&
                isSameHeading(prev.title, hit.title) &&
                hit.start - prev.start <= DUPLICATE_TITLE_WINDOW
            ) {
                // 同一标题的重复行：保留后面那条（正文真正开始处）
                merged[merged.lastIndex] = hit
            } else {
                merged += hit
            }
        }

        val out = ArrayList<ImportedTocEntry>(merged.size)
        var lastStart = -MIN_TITLE_GAP - 1
        for (hit in merged) {
            if (lastStart >= 0 && hit.start - lastStart < MIN_TITLE_GAP) continue
            out += ImportedTocEntry(level = 1, title = hit.title, sourceOffset = hit.start)
            lastStart = hit.start
        }
        return out
    }

    /**
     * 判断两条标题是否属于「同一条标题的重复」。
     * 除了完全相同，还覆盖原文截断/省略号差异的情况（如「第300章 这些『土著』深不可测！」
     * 与「第300章 这些『土著』……深不可测！」），用公共前缀占比判断。
     */
    private fun isSameHeading(a: String, b: String): Boolean {
        val na = normalizeTitle(a)
        val nb = normalizeTitle(b)
        if (na == nb) return true
        if (na.isEmpty() || nb.isEmpty()) return false
        val shorter = minOf(na.length, nb.length)
        if (shorter < 6) return false
        var common = 0
        while (common < shorter && na[common] == nb[common]) common++
        return common >= (shorter * 0.75f).toInt()
    }

    /** 归一化标题：忽略空白与常见全角/半角差异，用于判重。 */
    private fun normalizeTitle(title: String): String =
        title.filterNot { it.isWhitespace() || it == '　' }

    /** 旧的行扫描兜底（规则都未命中时）。 */
    private fun parseLineByLine(text: String): List<ImportedTocEntry> {
        val out = ArrayList<ImportedTocEntry>()
        var offset = 0
        while (offset <= text.length) {
            val lineStart = offset
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, lineEnd)
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && levelForLine(trimmed) != null) {
                val titleStart = lineStart + line.indexOf(trimmed).coerceAtLeast(0)
                out += ImportedTocEntry(
                    level = 1,
                    title = trimmed,
                    sourceOffset = titleStart.coerceIn(lineStart, lineEnd),
                )
            }
            if (lineEnd >= text.length) break
            offset = lineEnd + 1
        }
        return out
    }

    internal fun levelForLine(trimmedLine: String): Int? {
        if (CANDIDATE_RULES.any { it.matchEntire(trimmedLine.trimEnd()) != null }) return 1
        if (RE_LINE_FALLBACK.matches(trimmedLine)) return 1
        return null
    }
}
