package space.liushenme.markdownreader.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** 标题/书签预览中的简单 inline HTML（如 `<strong>`）清理与 Compose 样式转换。 */
object MarkdownInlineHtml {

    private val ANY_TAG = Regex("""<[^>]+>""")

    fun stripTags(source: String): String {
        if (!source.contains('<')) return source.trim()
        return decodeEntities(ANY_TAG.replace(source, ""))
            .replace(Regex("""[ \t]+"""), " ")
            .trim()
    }

    fun toAnnotatedString(source: String, base: SpanStyle = SpanStyle()): AnnotatedString {
        if (!source.contains('<')) {
            return AnnotatedString(decodeEntities(source))
        }
        return buildAnnotatedString {
            var bold = false
            var italic = false
            var underline = false
            var index = 0
            while (index < source.length) {
                if (source[index] == '<') {
                    val end = source.indexOf('>', index)
                    if (end < 0) break
                    when (source.substring(index + 1, end).trim().lowercase()) {
                        "strong", "b" -> bold = true
                        "/strong", "/b" -> bold = false
                        "em", "i" -> italic = true
                        "/em", "/i" -> italic = false
                        "u" -> underline = true
                        "/u" -> underline = false
                    }
                    index = end + 1
                    continue
                }
                val nextTag = source.indexOf('<', index).let { if (it < 0) source.length else it }
                val chunk = decodeEntities(source.substring(index, nextTag))
                if (chunk.isNotEmpty()) {
                    pushStyle(
                        SpanStyle(
                            fontWeight = if (bold) FontWeight.Bold else base.fontWeight,
                            fontStyle = if (italic) FontStyle.Italic else base.fontStyle,
                            textDecoration = when {
                                underline -> TextDecoration.Underline
                                else -> base.textDecoration
                            },
                        ),
                    )
                    append(chunk)
                    pop()
                }
                index = nextTag
            }
        }
    }

    private fun decodeEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}
