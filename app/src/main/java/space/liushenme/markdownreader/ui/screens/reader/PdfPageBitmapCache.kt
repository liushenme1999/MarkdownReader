package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import space.liushenme.markdownreader.importing.PdfPageRenderSize
import java.io.File
import java.util.LinkedHashMap

internal data class PdfBitmapKey(val path: String, val targetWidth: Int)

/**
 * 按目标宽度解码页图。放大松手后提高 [targetWidth] 即「重绘」；离开视口后 [retain] 回收。
 * 本地只有导入 PNG 时，清晰度上限是文件像素，不会凭空变清。
 */
internal class PdfPageBitmapCache(
    private val maxEntries: Int = 8,
) {
    private val cache = object : LinkedHashMap<PdfBitmapKey, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PdfBitmapKey, Bitmap>?): Boolean {
            if (size <= maxEntries) return false
            eldest?.value?.takeIf { !it.isRecycled }?.recycle()
            return true
        }
    }

    @Synchronized
    fun decode(path: String, targetWidth: Int): Bitmap? {
        val width = targetWidth.coerceIn(1, PdfPageRenderSize.MAX_WIDTH)
        val key = PdfBitmapKey(path, width)
        cache[key]?.takeIf { !it.isRecycled }?.let { return it }
        val file = File(path)
        if (!file.isFile) return null
        val decoded = decodeScaled(file, width) ?: return null
        cache[key] = decoded
        return decoded
    }

    @Synchronized
    fun retain(keep: Set<PdfBitmapKey>) {
        val stale = cache.keys.filterNot { it in keep }.toList()
        for (key in stale) {
            cache.remove(key)?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    /** 可见页换解码宽度时，同一 path 的旧图先留着，避免闪空白。 */
    @Synchronized
    fun retainPaths(paths: Set<String>) {
        val stale = cache.keys.filterNot { it.path in paths }.toList()
        for (key in stale) {
            cache.remove(key)?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    @Synchronized
    fun clear() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
    }

    companion object {
        fun decodeBounds(path: String): Pair<Int, Int>? {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            val w = opts.outWidth
            val h = opts.outHeight
            if (w <= 0 || h <= 0) return null
            return w to h
        }

        internal fun sampleSize(srcWidth: Int, targetWidth: Int): Int {
            val src = srcWidth.coerceAtLeast(1)
            val dest = targetWidth.coerceAtLeast(1)
            var sample = 1
            while (src / (sample * 2) >= dest) {
                sample *= 2
            }
            return sample.coerceAtLeast(1)
        }

        private fun decodeScaled(file: File, targetWidth: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val srcW = bounds.outWidth
            if (srcW <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(srcW, targetWidth)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeFile(file.absolutePath, opts)
        }
    }
}
