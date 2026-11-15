package space.liushenme.markdownreader.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtBodyNormalizerTest {

    @Test
    fun removesDuplicatedHeadingRightAfterFirstOne() {
        val text = "第1章 我叫罗炎，在魔王学院上学\r\n\r\n    第1章 我叫罗炎，在魔王学院上学\r\n\r\n    阴森恐怖的教室。"
        val out = TxtBodyNormalizer.dedupeRepeatedHeadingLines(text)
        assertEquals(1, Regex("第1章 我叫罗炎，在魔王学院上学").findAll(out).count())
        assertTrue("正文必须保留", out.contains("阴森恐怖的教室"))
    }

    @Test
    fun keepsNormalTextUntouched() {
        val text = "第1章 开始\r\n正文正文正文\r\n第2章 结束\r\n正文正文"
        assertEquals(text, TxtBodyNormalizer.dedupeRepeatedHeadingLines(text))
    }
}
