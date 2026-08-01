package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
import org.junit.Assert.assertFalse
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
class TocJumpProgressRestoreVsTitleTest {

    @Test
    fun progressRestore_mapsSection18Near174_notTitle() {
        val markdown = TestFixtures.fullFormatSampleMarkdown()
        val toc = parseMarkdownToc(markdown)
        val sec18 = toc.first { it.title == "十八、转义字符测试" }
        val boundaries = computeChapterBoundaries(markdown, toc)
        val (winStart, winEnd) = computeReadingWindow(
            boundaries = boundaries,
            charPos = sec18.sourceOffset,
            contentLen = markdown.length,
        )
        val slice = markdown.substring(winStart, winEnd)
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(slice, context)
        val rendered = markwon.toMarkdown(prepared.text).toString()

        val titleOffset = resolveDisplayedCharOffset(
            sourceContent = markdown,
            sourceOffset = sec18.sourceOffset,
            displayedText = rendered,
            renderPlainText = false,
            windowStart = winStart,
            windowEnd = winEnd,
            tocEntries = toc,
            preferredEntry = sec18,
        )
        val progressOffset = resolveDisplayedCharOffsetForProgressRestore(
            sourceOffset = sec18.sourceOffset,
            windowStart = winStart,
            windowEnd = winEnd,
            displayedLen = rendered.length,
            renderPlainText = false,
        )
        val titleLine = lineAt(rendered, titleOffset)
        val progressLine = lineAt(rendered, progressOffset)
        assertTrue("title path: $titleLine", titleLine.contains("十八"))
        assertFalse(
            "progress restore should not match title for sec18: progressLine=$progressLine titleLine=$titleLine",
            titleMatchesRenderedLine(progressLine, sec18.title),
        )
    }
}
