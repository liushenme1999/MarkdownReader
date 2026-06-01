package space.liushenme.markdownreader.markdown

import android.graphics.Bitmap
import android.graphics.Color

internal object DiagramBitmapUtils {

    /** 裁掉底部多余白边（WebView 截图偶发比 SVG 高出数像素～数百像素）。 */
    fun cropTrailingWhiteStrip(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 4) return source
        var lastContentRow = h - 1
        scan@ for (y in h - 1 downTo 0) {
            var nonWhite = false
            var x = 0
            while (x < w) {
                if (source.getPixel(x, y) != Color.WHITE) {
                    nonWhite = true
                    break
                }
                x += 8
            }
            if (nonWhite) {
                lastContentRow = y
                break@scan
            }
        }
        val trimmedH = (lastContentRow + 2).coerceIn(1, h)
        if (trimmedH >= h - 2) return source
        return Bitmap.createBitmap(source, 0, 0, w, trimmedH)
    }

    fun scaledDrawableBounds(
        bitmap: Bitmap,
        lineWidthPx: Int,
    ): Pair<Int, Int> {
        val width = lineWidthPx.coerceAtLeast(1)
        val height = (width * bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1))
            .toInt()
            .coerceAtLeast(1)
        return width to height
    }
}
