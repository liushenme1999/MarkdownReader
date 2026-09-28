package space.liushenme.markdownreader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.liushenme.markdownreader.model.ReaderPageTurnMode

class ReaderNavigationPlanTest {

    private val pdfPages = listOf(
        PdfPageRef(0, "page-0", 0, "page-0.png"),
        PdfPageRef(1, "page-1", 100, "page-1.png"),
        PdfPageRef(2, "page-2", 200, "page-2.png"),
    )

    @Test
    fun annotationInContinuousPdf_preservesFractionWithinPage() {
        val plan = plan(
            requestKind = ReaderNavigationRequestKind.Annotation,
            isPdf = true,
            pdfPages = pdfPages,
            requestedChar = 150,
        ) as ReaderNavigationPlan.PdfContinuous

        assertEquals(1, plan.pageIndex)
        assertEquals(0.5f, plan.fractionInPage, 0.001f)
        assertEquals(149, plan.progressChar)
    }

    @Test
    fun tocInContinuousPdf_alignsResolvedPageToTop() {
        val plan = plan(
            requestKind = ReaderNavigationRequestKind.Toc,
            isPdf = true,
            pdfPages = pdfPages,
            requestedChar = 150,
            resolvedPdfPageIndex = 2,
        ) as ReaderNavigationPlan.PdfContinuous

        assertEquals(2, plan.pageIndex)
        assertEquals(0f, plan.fractionInPage, 0f)
        assertEquals(200, plan.progressChar)
    }

    @Test
    fun horizontalPageMode_usesPagedPlan() {
        val plan = plan(
            pageTurnMode = ReaderPageTurnMode.HorizontalSwipe,
            hasPageSpecs = true,
            requestedChar = 480,
        )

        assertEquals(ReaderNavigationPlan.Paged(480, null), plan)
    }

    @Test
    fun legacyPdfWithoutPageCatalog_usesVerticalPdfPlan() {
        val plan = plan(
            isPdf = true,
            requestedChar = 320,
            resolvedPdfPageIndex = 4,
        )

        assertEquals(ReaderNavigationPlan.PdfVertical(320, 4), plan)
    }

    @Test
    fun verticalMarkdown_usesChunkWindowAndClampsTarget() {
        val plan = plan(requestedChar = 1_500)

        assertEquals(ReaderNavigationPlan.ChunkWindow(999), plan)
    }

    @Test
    fun pagedRouteWinsOverLegacyVerticalPdfFallback() {
        val plan = plan(
            isPdf = true,
            pageTurnMode = ReaderPageTurnMode.HorizontalSwipe,
            hasPageSpecs = true,
            requestedChar = 320,
            resolvedPdfPageIndex = 4,
        )

        assertTrue(plan is ReaderNavigationPlan.Paged)
    }

    private fun plan(
        requestKind: ReaderNavigationRequestKind = ReaderNavigationRequestKind.Annotation,
        isPdf: Boolean = false,
        pageTurnMode: ReaderPageTurnMode = ReaderPageTurnMode.VerticalScroll,
        hasPageSpecs: Boolean = false,
        pdfPages: List<PdfPageRef> = emptyList(),
        requestedChar: Int,
        resolvedPdfPageIndex: Int? = null,
    ): ReaderNavigationPlan = createReaderNavigationPlan(
        requestKind = requestKind,
        isPdf = isPdf,
        pageTurnMode = pageTurnMode,
        hasPageSpecs = hasPageSpecs,
        pdfPages = pdfPages,
        contentLength = 1_000,
        requestedChar = requestedChar,
        resolvedPdfPageIndex = resolvedPdfPageIndex,
    )
}
