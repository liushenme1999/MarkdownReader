package space.liushenme.markdownreader.importing

import android.content.Context
import kotlin.math.sqrt

/** 导入时 PDF 页图栅格尺寸：按屏幕加宽，并限制单页像素。 */
internal object PdfPageRenderSize {
    const val MIN_WIDTH = 1080
    const val MAX_WIDTH = 2160
    const val MAX_PIXELS = 2160 * 3840

    fun targetWidth(screenWidthPx: Int): Int =
        (screenWidthPx.coerceAtLeast(1) * 2).coerceIn(MIN_WIDTH, MAX_WIDTH)

    fun targetWidth(context: Context): Int =
        targetWidth(context.resources.displayMetrics.widthPixels)

    data class Size(val width: Int, val height: Int)

    fun fitPage(
        srcWidth: Int,
        srcHeight: Int,
        targetWidth: Int = MIN_WIDTH,
        maxPixels: Int = MAX_PIXELS,
    ): Size {
        val srcW = srcWidth.coerceAtLeast(1)
        val srcH = srcHeight.coerceAtLeast(1)
        var dstW = targetWidth.coerceAtLeast(1)
        var dstH = (srcH.toDouble() * dstW / srcW).toInt().coerceAtLeast(1)
        val pixels = dstW.toLong() * dstH
        if (pixels > maxPixels) {
            val scale = sqrt(maxPixels.toDouble() / pixels)
            dstW = (dstW * scale).toInt().coerceAtLeast(1)
            dstH = (dstH * scale).toInt().coerceAtLeast(1)
        }
        return Size(dstW, dstH)
    }
}
