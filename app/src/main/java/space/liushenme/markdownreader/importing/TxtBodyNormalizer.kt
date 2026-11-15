package space.liushenme.markdownreader.importing

/**
 * 纯文本正文规范化。
 *
 * 很多网文在章首会**重复输出一次章节标题**：先一行无缩进标题，紧接着再来一行带缩进的同名标题。
 * 阅读时看起来就是标题连了两遍。这里在导入（或首次打开旧书）时把「紧接着重复的同一标题行」
 * 合并成一条，只保留后面那条（正文真正开始处）。
 */
object TxtBodyNormalizer {

    /** 判定「紧接着重复」的最大行窗口（中间允许空行）。 */
    private const val DUPLICATE_LINE_WINDOW = 4

    /**
     * 统一换行符为 `\n`。
     *
     * Windows 风格的 `\r\n` 会让排版把 `\r` 当成额外换行（行数翻倍、每页容量测偏小），
     * 也会让「空行分页/段落」判断失效，因此导入与旧书升级时统一规范化。
     */
    fun normalizeLineEndings(text: String): String =
        if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')

    fun dedupeRepeatedHeadingLines(text: String): String {
        if (text.isEmpty()) return text
        val lines = text.split('\n')
        if (lines.size < 2) return text
        val keep = BooleanArray(lines.size) { true }
        var i = 0
        while (i < lines.size) {
            val current = normalize(lines[i])
            if (current.isEmpty() || !looksLikeHeading(current)) {
                i++
                continue
            }
            // 向后找最近的「非空、非标题」行之前，是否出现同一条标题
            var j = i + 1
            var scanned = 0
            var duplicateAt = -1
            while (j < lines.size && scanned <= DUPLICATE_LINE_WINDOW) {
                val next = normalize(lines[j])
                if (next.isEmpty()) {
                    j++
                    continue
                }
                if (next == current) {
                    duplicateAt = j
                    break
                }
                if (looksLikeHeading(next)) break
                break
            }
            if (duplicateAt > 0) {
                // 删除较早那条（保留真正章节标题）
                keep[i] = false
                i = duplicateAt + 1
                continue
            }
            i++
        }
        val sb = StringBuilder(text.length)
        for (index in lines.indices) {
            if (!keep[index]) continue
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(lines[index])
        }
        return sb.toString()
    }

    private fun normalize(line: String): String =
        line.filterNot { it.isWhitespace() || it == '　' }

    private fun looksLikeHeading(compactLine: String): Boolean {
        if (compactLine.length > 40) return false
        return compactLine.startsWith("第") ||
            compactLine.startsWith("序章") ||
            compactLine.startsWith("楔子") ||
            compactLine.startsWith("番外") ||
            compactLine.startsWith("Chapter", ignoreCase = true)
    }
}
