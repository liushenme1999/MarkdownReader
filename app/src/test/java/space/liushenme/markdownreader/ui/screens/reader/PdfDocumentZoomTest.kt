package space.liushenme.markdownreader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfDocumentZoomTest {

    @Test
    fun documentScrollAfterZoom_keepsFocalPoint() {
        val scrollY = 400f
        val focalY = 200f
        val next = pdfDocumentScrollAfterZoom(scrollY, focalY, oldScale = 1f, newScale = 2f)
        assertEquals(1000f, next, 0.01f)
    }

    @Test
    fun documentScrollAfterZoom_restoreWhenZoomingOut() {
        val zoomed = pdfDocumentScrollAfterZoom(1000f, 200f, oldScale = 2f, newScale = 1f)
        assertEquals(400f, zoomed, 0.01f)
    }

    @Test
    fun clampDocumentPanX_leftAlignedDoesNotExposeSides() {
        assertEquals(0f, clampPdfDocumentPanX(-10f, 1000f, 1f), 0.01f)
        assertEquals(-1000f, clampPdfDocumentPanX(-9999f, 1000f, 2f), 0.01f)
        assertEquals(0f, clampPdfDocumentPanX(80f, 1000f, 2f), 0.01f)
        assertEquals(-400f, clampPdfDocumentPanX(-400f, 1000f, 1.4f), 0.01f)
    }

    @Test
    fun panXAfterZoom_keepsFocalPointAndStaysOnPaper() {
        val viewport = 1000f
        val next = pdfDocumentPanXAfterZoom(panX = 0f, focalX = 500f, oldScale = 1f, newScale = 2f)
        assertEquals(-500f, next, 0.01f)
        assertEquals(-500f, clampPdfDocumentPanX(next, viewport, 2f), 0.01f)
        val back = pdfDocumentPanXAfterZoom(next, focalX = 500f, oldScale = 2f, newScale = 1f)
        assertEquals(0f, back, 0.01f)
    }

    @Test
    fun pageHeights_scaleWithZoom() {
        val heights1 = pdfPageHeights(1000f, 1f, listOf(1000 to 2000))
        val heights2 = pdfPageHeights(1000f, 2f, listOf(1000 to 2000))
        assertEquals(2000f, heights1[0], 0.01f)
        assertEquals(4000f, heights2[0], 0.01f)
    }

    @Test
    fun displaySize_scalesWidthAndHeightTogether() {
        val intrinsW = 1000
        val intrinsH = 2000
        val viewport = 1080f
        val w1 = pdfPageDisplayWidth(viewport, 1f)
        val w2 = pdfPageDisplayWidth(viewport, 2.5f)
        val h1 = pdfPageHeightPx(viewport, 1f, intrinsW, intrinsH)
        val h2 = pdfPageHeightPx(viewport, 2.5f, intrinsW, intrinsH)
        assertEquals(viewport, w1, 0.01f)
        assertEquals(viewport * 2.5f, w2, 0.01f)
        assertEquals(w1 / h1, w2 / h2, 0.0001f)
        assertEquals(intrinsW.toFloat() / intrinsH, w2 / h2, 0.0001f)
    }

    @Test
    fun scrollToOffset_andBack() {
        val heights = floatArrayOf(1000f, 2000f, 1500f)
        val gap = 16f
        val y = pdfScrollYForPageFraction(1, 0.25f, heights, gap)
        val (index, offset) = pdfScrollToOffset(y, heights, gap)
        assertEquals(1, index)
        assertEquals((2000f * 0.25f).toInt(), offset)
        val anchor = pdfAnchorAtScrollY(y, heights, gap)
        assertEquals(1, anchor.pageIndex)
        assertEquals(0.25f, anchor.fractionInPage, 0.01f)
    }

    @Test
    fun scrollToOffset_doesNotExceedPageHeight() {
        val heights = floatArrayOf(800f, 800f)
        val (index, offset) = pdfScrollToOffset(10_000f, heights, gap = 0f)
        assertEquals(1, index)
        assertEquals(800, offset)
    }

    @Test
    fun visiblePages_includeNeighbors() {
        val heights = floatArrayOf(800f, 800f, 800f, 800f)
        val gap = 0f
        val visible = pdfVisiblePageIndices(scrollY = 900f, viewportHeight = 700f, heights, gap)
        assertTrue(visible.first <= 1)
        assertTrue(visible.last >= 2)
    }

    @Test
    fun reachedEnd_whenScrolledToBottom() {
        val heights = floatArrayOf(1000f, 1000f)
        val gap = 0f
        assertFalse(pdfDocumentReachedEnd(0f, 800f, heights, gap))
        assertTrue(pdfDocumentReachedEnd(1200f, 800f, heights, gap))
    }

    @Test
    fun decodeWidth_capsAtMax() {
        assertEquals(2160, pdfDecodeWidthForScale(2000f, 8f, 2160))
        assertEquals(1000, pdfDecodeWidthForScale(1000f, 1f, 2160))
    }

    @Test
    fun pinchVisualScale_keepsProductInZoomRange() {
        assertEquals(2f, pdfPinchVisualScale(1f, 2f), 0.01f)
        assertEquals(1f, pdfPinchVisualScale(8f, 2f), 0.01f)
        assertEquals(0.5f, pdfPinchVisualScale(2f, 0.4f), 0.01f)
    }
}
