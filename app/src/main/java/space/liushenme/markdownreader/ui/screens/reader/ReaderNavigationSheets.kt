package space.liushenme.markdownreader.ui.screens.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.importing.PdfReaderContent

/** Immutable input for the bookmark/highlight sheet. */
internal data class ReaderMarksSheetState(
    val bookmarks: List<BookmarkEntity>,
    val highlights: List<HighlightEntity>,
    val totalChars: Int,
    val isPdf: Boolean,
    val sourceContent: String,
    val pendingBookmarkIds: Set<Long>,
    val pendingHighlightIds: Set<Long>,
    val bookmarkJumpPositions: Map<Long, Int>,
    val highlightJumpPositions: Map<Long, Int>,
)

/** Immutable input for the table-of-contents sheet. */
internal data class ReaderTocSheetState(
    val entries: List<MarkdownTocEntry>,
    val emptyMessage: String,
    val currentEntry: MarkdownTocEntry?,
)

/**
 * Reader navigation panels. They translate row clicks into stable navigation
 * intents while ReaderScreen remains responsible for the actual layout jump.
 */
@Composable
internal fun ReaderNavigationSheets(
    showMarks: Boolean,
    showToc: Boolean,
    marksState: ReaderMarksSheetState,
    tocState: ReaderTocSheetState,
    onAnnotationClick: (AnnotationJumpTarget) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onDeleteHighlight: (HighlightEntity) -> Unit,
    onTocEntryClick: (MarkdownTocEntry) -> Unit,
    onDismissMarks: () -> Unit,
    onDismissToc: () -> Unit,
) {
    if (showMarks) {
        val context = LocalContext.current
        val bookmarkRows = remember(
            marksState.bookmarks,
            marksState.isPdf,
            marksState.sourceContent,
            context,
        ) {
            if (!marksState.isPdf) {
                marksState.bookmarks
            } else {
                marksState.bookmarks.map { bookmark ->
                    bookmark.copy(
                        previewText = PdfReaderContent.bookmarkPreview(
                            context,
                            marksState.sourceContent,
                            bookmark.position,
                        ),
                    )
                }
            }
        }
        BookmarksSheet(
            bookmarks = bookmarkRows,
            highlights = marksState.highlights,
            totalChars = marksState.totalChars,
            pendingBookmarkIds = marksState.pendingBookmarkIds,
            pendingHighlightIds = marksState.pendingHighlightIds,
            onBookmarkClick = { bookmark ->
                onAnnotationClick(bookmarkJumpTarget(bookmark, marksState.bookmarkJumpPositions))
            },
            onDeleteBookmark = onDeleteBookmark,
            onHighlightClick = { highlight ->
                onAnnotationClick(highlightJumpTarget(highlight, marksState.highlightJumpPositions))
            },
            onDeleteHighlight = onDeleteHighlight,
            onDismiss = onDismissMarks,
        )
    }

    if (showToc) {
        TocSheet(
            entries = tocState.entries,
            emptyTocMessage = tocState.emptyMessage,
            currentEntry = tocState.currentEntry,
            onEntryClick = onTocEntryClick,
            onDismiss = onDismissToc,
        )
    }
}

internal fun bookmarkJumpTarget(
    bookmark: BookmarkEntity,
    resolvedPositions: Map<Long, Int>,
): AnnotationJumpTarget = AnnotationJumpTarget(
    sourceOffset = resolvedPositions[bookmark.id] ?: bookmark.position,
    previewText = bookmark.previewText,
    kind = AnnotationJumpKind.Bookmark,
)

internal fun highlightJumpTarget(
    highlight: HighlightEntity,
    resolvedPositions: Map<Long, Int>,
): AnnotationJumpTarget = AnnotationJumpTarget(
    sourceOffset = resolvedPositions[highlight.id] ?: highlight.startPosition,
    previewText = highlight.highlightedText,
    kind = AnnotationJumpKind.Highlight,
    highlightId = highlight.id,
)
