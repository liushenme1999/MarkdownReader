package space.liushenme.markdownreader.markdown

/** 把源码原文包装为 Markwon 代码块；仅用于渲染，不修改落盘正文。 */
object ReaderCodeBlockMarkdown {

    fun wrap(source: String, languageToken: String?): String {
        val maxRun = Regex("`+").findAll(source).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat((maxRun + 1).coerceAtLeast(3))
        val language = languageToken
            ?.trim()
            ?.substringBefore(' ')
            ?.substringBefore('{')
            ?.filterNot { it == '`' || it == '\n' || it == '\r' }
            .orEmpty()
        val opening = fence + language
        return buildString(source.length + fence.length * 2 + 8) {
            append(opening)
            append('\n')
            append(source)
            if (source.isNotEmpty() && !source.endsWith('\n')) append('\n')
            append(fence)
        }
    }
}
