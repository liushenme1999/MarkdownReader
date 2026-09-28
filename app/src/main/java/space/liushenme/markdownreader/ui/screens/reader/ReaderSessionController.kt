package space.liushenme.markdownreader.ui.screens.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.liushenme.markdownreader.data.local.entity.isFinishedReading
import space.liushenme.markdownreader.data.repository.ReadingSessionStats

/**
 * Owns the mutable state of one reading session.
 *
 * The controller deliberately has no Android or repository dependency. It is the
 * deterministic boundary between rendering callbacks and persistence/UI effects.
 */
internal class ReaderSessionController(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val _readingProgress = MutableStateFlow(0f)
    val readingProgress: StateFlow<Float> = _readingProgress.asStateFlow()

    private val _viewportTopChar = MutableStateFlow(0)
    val viewportTopChar: StateFlow<Int> = _viewportTopChar.asStateFlow()

    private val _viewportBottomChar = MutableStateFlow(0)
    val viewportBottomChar: StateFlow<Int> = _viewportBottomChar.asStateFlow()

    private val _visibleChapterOffset = MutableStateFlow<Int?>(null)
    val visibleChapterOffset: StateFlow<Int?> = _visibleChapterOffset.asStateFlow()

    private val _showMarkFinishedPrompt = MutableStateFlow(false)
    val showMarkFinishedPrompt: StateFlow<Boolean> = _showMarkFinishedPrompt.asStateFlow()

    var lastKnownReadingCharPos: Int = 0
        private set
    var lastKnownProgressPreview: String = ""
        private set

    private var sessionSegmentStartMs = 0L
    private var sessionSegmentStartCharPos = 0
    private var readingForeground = false
    private var lastKnownReachedEnd = false
    private var finishedLatch = false
    private var finishPromptDismissedThisSession = false
    private var finishPromptEnabled = false
    val canPersistProgress: Boolean
        get() = finishPromptEnabled

    fun resetForLoad() {
        _readingProgress.value = 0f
        _viewportTopChar.value = 0
        _viewportBottomChar.value = 0
        _visibleChapterOffset.value = null
        _showMarkFinishedPrompt.value = false
        lastKnownReadingCharPos = 0
        lastKnownProgressPreview = ""
        sessionSegmentStartMs = 0L
        sessionSegmentStartCharPos = 0
        lastKnownReachedEnd = false
        finishedLatch = false
        finishPromptDismissedThisSession = false
        finishPromptEnabled = false
    }

    fun restoreBookState(
        progress: Float,
        position: Int,
        contentLength: Int,
        previewText: String,
    ) {
        val finished = progress.isFinishedReading()
        _readingProgress.value = progress
        lastKnownReachedEnd = finished
        finishedLatch = finished
        finishPromptDismissedThisSession = finished
        finishPromptEnabled = false
        setViewportChars(position, contentLength = contentLength)
        setVisibleChapterOffset(null)
        lastKnownProgressPreview = previewText.trim()
    }

    fun setViewportChars(top: Int, bottom: Int = top, contentLength: Int) {
        val length = contentLength.coerceAtLeast(0)
        val normalizedTop = top.coerceIn(0, length)
        val normalizedBottom = bottom.coerceIn(normalizedTop, length)
        lastKnownReadingCharPos = normalizedTop
        _viewportTopChar.value = normalizedTop
        _viewportBottomChar.value = normalizedBottom
    }

    fun setVisibleChapterOffset(offset: Int?) {
        _visibleChapterOffset.value = offset?.coerceAtLeast(0)
    }

    fun onReadingResumed(hasBook: Boolean, hasContent: Boolean) {
        readingForeground = true
        if (!hasBook || !hasContent || sessionSegmentStartMs > 0L) return
        sessionSegmentStartMs = nowMillis()
        sessionSegmentStartCharPos = lastKnownReadingCharPos
    }

    fun onReadingPaused(contentLength: Int): ReaderSessionDelta? {
        readingForeground = false
        val segmentStart = sessionSegmentStartMs
        if (segmentStart <= 0L) return null
        sessionSegmentStartMs = 0L
        val elapsed = (nowMillis() - segmentStart).coerceAtLeast(0L)
        val endPosition = lastKnownReadingCharPos.coerceIn(0, contentLength.coerceAtLeast(0))
        val charsRead = (endPosition - sessionSegmentStartCharPos).coerceAtLeast(0)
        sessionSegmentStartCharPos = endPosition
        return ReaderSessionDelta(
            charsRead = charsRead,
            minutesRead = ReadingSessionStats.elapsedMillisToMinutes(elapsed),
        )
    }

    fun updateVisibleProgress(globalChar: Int, reachedEnd: Boolean, bottomChar: Int?, contentLength: Int) {
        val length = contentLength.coerceAtLeast(1)
        val position = globalChar.coerceIn(0, length)
        setViewportChars(position, bottomChar ?: position, length)
        val progress = applyViewportProgress(position, length, reachedEnd)
        if ((_readingProgress.value * 100).toInt() != (progress * 100).toInt()) {
            _readingProgress.value = progress
        }
        maybeShowMarkFinishedPrompt()
    }

    fun updateProgressAtChar(
        globalChar: Int,
        previewText: String?,
        reachedEnd: Boolean,
        bottomChar: Int?,
        chapterOffset: Int?,
        contentLength: Int,
    ): ReaderProgressSnapshot {
        val length = contentLength.coerceAtLeast(1)
        val position = globalChar.coerceIn(0, length)
        setViewportChars(position, bottomChar ?: _viewportBottomChar.value.coerceAtLeast(position), length)
        if (chapterOffset != null) setVisibleChapterOffset(chapterOffset)
        previewText?.let { lastKnownProgressPreview = it }
        _readingProgress.value = applyViewportProgress(position, length, reachedEnd)
        maybeShowMarkFinishedPrompt()
        return snapshot()
    }

    fun markAsFinished(contentLength: Int): ReaderProgressSnapshot {
        finishedLatch = true
        finishPromptDismissedThisSession = true
        lastKnownReachedEnd = true
        _showMarkFinishedPrompt.value = false
        _readingProgress.value = 1f
        return snapshot(contentLength)
    }

    fun setFinishPromptEnabled(enabled: Boolean) {
        finishPromptEnabled = enabled
        if (enabled) maybeShowMarkFinishedPrompt() else _showMarkFinishedPrompt.value = false
    }

    fun onDocumentEndChanged(atEnd: Boolean) {
        lastKnownReachedEnd = atEnd
        if (finishPromptEnabled && !atEnd) {
            finishedLatch = false
            _showMarkFinishedPrompt.value = false
            return
        }
        maybeShowMarkFinishedPrompt()
    }

    fun dismissFinishPrompt() {
        finishPromptDismissedThisSession = true
        _showMarkFinishedPrompt.value = false
    }

    fun captureProgress(contentLength: Int, globalChar: Int?, previewText: String?): ReaderProgressSnapshot {
        if (globalChar != null) lastKnownReadingCharPos = globalChar.coerceIn(0, contentLength.coerceAtLeast(0))
        previewText?.let { lastKnownProgressPreview = it }
        _readingProgress.value = progressToPersist()
        return snapshot(contentLength)
    }

    fun capturePosition(
        contentLength: Int,
        globalChar: Int,
        previewText: String?,
        reachedEnd: Boolean?,
    ): ReaderProgressSnapshot {
        val length = contentLength.coerceAtLeast(1)
        lastKnownReadingCharPos = globalChar.coerceIn(0, length)
        reachedEnd?.let {
            lastKnownReachedEnd = it
            if (finishPromptEnabled && !it) finishedLatch = false
        }
        previewText?.let { lastKnownProgressPreview = it }
        _readingProgress.value = progressToPersist()
        return snapshot(length)
    }

    fun updateBookmarkPosition(position: Int, previewText: String?, contentLength: Int, isPdf: Boolean) {
        val length = contentLength.coerceAtLeast(1)
        lastKnownReadingCharPos = position.coerceIn(0, length)
        if (!isPdf && previewText != null) lastKnownProgressPreview = previewText
        _readingProgress.value = lastKnownReadingCharPos.toFloat() / length
    }

    fun snapshot(contentLength: Int = Int.MAX_VALUE): ReaderProgressSnapshot =
        ReaderProgressSnapshot(
            progress = progressToPersist(),
            position = lastKnownReadingCharPos.coerceIn(0, contentLength.coerceAtLeast(0)),
            previewText = lastKnownProgressPreview,
        )

    private fun applyViewportProgress(position: Int, contentLength: Int, reachedEnd: Boolean): Float {
        lastKnownReachedEnd = reachedEnd
        if (finishPromptEnabled && !reachedEnd) {
            finishedLatch = false
            _showMarkFinishedPrompt.value = false
        }
        return displayedReadingProgress(position, contentLength, finishedLatch)
    }

    private fun progressToPersist(): Float = when {
        finishedLatch -> 1f
        _readingProgress.value.isFinishedReading() -> 1f
        else -> _readingProgress.value
    }

    private fun maybeShowMarkFinishedPrompt() {
        if (!finishPromptEnabled || !lastKnownReachedEnd || finishPromptDismissedThisSession) return
        if (finishedLatch || _readingProgress.value.isFinishedReading()) return
        _showMarkFinishedPrompt.value = true
    }
}

internal data class ReaderProgressSnapshot(
    val progress: Float,
    val position: Int,
    val previewText: String,
)

internal data class ReaderSessionDelta(
    val charsRead: Int,
    val minutesRead: Int,
)
