package space.liushenme.markdownreader.ui.screens.reader

import space.liushenme.markdownreader.model.ReaderPageTurnMode

/** Why a reader jump was requested. TOC jumps always align a PDF page to its top. */
internal enum class ReaderNavigationRequestKind {
    Annotation,
    Toc,
}

/**
 * Pure navigation decision consumed by [ReaderScreen]. Compose state and view side effects stay outside
 * this model, which makes the routing rules independently testable.
 */
internal sealed interface ReaderNavigationPlan {
    data class PdfContinuous(
        val pageIndex: Int,
        val fractionInPage: Float,
        val progressChar: Int,
    ) : ReaderNavigationPlan

    data class Paged(
        val charPosition: Int,
        val pdfPageIndex: Int?,
    ) : ReaderNavigationPlan

    data class PdfVertical(
        val charPosition: Int,
        val pageIndex: Int,
    ) : ReaderNavigationPlan

    data class ChunkWindow(
        val charPosition: Int,
    ) : ReaderNavigationPlan
}

internal fun createReaderNavigationPlan(
    requestKind: ReaderNavigationRequestKind,
    isPdf: Boolean,
    pageTurnMode: ReaderPageTurnMode,
    hasPageSpecs: Boolean,
    pdfPages: List<PdfPageRef>,
    contentLength: Int,
    requestedChar: Int,
    resolvedPdfPageIndex: Int?,
): ReaderNavigationPlan {
    val charPosition = requestedChar.coerceIn(0, (contentLength - 1).coerceAtLeast(0))
    if (isPdf && pdfPages.isNotEmpty()) {
        val decoded = PdfPageCatalog.decodeProgress(pdfPages, charPosition, contentLength)
        val pageIndex = when (requestKind) {
            ReaderNavigationRequestKind.Annotation -> resolvedPdfPageIndex ?: decoded.pageIndex
            ReaderNavigationRequestKind.Toc -> resolvedPdfPageIndex ?: 0
        }.coerceIn(0, pdfPages.lastIndex)
        val fraction = when {
            requestKind == ReaderNavigationRequestKind.Toc -> 0f
            resolvedPdfPageIndex != null && resolvedPdfPageIndex != decoded.pageIndex -> 0f
            else -> decoded.fractionInPage
        }
        val progressChar = when (requestKind) {
            ReaderNavigationRequestKind.Annotation -> PdfPageCatalog.encodeProgress(
                pdfPages,
                pageIndex,
                fraction,
                contentLength,
            )
            ReaderNavigationRequestKind.Toc -> pdfPages[pageIndex].sourceOffset
        }
        return ReaderNavigationPlan.PdfContinuous(pageIndex, fraction, progressChar)
    }
    if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && hasPageSpecs) {
        return ReaderNavigationPlan.Paged(charPosition, resolvedPdfPageIndex)
    }
    if (isPdf && resolvedPdfPageIndex != null) {
        return ReaderNavigationPlan.PdfVertical(charPosition, resolvedPdfPageIndex)
    }
    return ReaderNavigationPlan.ChunkWindow(charPosition)
}
