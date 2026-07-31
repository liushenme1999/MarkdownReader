package space.liushenme.markdownreader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveTocCurrentIndexTest {

    private val entries = listOf(
        MarkdownTocEntry(level = 1, title = "一", sourceOffset = 0, rawTitle = "一"),
        MarkdownTocEntry(level = 2, title = "二", sourceOffset = 100, rawTitle = "二"),
        MarkdownTocEntry(level = 1, title = "三", sourceOffset = 200, rawTitle = "三"),
    )

    @Test
    fun resolve_bySourceOffset() {
        assertEquals(
            1,
            resolveTocCurrentIndex(
                entries,
                MarkdownTocEntry(level = 2, title = "二", sourceOffset = 100, rawTitle = "二"),
            ),
        )
    }

    @Test
    fun resolve_lastChapter() {
        assertEquals(
            2,
            resolveTocCurrentIndex(
                entries,
                MarkdownTocEntry(level = 1, title = "三", sourceOffset = 200, rawTitle = "三"),
            ),
        )
    }

    @Test
    fun resolve_fallbackRawTitle() {
        assertEquals(
            0,
            resolveTocCurrentIndex(
                entries,
                MarkdownTocEntry(level = 1, title = "别名", sourceOffset = 999, rawTitle = "一"),
            ),
        )
    }

    @Test
    fun resolve_nullWhenMissing() {
        assertNull(resolveTocCurrentIndex(entries, null))
        assertNull(
            resolveTocCurrentIndex(
                entries,
                MarkdownTocEntry(level = 1, title = "无", sourceOffset = 999, rawTitle = "无"),
            ),
        )
    }
}
