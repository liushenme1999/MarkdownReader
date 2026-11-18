package space.liushenme.markdownreader.ui.screens.reader

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity

class ReaderBookmarkToggleTest {

    private fun bookmark(id: Long, position: Int) = BookmarkEntity(
        id = id,
        bookId = 1L,
        position = position,
        previewText = "p$id",
        createTime = Date(0),
    )

    @Test
    fun samePage_togglesExistingBookmarkOff() {
        val existing = bookmark(1, position = 1_000)
        val result = findBookmarkToToggle(
            bookmarks = listOf(existing),
            position = 1_050,
            rangeStart = 800,
            rangeEnd = 1_200,
        )
        assertEquals(existing, result)
    }

    @Test
    fun nextPages_doNotDeletePreviousPageBookmark() {
        // 回归：上一页 1000 处加了书签，翻 1-2 页（位置 1600）再加书签必须新增而不是取消。
        val existing = bookmark(1, position = 1_000)
        val result = findBookmarkToToggle(
            bookmarks = listOf(existing),
            position = 1_600,
            rangeStart = 1_500,
            rangeEnd = 1_900,
        )
        assertNull(result)
    }

    @Test
    fun oldCharacterWindowBugIsGone() {
        // 旧实现窗口 (total/40).coerceIn(300,1500)：1600 与 1000 相差 600 会被误判为同一处。
        val existing = bookmark(1, position = 1_000)
        assertNull(
            findBookmarkToToggle(
                bookmarks = listOf(existing),
                position = 1_600,
                rangeStart = 1_500,
                rangeEnd = 1_900,
            ),
        )
    }

    @Test
    fun withoutRange_onlyExactPositionToggles() {
        val existing = bookmark(1, position = 1_000)
        assertEquals(
            existing,
            findBookmarkToToggle(listOf(existing), position = 1_001),
        )
        assertNull(
            findBookmarkToToggle(listOf(existing), position = 1_200),
        )
    }

    @Test
    fun multipleBookmarksInRange_picksFirstInVisibleRange() {
        val first = bookmark(1, position = 900)
        val second = bookmark(2, position = 1_100)
        val result = findBookmarkToToggle(
            bookmarks = listOf(first, second),
            position = 1_000,
            rangeStart = 800,
            rangeEnd = 1_200,
        )
        assertEquals(first, result)
    }

    @Test
    fun emptyBookmarks_returnsNull() {
        assertNull(findBookmarkToToggle(emptyList(), position = 10, rangeStart = 0, rangeEnd = 100))
    }

    @Test
    fun invertedRangeFallsBackToExactPosition() {
        val existing = bookmark(1, position = 1_000)
        assertEquals(
            existing,
            findBookmarkToToggle(listOf(existing), position = 1_000, rangeStart = 2_000, rangeEnd = 1_000),
        )
        assertNull(
            findBookmarkToToggle(listOf(existing), position = 1_500, rangeStart = 2_000, rangeEnd = 1_000),
        )
    }
}
