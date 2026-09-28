package space.liushenme.markdownreader.ui.screens.reader

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity

class ReaderNavigationSheetsTest {

    @Test
    fun bookmarkJumpTarget_prefersResolvedAnchorPosition() {
        val bookmark = BookmarkEntity(
            id = 7L,
            bookId = 1L,
            position = 120,
            previewText = "old preview",
            createTime = Date(0),
        )

        val target = bookmarkJumpTarget(bookmark, mapOf(7L to 480))

        assertEquals(480, target.sourceOffset)
        assertEquals("old preview", target.previewText)
        assertEquals(AnnotationJumpKind.Bookmark, target.kind)
        assertNull(target.highlightId)
    }

    @Test
    fun highlightJumpTarget_fallsBackToStoredPositionWhenUnresolved() {
        val highlight = HighlightEntity(
            id = 9L,
            bookId = 1L,
            startPosition = 240,
            endPosition = 260,
            highlightedText = "selected text",
            color = 0xFF00FF00.toInt(),
            createTime = Date(0),
        )

        val target = highlightJumpTarget(highlight, emptyMap())

        assertEquals(240, target.sourceOffset)
        assertEquals("selected text", target.previewText)
        assertEquals(AnnotationJumpKind.Highlight, target.kind)
        assertEquals(9L, target.highlightId)
    }
}
