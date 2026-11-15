package space.liushenme.markdownreader.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯文本目录解析：兼容常见小说章节写法（参考 Legado 的内置规则）。 */
class PlainTextTocParserTest {

    private fun body(vararg chapters: String): String = buildString {
        append("《测试小说》 作者：某作者\n\n")
        chapters.forEach { title ->
            append(title).append('\n')
            append("正文内容正文内容正文内容，用来拉开章节间距。\n".repeat(30))
            append('\n')
        }
    }

    @Test
    fun parsesChapterTitlesWithSpaces() {
        val text = body("第515章 未来的百科全书派", "第 516 章 带空格", "第517章　全角空格")
        val toc = PlainTextTocParser.parse(text)
        assertEquals(3, toc.size)
        assertEquals("第515章 未来的百科全书派", toc[0].title)
        assertEquals("第 516 章 带空格", toc[1].title)
        assertTrue(toc[2].title.startsWith("第517章"))
        // 偏移必须指向标题行首，供跳转使用
        assertEquals(text.indexOf("第515章"), toc[0].sourceOffset)
        assertEquals(text.indexOf("第 516 章"), toc[1].sourceOffset)
    }

    @Test
    fun parsesChineseAndSpecialTitles() {
        val text = body("第一章 开始", "第十回 归来", "楔子", "番外篇 以后")
        val toc = PlainTextTocParser.parse(text)
        assertEquals(4, toc.size)
        assertEquals("第一章 开始", toc[0].title)
        assertEquals("第十回 归来", toc[1].title)
        assertEquals("楔子", toc[2].title)
    }

    @Test
    fun parsesNumberedAndEnglishTitles() {
        val text = body("1. 开端", "2、发展", "Chapter 3 The Return")
        val toc = PlainTextTocParser.parse(text)
        assertTrue("应至少解析出编号标题，实际=${toc.map { it.title }}", toc.size >= 3)
    }

    @Test
    fun ignoresDenseFakeTitles() {
        // 目录页把标题挤在一起（间隔很小），不应全部当成章节
        val text = "第1章 甲\n第2章 乙\n第3章 丙\n" + "正文".repeat(500)
        val toc = PlainTextTocParser.parse(text)
        assertEquals("相邻过近的标题应被过滤", 1, toc.size)
    }

    @Test
    fun mergesRepeatedChapterTitleAsSeenInRealNovels() {
        // 真实网文结构：先一行标题，紧接缩进再出现一次真正的章节标题
        val text = "第4章 让世界感受痛苦吧！ \r\n\r\n    第4章 让世界感受痛苦吧！\r\n\r\n" +
            "正文正文正文。".repeat(200) + "\r\n" +
            "第5章 天灾来袭！ \r\n\r\n    第5章 天灾来袭！\r\n\r\n" + "正文正文正文。".repeat(200)
        val toc = PlainTextTocParser.parse(text)
        assertEquals("重复的章首标题应合并为一条", 2, toc.size)
        assertEquals("第4章 让世界感受痛苦吧！", toc[0].title)
        // 只要合并成一条即可：两条重复标题相距仅几十字符，跳转误差可忽略
        assertTrue("偏移必须有效", toc[0].sourceOffset >= 0)
        assertEquals(text.indexOf("第5章"), toc[1].sourceOffset)
    }

    @Test
    fun emptyTextYieldsEmptyToc() {
        assertTrue(PlainTextTocParser.parse("").isEmpty())
    }
}
