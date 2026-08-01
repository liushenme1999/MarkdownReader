package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
import android.text.Spanned
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TocJumpEscapedHeadingTest {

    @Test
    fun escapedHashLine_doesNotCreateExtraHeadingSpan() {
        val md = """
            # 十八、转义字符测试
            \#  \*  \-  \`  \[  \]  \_  \!

            # 十九、混合嵌套排版
            - 一级列表项
        """.trimIndent()
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(md, context)
        val rendered = markwon.toMarkdown(prepared.text)
        val headingStarts = markdownHeadingSpanStarts(rendered)
        val lines = headingStarts.map { lineAt(rendered.toString(), it) }

        assertEquals(2, headingStarts.size)
        assertEquals("十八、转义字符测试", lines[0])
        assertEquals("十九、混合嵌套排版", lines[1])
        assertFalse(lines.any { it.startsWith("#") || it.contains("\\") })
    }

    @Test
    fun tocJump_section18to20_inIsolatedSlice() {
        val md = """
            # 十八、转义字符测试
            \#  \*  \-  \`  \[  \]  \_  \!

            # 十九、混合嵌套排版
            - 一级列表项
              1. 混合有序子项一
              2. 混合有序子项二
            - 一级列表第二项
              - 无序列嵌套子项

            # 二十、长文本滚动测试
            大量填充正文内容
        """.trimIndent()
        val toc = parseMarkdownToc(md)
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(md, context)
        val rendered = markwon.toMarkdown(prepared.text)
        val headingStarts = markdownHeadingSpanStarts(rendered)

        toc.forEach { entry ->
            val rank = headingRankInWindow(toc, entry.sourceOffset, 0)
            val resolved = resolveDisplayedCharOffset(
                sourceContent = md,
                sourceOffset = entry.sourceOffset,
                displayedText = rendered,
                renderPlainText = false,
                windowStart = 0,
                windowEnd = md.length,
                tocEntries = toc,
                preferredEntry = entry,
            )
            assertEquals(
                "jump to ${entry.title}",
                headingStarts[rank],
                resolved,
            )
        }
    }
}
