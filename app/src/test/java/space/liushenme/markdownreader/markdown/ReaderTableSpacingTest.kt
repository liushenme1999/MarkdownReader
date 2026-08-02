package space.liushenme.markdownreader.markdown

import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTableSpacingTest {

    @Test
    fun compensateLineSpacing_shrinksMetricsSoMultiplierRecoversHeight() {
        val fm = Paint.FontMetricsInt().apply {
            ascent = -300
            descent = 0
            top = -300
            bottom = 0
        }
        ReaderTableSpacing.compensateLineSpacing(fm, 1.5f)
        val compensated = fm.descent - fm.ascent
        assertEquals(200, compensated)
        assertTrue(fm.ascent < 0)
        assertEquals(0, fm.descent)
    }

    @Test
    fun compensateLineSpacing_noopWhenMultiplierIsOne() {
        val fm = Paint.FontMetricsInt().apply {
            ascent = -300
            descent = 0
            top = -300
            bottom = 0
        }
        ReaderTableSpacing.compensateLineSpacing(fm, 1f)
        assertEquals(-300, fm.ascent)
        assertEquals(0, fm.descent)
    }
}
