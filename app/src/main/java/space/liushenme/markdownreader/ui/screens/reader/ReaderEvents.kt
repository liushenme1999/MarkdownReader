package space.liushenme.markdownreader.ui.screens.reader

import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.model.HighlightStyle

sealed interface ReaderEvent {
    data class LoadBook(val bookId: Long) : ReaderEvent
    data object ReadingResumed : ReaderEvent
    data object ReadingPaused : ReaderEvent
    data class VisibleProgress(
        val globalChar: Int,
        val reachedEnd: Boolean = false,
        val bottomChar: Int? = null,
        val chapterOffset: Int? = null,
    ) : ReaderEvent
    data class ProgressAtChar(
        val globalChar: Int,
        val previewText: String? = null,
        val reachedEnd: Boolean = false,
        val bottomChar: Int? = null,
        val chapterOffset: Int? = null,
        val immediate: Boolean = false,
    ) : ReaderEvent
    data class PersistVisibleProgress(
        val globalChar: Int? = null,
        val previewText: String? = null,
    ) : ReaderEvent
    data class PersistReadingPosition(
        val globalChar: Int,
        val previewText: String? = null,
        val reachedEnd: Boolean? = null,
    ) : ReaderEvent
    data class FinishPromptEnabled(val enabled: Boolean) : ReaderEvent
    data class DocumentEndChanged(val atEnd: Boolean) : ReaderEvent
    data object MarkFinished : ReaderEvent
    data object DismissFinishPrompt : ReaderEvent
    data object RetryLoad : ReaderEvent
    data class AddBookmark(val previewText: String, val note: String? = null) : ReaderEvent
    data class DeleteBookmark(val bookmark: BookmarkEntity) : ReaderEvent
    data class DeleteHighlight(val highlight: HighlightEntity) : ReaderEvent
    data class DeleteHighlightById(val highlightId: Long) : ReaderEvent
    data class UpdateHighlightAppearance(
        val highlightId: Long,
        val colorArgb: Int,
        val style: HighlightStyle,
    ) : ReaderEvent
    data class ToggleBookmarkAtSwipe(
        val previewText: String? = null,
        val positionForAdd: Int? = null,
    ) : ReaderEvent
}

sealed interface ReaderEffect {
    data class BookLoaded(val bookId: Long) : ReaderEffect
    data class LoadFailed(val state: ReaderLoadState) : ReaderEffect
    data class BookmarkToggled(val added: Boolean) : ReaderEffect
}
