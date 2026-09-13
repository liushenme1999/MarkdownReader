package space.liushenme.markdownreader.markdown

import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.ui.theme.ReadingTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderHighlightSurfaceTest {

    @Test
    fun defaultPaper_matchesPaperPreset() {
        assertEquals(ReadingTheme.Paper.backgroundColor.toArgb(), ReaderHighlightSurface.DEFAULT_PAPER_ARGB)
    }

    @Test
    fun lightPaper_fillIsDarkerThanPaper() {
        val paper = ReadingTheme.White.backgroundColor.toArgb()
        val fill = ReaderHighlightSurface.fill(paper)
        assertFalse(ReaderHighlightSurface.isDarkPaper(paper))
        assertTrue(ColorUtils.calculateLuminance(fill) < ColorUtils.calculateLuminance(paper))
        assertTrue(ColorUtils.calculateLuminance(ReaderHighlightSurface.stroke(paper)) < ColorUtils.calculateLuminance(fill))
    }

    @Test
    fun darkPaper_fillIsLighterThanPaper() {
        val paper = ReadingTheme.Dark.backgroundColor.toArgb()
        val fill = ReaderHighlightSurface.fill(paper)
        assertTrue(ReaderHighlightSurface.isDarkPaper(paper))
        assertTrue(ColorUtils.calculateLuminance(fill) > ColorUtils.calculateLuminance(paper))
        assertTrue(ColorUtils.calculateLuminance(ReaderHighlightSurface.stroke(paper)) > ColorUtils.calculateLuminance(fill))
    }

    @Test
    fun greenPaper_keepsGreenHue() {
        val paper = ReadingTheme.Green.backgroundColor.toArgb()
        val fill = ReaderHighlightSurface.fill(paper)
        assertNotEquals(Color.parseColor("#F3F6FA"), fill)
        assertTrue(Color.green(fill) > Color.blue(fill))
        assertTrue(Color.green(fill) > Color.red(fill))
    }
}
