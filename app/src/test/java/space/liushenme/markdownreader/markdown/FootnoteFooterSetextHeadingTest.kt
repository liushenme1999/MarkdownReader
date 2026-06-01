package space.liushenme.markdownreader.markdown

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.ui.screens.reader.markdownHeadingSpanStarts
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FootnoteFooterSetextHeadingTest {

    @Test
    fun footnoteFooter_separatorMustNotTurnLastParagraphIntoSetextHeading() {
        val longBody = File("/Users/liukejun/Downloads/全格式示例markdown.md")
            .readText()
            .substringAfter("# 二十、长文本滚动测试\n")
            .trimEnd()
        val md = """
            # 十二、脚注用法
            引用[^note1]
            [^note1]: 定义内容

            # 二十、长文本滚动测试
            $longBody
        """.trimIndent()
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(md)
        val rendered = markwon.toMarkdown(prepared.text)
        val lines = markdownHeadingSpanStarts(rendered).map {
            space.liushenme.markdownreader.ui.screens.reader.lineAt(rendered.toString(), it)
        }
        assertEquals(
            "paragraph must not become setext heading before footnote --- footer; spans=$lines",
            2,
            lines.size,
        )
    }
}
