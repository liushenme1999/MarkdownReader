package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory
import space.liushenme.markdownreader.testutil.TestFixtures

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TocJumpSection20ExtraHeadingTest {

    @Test
    fun section20LongParagraph_mustNotBecomeHeadingSpan() {
        val body = TestFixtures.fullFormatSampleMarkdown()
            .substringAfter("# 二十、长文本滚动测试\n")
            .trimEnd()
        val md = "# 二十、长文本滚动测试\n$body"
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(md, context)
        val rendered = markwon.toMarkdown(prepared.text)
        val headingStarts = markdownHeadingSpanStarts(rendered)
        val lines = headingStarts.map { lineAt(rendered.toString(), it) }

        assertEquals(
            "Only the ATX title should be a heading, body was: ${lines.drop(1)}",
            1,
            headingStarts.size,
        )
        assertEquals("二十、长文本滚动测试", lines.single())
    }

    @Test
    fun section19And20_together_reproducesExtraHeadingSpan() {
        val full = TestFixtures.fullFormatSampleMarkdown()
        val start = full.indexOf("# 十九、混合嵌套排版")
        val md = full.substring(start)
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(md, context)
        val rendered = markwon.toMarkdown(prepared.text)
        val headingStarts = markdownHeadingSpanStarts(rendered)
        val lines = headingStarts.map { lineAt(rendered.toString(), it) }

        assertEquals(
            "Expected 2 headings but got: $lines",
            2,
            headingStarts.size,
        )
    }
}
