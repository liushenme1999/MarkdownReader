package space.liushenme.markdownreader.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageRenderSizeTest {

    @Test
    fun targetWidth_clampsTwoTimesScreenWidth() {
        assertEquals(PdfPageRenderSize.MIN_WIDTH, PdfPageRenderSize.targetWidth(320))
        assertEquals(1440, PdfPageRenderSize.targetWidth(720))
        assertEquals(PdfPageRenderSize.MAX_WIDTH, PdfPageRenderSize.targetWidth(2000))
    }

    @Test
    fun fitPage_keepsAspectAndRespectsPixelCap() {
        val fitted = PdfPageRenderSize.fitPage(
            srcWidth = 595,
            srcHeight = 842,
            targetWidth = 2160,
        )
        assertEquals(2160, fitted.width)
        val expectedH = (842.0 * 2160 / 595).toInt()
        assertEquals(expectedH, fitted.height)
        assertTrue(fitted.width.toLong() * fitted.height <= PdfPageRenderSize.MAX_PIXELS)
    }

    @Test
    fun fitPage_scalesDownPosterToMaxPixels() {
        val fitted = PdfPageRenderSize.fitPage(
            srcWidth = 100,
            srcHeight = 10_000,
            targetWidth = 2160,
            maxPixels = 1_000_000,
        )
        assertTrue(fitted.width.toLong() * fitted.height <= 1_000_000)
        assertTrue(fitted.width >= 1)
        assertTrue(fitted.height >= 1)
        val ratio = fitted.height.toDouble() / fitted.width
        assertEquals(100.0, ratio, 2.0)
    }
}
