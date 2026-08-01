package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory
import space.liushenme.markdownreader.testutil.TestFixtures

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TocJumpSection18FullDocTest {

    @Test
    fun section18to20_resolveOffsetByTitle_inFullPreparedDoc() {
        val markdown = TestFixtures.fullFormatSampleMarkdown()
        val toc = parseMarkdownToc(markdown)
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)

        val boundaries = computeChapterBoundaries(markdown, toc)
        listOf("十八、转义字符测试", "十九、混合嵌套排版", "二十、长文本滚动测试").forEach { title ->
            val entry = toc.first { it.title == title }
            val (winStart, winEnd) = computeTocJumpReadingWindow(
                boundaries = boundaries,
                charPos = entry.sourceOffset,
                contentLen = markdown.length,
            )
            val slice = markdown.substring(winStart, winEnd)
            val prepared = ReaderMarkwonFactory.prepareMarkdown(slice, context)
            val rendered = markwon.toMarkdown(prepared.text)
            val resolved = resolveDisplayedCharOffset(
                sourceContent = markdown,
                sourceOffset = entry.sourceOffset,
                displayedText = rendered,
                renderPlainText = false,
                windowStart = winStart,
                windowEnd = winEnd,
                tocEntries = toc,
                preferredEntry = entry,
            )
            val line = lineAt(rendered.toString(), resolved)
            assertTrue("jump $title got line=$line", titleMatchesRenderedLine(line, title))
        }
    }
}
