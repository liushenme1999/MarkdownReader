package space.liushenme.markdownreader.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/** legado 默认阅读背景图（`assets/bg/`）的解码与平均色缓存。 */
object ReaderBackgroundImages {
    const val ASSET_DIR = "bg"

    val defaultAssets: List<String> = listOf(
        "午后沙滩.jpg",
        "宁静夜色.jpg",
        "山水墨影.jpg",
        "山水画.jpg",
        "护眼漫绿.jpg",
        "新羊皮纸.jpg",
        "明媚倾城.jpg",
        "深宫魅影.jpg",
        "清新时光.jpg",
        "羊皮纸1.jpg",
        "羊皮纸2.jpg",
        "羊皮纸3.jpg",
        "羊皮纸4.jpg",
        "边彩画布.jpg",
    )

    private val meanColorCache = mutableMapOf<String, Color>()
    private val displayCache = mutableMapOf<String, Bitmap>()
    private val thumbCache = mutableMapOf<String, Bitmap>()
    private val lock = Any()

    fun displayName(assetName: String): String = assetName.substringBeforeLast('.')

    fun meanColor(context: Context, assetName: String): Color? {
        synchronized(lock) {
            meanColorCache[assetName]?.let { return it }
        }
        val sample = decodeAsset(context, assetName, 200, 200) ?: return null
        val color = sample.readerMeanColor()
        sample.recycle()
        synchronized(lock) {
            meanColorCache[assetName] = color
        }
        return color
    }

    fun loadDisplay(context: Context, assetName: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val width = reqWidth.coerceAtLeast(1)
        val height = reqHeight.coerceAtLeast(1)
        val key = "$assetName|$width|$height"
        synchronized(lock) {
            displayCache[key]?.takeUnless { it.isRecycled }?.let { return it }
        }
        val bitmap = decodeAsset(context, assetName, width, height) ?: return null
        synchronized(lock) {
            meanColorCache.getOrPut(assetName) { bitmap.readerMeanColor() }
            displayCache[key] = bitmap
        }
        return bitmap
    }

    fun loadThumbnail(context: Context, assetName: String): Bitmap? {
        synchronized(lock) {
            thumbCache[assetName]?.takeUnless { it.isRecycled }?.let { return it }
        }
        val bitmap = decodeAsset(context, assetName, 160, 200) ?: return null
        synchronized(lock) {
            meanColorCache.getOrPut(assetName) { bitmap.readerMeanColor() }
            thumbCache[assetName] = bitmap
        }
        return bitmap
    }

    private fun decodeAsset(
        context: Context,
        assetName: String,
        reqWidth: Int,
        reqHeight: Int,
    ): Bitmap? {
        val path = "$ASSET_DIR/$assetName"
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
            }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}

/** 对齐 legado `Bitmap.getMeanColor()`：横向 0–99%、纵向 70–99%。 */
internal fun Bitmap.readerMeanColor(): Color {
    val width = this.width
    val height = this.height
    if (width <= 0 || height <= 0) return Color.Gray
    var pixelSumRed = 0
    var pixelSumGreen = 0
    var pixelSumBlue = 0
    for (i in 0..99) {
        for (j in 70..99) {
            val x = (i * width / 100f).roundToInt().coerceIn(0, width - 1)
            val y = (j * height / 100f).roundToInt().coerceIn(0, height - 1)
            val pixel = getPixel(x, y)
            pixelSumRed += AndroidColor.red(pixel)
            pixelSumGreen += AndroidColor.green(pixel)
            pixelSumBlue += AndroidColor.blue(pixel)
        }
    }
    return Color(
        red = (pixelSumRed / 3000 + 3).coerceAtMost(255),
        green = (pixelSumGreen / 3000 + 3).coerceAtMost(255),
        blue = (pixelSumBlue / 3000 + 3).coerceAtMost(255),
    )
}
