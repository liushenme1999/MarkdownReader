package space.liushenme.markdownreader.ui.screens.reader

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.ui.theme.ReadingTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PdfPageAppearanceTest {

    @Test
    fun shouldInvertPdfPages_onlyWhenBackgroundIsDark() {
        assertTrue(shouldInvertPdfPages(ReadingTheme.Dark.backgroundColor.luminance()))
        assertFalse(shouldInvertPdfPages(ReadingTheme.Paper.backgroundColor.luminance()))
        assertFalse(shouldInvertPdfPages(ReadingTheme.White.backgroundColor.luminance()))
        assertFalse(shouldInvertPdfPages(ReadingTheme.Green.backgroundColor.luminance()))
        assertFalse(shouldInvertPdfPages(ReadingTheme.Sepia.backgroundColor.luminance()))
        assertTrue(shouldInvertPdfPages(0f))
        assertFalse(shouldInvertPdfPages(PDF_DARK_PAGE_LUMINANCE_THRESHOLD))
    }

    @Test
    fun pdfPageInvertMatrix_negatesRgbKeepsAlpha() {
        val values = PDF_PAGE_INVERT_MATRIX_VALUES
        assertEquals(20, values.size)
        assertEquals(-1f, values[0], 0.001f)
        assertEquals(255f, values[4], 0.001f)
        assertEquals(-1f, values[6], 0.001f)
        assertEquals(255f, values[9], 0.001f)
        assertEquals(-1f, values[12], 0.001f)
        assertEquals(255f, values[14], 0.001f)
        assertEquals(1f, values[18], 0.001f)
        assertEquals(0f, values[19], 0.001f)
    }
}
