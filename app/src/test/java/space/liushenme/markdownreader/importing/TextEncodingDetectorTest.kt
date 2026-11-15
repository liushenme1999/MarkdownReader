package space.liushenme.markdownreader.importing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class TextEncodingDetectorTest {

    private fun gb18030(text: String, corruptByte: Boolean = false): ByteArray {
        val body = text.toByteArray(Charset.forName("GB18030"))
        if (!corruptByte) return body
        // 老小说常夹带个别非法双字节：严格解码会整篇失败
        return body + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + body.copyOfRange(0, 40)
    }

    @Test
    fun decodesGb18030NovelWithBadBytes() {
        val sample = "第515章 未来的百科全书派\n\n被榨干的冒险者也不会浪费".repeat(200)
        val decoded = TextEncodingDetector.decode(gb18030(sample, corruptByte = true))
        assertTrue(
            "应识别为中文而不是乱码，实际开头：${decoded.take(20)}",
            decoded.contains("冒险者") || decoded.contains("百科全书"),
        )
        assertFalse(decoded.contains("Ã") && decoded.contains("¡"))
    }

    @Test
    fun keepsUtf8Detection() {
        val text = "第1章 测试 UTF-8 文本\n正文内容".repeat(200)
        assertTrue(TextEncodingDetector.decode(text.toByteArray(Charsets.UTF_8)).contains("测试 UTF-8 文本"))
    }
}
