package space.liushenme.markdownreader.ui.screens.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReaderZoomTest {

    @Test
    fun clampPdfZoom_staysWithinRange() {
        assertEquals(PDF_ZOOM_MIN, clampPdfZoom(0.2f), 0.001f)
        assertEquals(PDF_ZOOM_MAX, clampPdfZoom(20f), 0.001f)
        assertEquals(2.5f, clampPdfZoom(2.5f), 0.001f)
    }

    @Test
    fun clampPdfPan_zeroWhenNotZoomed() {
        val pan = clampPdfPan(Offset(80f, 40f), scale = 1f, viewport = Size(1000f, 1800f))
        assertEquals(0f, pan.x, 0.001f)
        assertEquals(0f, pan.y, 0.001f)
    }

    @Test
    fun clampPdfPan_limitsToExtraScaledSize() {
        val viewport = Size(1000f, 1800f)
        val scale = 2f
        val pan = clampPdfPan(Offset(9999f, -9999f), scale, viewport)
        assertEquals(500f, pan.x, 0.001f)
        assertEquals(-900f, pan.y, 0.001f)
        assertTrue(pdfZoomIsActive(scale))
        assertFalse(pdfZoomIsActive(1f))
    }

    @Test
    fun snapPdfZoom_collapsesNearMin() {
        assertEquals(PDF_ZOOM_MIN, snapPdfZoom(1.04f), 0.001f)
        assertEquals(PDF_ZOOM_MIN, snapPdfZoom(PDF_SNAP_TO_MIN_SCALE - 0.001f), 0.001f)
        assertEquals(1.5f, snapPdfZoom(1.5f), 0.001f)
    }

    @Test
    fun pdfDoubleTapTargetScale_togglesBetweenMinAnd2_5() {
        assertEquals(PDF_DOUBLE_TAP_ZOOM, pdfDoubleTapTargetScale(1f), 0.001f)
        assertEquals(PDF_ZOOM_MIN, pdfDoubleTapTargetScale(PDF_DOUBLE_TAP_ZOOM), 0.001f)
        assertEquals(PDF_ZOOM_MIN, pdfDoubleTapTargetScale(4f), 0.001f)
    }

    @Test
    fun pdfZoomOffsetForFocalPoint_keepsTapCenteredWhenZooming() {
        val viewport = Size(1000f, 1800f)
        val tap = Offset(200f, 300f)
        val scale = PDF_DOUBLE_TAP_ZOOM
        val offset = pdfZoomOffsetForFocalPoint(tap, scale, viewport)
        val center = Offset(500f, 900f)
        val expected = clampPdfPan((tap - center) * (1f - scale), scale, viewport)
        assertEquals(expected.x, offset.x, 0.001f)
        assertEquals(expected.y, offset.y, 0.001f)
    }

    @Test
    fun pdfHorizontalOverscrollTurn_nextPageWhenPanningPastRightEdge() {
        val viewport = Size(1000f, 1800f)
        val scale = 2f
        val maxX = 500f
        val atRight = Offset(-maxX, 0f)
        val turn = pdfHorizontalOverscrollTurn(
            currentOffset = atRight,
            unclampedOffset = Offset(-maxX - 80f, 0f),
            scale = scale,
            viewport = viewport,
            overflowAccumX = 0f,
            edgeSlop = 40f,
        )
        assertEquals(1, turn)
    }

    @Test
    fun pdfHorizontalOverscrollTurn_previousPageWhenPanningPastLeftEdge() {
        val viewport = Size(1000f, 1800f)
        val scale = 2f
        val maxX = 500f
        val atLeft = Offset(maxX, 0f)
        val turn = pdfHorizontalOverscrollTurn(
            currentOffset = atLeft,
            unclampedOffset = Offset(maxX + 80f, 0f),
            scale = scale,
            viewport = viewport,
            overflowAccumX = 0f,
            edgeSlop = 40f,
        )
        assertEquals(-1, turn)
    }

    @Test
    fun pdfHorizontalOverscrollTurn_zeroWhenNotAtEdge() {
        val viewport = Size(1000f, 1800f)
        val turn = pdfHorizontalOverscrollTurn(
            currentOffset = Offset.Zero,
            unclampedOffset = Offset(-80f, 0f),
            scale = 2f,
            viewport = viewport,
            overflowAccumX = 0f,
            edgeSlop = 40f,
        )
        assertEquals(0, turn)
    }
}
