package space.liushenme.markdownreader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import space.liushenme.markdownreader.data.repository.ReadingFormatProfile

class ReaderPageTurnModeTest {

    @Test
    fun markdown_onlySupportsVerticalScroll() {
        assertEquals(
            listOf(ReaderPageTurnMode.VerticalScroll),
            ReaderPageTurnMode.selectableModesFor(ReadingFormatProfile.MARKDOWN),
        )
        assertFalse(ReaderPageTurnMode.supportsHorizontal(ReadingFormatProfile.MARKDOWN))
    }

    @Test
    fun textAndOtherFormats_keepHorizontalSwipe() {
        listOf(
            ReadingFormatProfile.PLAIN_TEXT,
            ReadingFormatProfile.HTML,
            ReadingFormatProfile.PDF,
            ReadingFormatProfile.CODE,
        ).forEach { profile ->
            assertEquals(
                ReaderPageTurnMode.selectableModes,
                ReaderPageTurnMode.selectableModesFor(profile),
            )
            assertTrue(ReaderPageTurnMode.supportsHorizontal(profile))
        }
    }

    @Test
    fun legacySimulationAndCover_normalizeToVerticalForMarkdownOnlyByCaller() {
        // normalize 仍把已下线模式归一为左右滑动（其他格式继续兼容旧偏好）
        assertEquals(
            ReaderPageTurnMode.HorizontalSwipe,
            ReaderPageTurnMode.normalize(ReaderPageTurnMode.SimulationPageTurn),
        )
        assertEquals(
            ReaderPageTurnMode.HorizontalSwipe,
            ReaderPageTurnMode.normalize(ReaderPageTurnMode.CoverPageTurn),
        )
    }
}
