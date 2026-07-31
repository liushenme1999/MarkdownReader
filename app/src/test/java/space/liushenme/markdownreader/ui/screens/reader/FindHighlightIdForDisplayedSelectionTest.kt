package space.liushenme.markdownreader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import java.util.Date

class FindHighlightIdForDisplayedSelectionTest {

    @Test
    fun findsExactRangeMatch() {
        val list = listOf(
            HighlightEntity(
                id = 3L,
                bookId = 1L,
                startPosition = 2,
                endPosition = 4,
                highlightedText = "选中",
                createTime = Date(),
            ),
        )
        assertEquals(
            3L,
            findHighlightIdForDisplayedSelection(list, "选中", 2, 4),
        )
    }

    @Test
    fun returnsNullWhenNoOverlap() {
        val list = listOf(
            HighlightEntity(
                id = 3L,
                bookId = 1L,
                startPosition = 0,
                endPosition = 2,
                highlightedText = "长按",
                createTime = Date(),
            ),
        )
        assertNull(findHighlightIdForDisplayedSelection(list, "选中", 2, 4))
    }
}
