package space.liushenme.markdownreader.document

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingPositionResolverTest {

    private val total = 10_000_000

    @Test
    fun consistentPosition_isPreferredOverProgress() {
        val pos = (total * 0.32f).toInt()
        // 翻页/存盘时序造成的少量偏差仍以精确位置为准。
        assertEquals(pos + 800, resolveReadingOffset(pos + 800, 0.32f, total))
    }

    @Test
    fun collapsedPosition_isRecoveredFromProgress() {
        // 历史 bug：位置被按窗口长度（约 6 万字）裁剪过，进度仍是 32%。
        assertEquals(
            (total * 0.32f).toInt(),
            resolveReadingOffset(position = 61_234, progress = 0.32f, totalChars = total),
        )
    }

    @Test
    fun missingPosition_usesProgress() {
        assertEquals(
            (total * 0.5f).toInt(),
            resolveReadingOffset(position = 0, progress = 0.5f, totalChars = total),
        )
    }

    @Test
    fun emptyProgressAndPosition_startAtZero() {
        assertEquals(0, resolveReadingOffset(position = 0, progress = 0f, totalChars = total))
    }

    @Test
    fun finishedBook_keepsEndPosition() {
        assertEquals(total, resolveReadingOffset(position = total, progress = 1f, totalChars = total))
    }

    @Test
    fun positionsAreClampedIntoBook() {
        assertEquals(total, resolveReadingOffset(position = total * 2, progress = 1f, totalChars = total))
        assertEquals(0, resolveReadingOffset(position = -5, progress = 0f, totalChars = total))
    }
}
