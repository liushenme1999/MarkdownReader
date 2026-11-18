package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import space.liushenme.markdownreader.importing.PdfPageRenderSize
import space.liushenme.markdownreader.markdown.ReaderMediaCache
import java.io.File
import java.util.LinkedHashMap

internal data class PdfBitmapKey(val path: String, val targetWidth: Int)

/**
 * 按目标宽度解码页图。放大松手后提高 [targetWidth] 即「重绘」；离开视口后 [retain] 回收。
 * 本地只有导入 PNG 时，清晰度上限是文件像素，不会凭空变清。
 *
 * 注意：淘汰时**只丢弃引用，绝不 `recycle()`**。
 * 解码出的 Bitmap 会同时被 Compose（`Image`/`asImageBitmap`）持有并绘制，缓存无从得知
 * 某个位图当前是否正在上屏；一旦 `recycle()` 掉仍在绘制的位图，Skia 会直接画不出内容，
 * 表现为「放大后偶发空白、缩小后才恢复」（因缩小改变解码宽度才会重新解码替换）。
 * 丢失缓存引用后由 GC 回收像素内存，既安全又能避免这类空白。
 */
internal class PdfPageBitmapCache(
    private val maxEntries: Int = ReaderMediaCache.policy.pdfBitmapEntries,
    /** 像素内存上限：默认取可用堆的 1/8，避免高倍率页图把进程撑爆。 */
    private val maxBytes: Long = defaultMaxBytes(),
) {
    private var cachedBytes: Long = 0L

    private val cache = object : LinkedHashMap<PdfBitmapKey, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PdfBitmapKey, Bitmap>?): Boolean {
            // 只淘汰引用；位图可能仍在屏幕上绘制，不能 recycle。
            return false
        }
    }

    @Synchronized
    fun decode(path: String, targetWidth: Int): Bitmap? {
        val width = targetWidth.coerceIn(1, PdfPageRenderSize.MAX_WIDTH)
        val key = PdfBitmapKey(path, width)
        cache[key]?.takeIf { !it.isRecycled }?.let { return it }
        val file = File(path)
        if (!file.isFile) return null
        val decoded = Companion.decodeScaled(file, width)
            // OOM 时清空缓存再按一半宽度重试：只返回 null 会让整页空白，
            // 降采样重试至少能显示可读的页图。
            ?: run {
                cache.clear()
                cachedBytes = 0L
                Companion.decodeScaled(file, (width / 2).coerceAtLeast(1))
            }
            ?: return null
        cache[key] = decoded
        cachedBytes += decoded.byteCount.toLong()
        trimToBudget()
        return decoded
    }

    @Synchronized
    fun retain(keep: Set<PdfBitmapKey>) {
        val stale = cache.keys.filterNot { it in keep }.toList()
        for (key in stale) {
            cache.remove(key)?.let { cachedBytes -= it.byteCount.toLong() }
        }
    }

    /** 可见页换解码宽度时，同一 path 的旧图先留着，避免闪空白。 */
    @Synchronized
    fun retainPaths(paths: Set<String>) {
        val stale = cache.keys.filterNot { it.path in paths }.toList()
        for (key in stale) {
            cache.remove(key)?.let { cachedBytes -= it.byteCount.toLong() }
        }
    }

    @Synchronized
    fun clear() {
        cache.clear()
        cachedBytes = 0L
    }

    /** 按 LRU 顺序淘汰到「条目数 + 像素内存」双上限以内。 */
    @Synchronized
    private fun trimToBudget() {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext() && (cache.size > maxEntries || (maxBytes > 0 && cachedBytes > maxBytes))) {
            val entry = iterator.next()
            cachedBytes -= entry.value.byteCount.toLong()
            iterator.remove()
        }
    }

    @Synchronized
    internal fun cachedBytesForTest(): Long = cachedBytes

    companion object {
        /** 可用堆的 1/8，至少 16MB，避免小内存设备上限制过紧。 */
        internal fun defaultMaxBytes(): Long =
            (Runtime.getRuntime().maxMemory() / 8).coerceAtLeast(16L * 1024L * 1024L)

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

        internal fun decodeScaled(file: File, targetWidth: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val srcW = bounds.outWidth
            if (srcW <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(srcW, targetWidth)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            // 高倍率下解码较大位图可能 OOM：返回 null 让上层沿用已有画面，
            // 而不是抛错中断组合导致整页空白或闪退。
            return try {
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }
}
