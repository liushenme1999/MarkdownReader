package space.liushenme.markdownreader.markdown

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DiagramBitmapUtilsTest {

    @Test
    fun cropTrailingWhiteStrip_removesBottomWhitespace() {
        val bmp = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        for (y in 0 until 80) {
            for (x in 0 until 100) {
                bmp.setPixel(x, y, Color.BLACK)
            }
        }
        val cropped = DiagramBitmapUtils.cropTrailingWhiteStrip(bmp)
        assertTrue(cropped.height < 200)
        assertTrue(cropped.height in 78..85)
    }

    @Test
    fun scaledDrawableBounds_preservesAspectWithinLineWidth() {
        val (w, h) = DiagramBitmapUtils.scaledDrawableBounds(
            bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888),
            lineWidthPx = 400,
        )
        assertEquals(400, w)
        assertEquals(200, h)
    }
}
