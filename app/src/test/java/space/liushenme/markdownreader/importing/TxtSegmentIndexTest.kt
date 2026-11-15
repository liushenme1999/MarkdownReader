package space.liushenme.markdownreader.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class TxtSegmentIndexTest {

    private fun writeBook(dir: File, chapters: Int): File {
        val file = File(dir, "body.txt")
        file.bufferedWriter(StandardCharsets.UTF_8).use { w ->
            repeat(chapters) { i ->
                w.write("第${i + 1}章 标题$i\n")
                w.write("这是第${i + 1}章的正文内容，用于验证按窗口加载。\n".repeat(20))
            }
        }
        return file
    }

    @Test
    fun buildIndex_coversWholeTextWithoutGaps() {
        val dir = File.createTempFile("txtidx", "").let { it.delete(); it.mkdirs(); it }
        try {
            val body = writeBook(dir, 200)
            val total = body.readText(StandardCharsets.UTF_8).length
            val segments = TxtSegmentIndex.build(body, targetChars = 2_000)
            assertNotNull(segments)
            assertEquals(0, segments!!.first().startChar)
            assertEquals(total, TxtSegmentIndex.totalChars(segments))
            // 段与段之间必须首尾相接，不能有空洞
            segments.zipWithNext().forEach { (a, b) -> assertEquals(a.startChar + a.charCount, b.startChar) }
            assertTrue(segments.size >= 2)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun appendingNextWindow_continuesWithoutGapsOrDuplicates() {
        val dir = File.createTempFile("txtidx", "").let { it.delete(); it.mkdirs(); it }
        try {
            val body = writeBook(dir, 200)
            val full = body.readText(StandardCharsets.UTF_8)
            val segments = TxtSegmentIndex.build(body, targetChars = 1_000)!!
            // 模拟阅读页：先开一小窗，再按「当前窗口末尾」连续追加，直到全书。
            var window = TxtSegmentIndex.loadWindow(body, segments, 0, before = 0, after = 1)!!
            val appended = StringBuilder(window.text)
            assertEquals(0, window.startChar)
            var hops = 0
            while (window.endChar < full.length && hops < 10_000) {
                val next = TxtSegmentIndex.loadWindow(
                    body,
                    segments,
                    window.endChar,
                    before = 0,
                    after = 1,
                ) ?: break
                // 追加必须从上一窗末尾接着读，不能重叠（否则翻页会看到重复段落）
                assertEquals(window.endChar, next.startChar)
                appended.append(next.text)
                window = next
                hops++
            }
            assertEquals(full, appended.toString())
            assertEquals(full.length, window.endChar)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun loadWindow_onlyReadsNearbySegmentsAndKeepsGlobalOffsets() {
        val dir = File.createTempFile("txtidx", "").let { it.delete(); it.mkdirs(); it }
        try {
            val body = writeBook(dir, 200)
            val full = body.readText(StandardCharsets.UTF_8)
            val segments = TxtSegmentIndex.build(body, targetChars = 2_000)!!
            val anchor = full.length / 2
            val window = TxtSegmentIndex.loadWindow(body, segments, anchor, before = 1, after = 1)
            assertNotNull(window)
            // 窗口必须远小于全文
            assertTrue("窗口应只加载附近内容，实际=${window!!.text.length}/${full.length}",
                window.text.length < full.length / 3)
            // 窗口文本必须与全文对应字符区间完全一致（坐标可直接用于进度/书签）
            assertEquals(full.substring(window.startChar, window.endChar), window.text)
            assertTrue("锚点必须落在窗口内", anchor in window.startChar..window.endChar)
            assertEquals(full.length, window.totalChars)
        } finally {
            dir.deleteRecursively()
        }
    }
}
