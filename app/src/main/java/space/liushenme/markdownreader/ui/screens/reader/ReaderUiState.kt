package space.liushenme.markdownreader.ui.screens.reader

import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.document.DocumentSnapshot
import space.liushenme.markdownreader.model.HighlightStyle
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.theme.ReadingStyleState
import space.liushenme.markdownreader.ui.theme.ReadingTheme

sealed interface ReaderLoadState {
    data object Loading : ReaderLoadState
    data object Empty : ReaderLoadState
    data object Ready : ReaderLoadState
    data class MissingSource(val message: String) : ReaderLoadState
    data class BrokenBundle(val message: String) : ReaderLoadState
    data class NetworkRequired(val message: String) : ReaderLoadState
    data class UnsupportedFormat(val message: String) : ReaderLoadState
    data class PermissionDenied(val message: String) : ReaderLoadState
    data class Failed(val message: String) : ReaderLoadState
}

/** Stable screen contract used while the rendering engine is progressively extracted. */
data class ReaderUiState(
    val loadState: ReaderLoadState = ReaderLoadState.Loading,
    val book: BookEntity? = null,
    val content: String = "",
    val structuredToc: List<MarkdownTocEntry>? = null,
    val documentSnapshot: DocumentSnapshot? = null,
    val readerLoadEpoch: Long = 0L,
    val readingProgress: Float = 0f,
    val viewportTopChar: Int = 0,
    val viewportBottomChar: Int = 0,
    val visibleChapterOffset: Int? = null,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val highlights: List<HighlightEntity> = emptyList(),
    val readingStyleState: ReadingStyleState = ReadingStyleState.DEFAULT,
    val currentTheme: ReadingTheme = ReadingTheme.Paper,
    val fontSize: Int = 16,
    val readerPaddingDp: Int = 32,
    val readerLineSpacingMultiplier: Float = 1.5f,
    val codeBlockWrap: Boolean = true,
    val hideSystemBars: Boolean = true,
    val showTocInLandscape: Boolean = true,
    val pageTurnMode: ReaderPageTurnMode = ReaderPageTurnMode.VerticalScroll,
    val lastHighlightColorArgb: Int = 0,
    val lastHighlightStyle: HighlightStyle = HighlightStyle.DEFAULT,
    val showMarkFinishedPrompt: Boolean = false,
) {
    val loadError: String?
        get() = when (val state = loadState) {
            is ReaderLoadState.MissingSource -> state.message
            is ReaderLoadState.BrokenBundle -> state.message
            is ReaderLoadState.NetworkRequired -> state.message
            is ReaderLoadState.UnsupportedFormat -> state.message
            is ReaderLoadState.PermissionDenied -> state.message
            is ReaderLoadState.Failed -> state.message
            ReaderLoadState.Loading,
            ReaderLoadState.Empty,
            ReaderLoadState.Ready,
            -> null
        }
}
