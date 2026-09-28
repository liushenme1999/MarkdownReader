package space.liushenme.markdownreader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSessionControllerTest {

    @Test
    fun restoreBookState_restoresStableAnchorAndPreview() {
        val controller = ReaderSessionController()

        controller.restoreBookState(
            progress = 0.25f,
            position = 250,
            contentLength = 1_000,
            previewText = "  chapter preview  ",
        )

        assertEquals(0.25f, controller.readingProgress.value)
        assertEquals(250, controller.viewportTopChar.value)
        assertEquals(250, controller.lastKnownReadingCharPos)
        assertEquals("chapter preview", controller.lastKnownProgressPreview)
    }

    @Test
    fun documentEnd_promptsOnlyAfterInitialRestoreIsReady() {
        val controller = ReaderSessionController()
        controller.restoreBookState(0.9f, 900, 1_000, "near end")

        controller.onDocumentEndChanged(true)
        assertFalse(controller.showMarkFinishedPrompt.value)

        controller.setFinishPromptEnabled(true)
        assertTrue(controller.showMarkFinishedPrompt.value)

        controller.dismissFinishPrompt()
        assertFalse(controller.showMarkFinishedPrompt.value)
        controller.onDocumentEndChanged(true)
        assertFalse(controller.showMarkFinishedPrompt.value)
    }

    @Test
    fun confirmedFinished_staysAtHundredUntilReaderLeavesEnd() {
        val controller = ReaderSessionController()
        controller.restoreBookState(0.9f, 900, 1_000, "near end")

        val finished = controller.markAsFinished(1_000)
        assertEquals(1f, finished.progress)

        controller.setFinishPromptEnabled(true)
        controller.updateVisibleProgress(400, reachedEnd = false, bottomChar = 500, contentLength = 1_000)

        assertEquals(0.4f, controller.readingProgress.value)
        assertFalse(controller.showMarkFinishedPrompt.value)
    }

    @Test
    fun pause_returnsOnlyForegroundSessionDelta() {
        var now = 1_000L
        val controller = ReaderSessionController(nowMillis = { now })
        controller.restoreBookState(0.1f, 100, 1_000, "start")

        assertNull(controller.onReadingPaused(1_000))

        controller.onReadingResumed(hasBook = true, hasContent = true)
        now += 61_000L
        controller.updateVisibleProgress(260, reachedEnd = false, bottomChar = 300, contentLength = 1_000)
        val delta = controller.onReadingPaused(1_000)

        assertEquals(160, delta?.charsRead)
        assertEquals(1, delta?.minutesRead)
        assertNull(controller.onReadingPaused(1_000))
    }

    @Test
    fun capturePosition_normalizesBoundsAndPreview() {
        val controller = ReaderSessionController()
        controller.restoreBookState(0f, 0, 100, "")

        val snapshot = controller.capturePosition(
            contentLength = 100,
            globalChar = 150,
            previewText = "last page",
            reachedEnd = false,
        )

        assertEquals(100, snapshot.position)
        assertEquals("last page", snapshot.previewText)
    }

    @Test
    fun resetForLoad_clearsPreviousBookSessionState() {
        val controller = ReaderSessionController()
        controller.restoreBookState(0.8f, 800, 1_000, "old book")
        controller.setVisibleChapterOffset(700)
        controller.setFinishPromptEnabled(true)
        controller.onDocumentEndChanged(true)

        controller.resetForLoad()

        assertEquals(0f, controller.readingProgress.value)
        assertEquals(0, controller.viewportTopChar.value)
        assertEquals(0, controller.viewportBottomChar.value)
        assertEquals(0, controller.lastKnownReadingCharPos)
        assertEquals("", controller.lastKnownProgressPreview)
        assertNull(controller.visibleChapterOffset.value)
        assertFalse(controller.showMarkFinishedPrompt.value)
        assertFalse(controller.canPersistProgress)
    }

    @Test
    fun restoredFinishedBook_doesNotPromptAgainAtDocumentEnd() {
        val controller = ReaderSessionController()
        controller.restoreBookState(1f, 1_000, 1_000, "finished")

        controller.onDocumentEndChanged(true)
        controller.setFinishPromptEnabled(true)

        assertFalse(controller.showMarkFinishedPrompt.value)
        assertEquals(1f, controller.readingProgress.value)
    }

    @Test
    fun navigationAwayFromFinishedEnd_releasesFinishedLatch() {
        val controller = ReaderSessionController()
        controller.restoreBookState(0.9f, 900, 1_000, "near end")
        controller.markAsFinished(1_000)
        controller.setFinishPromptEnabled(true)

        val snapshot = controller.updateProgressAtChar(
            globalChar = 250,
            previewText = "chapter two",
            reachedEnd = false,
            bottomChar = 350,
            chapterOffset = 200,
            contentLength = 1_000,
        )

        assertEquals(0.25f, snapshot.progress)
        assertEquals(250, snapshot.position)
        assertEquals(350, controller.viewportBottomChar.value)
        assertEquals(200, controller.visibleChapterOffset.value)
        assertFalse(controller.showMarkFinishedPrompt.value)
    }
}
