package space.liushenme.markdownreader.markdown

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import space.liushenme.markdownreader.importing.AtxMarkdownTocParser

class MarkdownInlineHtmlTest {

    @Test
    fun stripTags_removesStrongAndKeepsText() {
        val raw = "<strong>1. LLM 八股</strong>"
        assertEquals("1. LLM 八股", MarkdownInlineHtml.stripTags(raw))
    }

    @Test
    fun toAnnotatedString_appliesBoldForStrong() {
        val annotated = MarkdownInlineHtml.toAnnotatedString("<strong>1. LLM 八股</strong>")
        assertEquals("1. LLM 八股", annotated.text)
        val boldRanges = annotated.spanStyles.any { it.item.fontWeight == FontWeight.Bold }
        assertTrue(boldRanges)
    }

    @Test
    fun atxParser_stripsHtmlInTitle_butKeepsRawTitle() {
        val md = """
            ### <strong>1. LLM 八股</strong>

            #### <strong>1.1 请详细解释 Transformer</strong>
        """.trimIndent()
        val toc = AtxMarkdownTocParser.parse(md)
        assertEquals("1. LLM 八股", toc[0].title)
        assertTrue(toc[0].rawTitle.contains("<strong>"))
        assertFalse(toc[0].title.contains("<"))
    }
}
