package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 页图缓存的不变量：淘汰条目时只丢弃引用，绝不 recycle 位图。
 *
 * 解码出的 Bitmap 同时被 Compose（Image / asImageBitmap）持有并绘制，缓存不知道某个位图
 * 当前是否正在上屏。若淘汰时 recycle，正在显示的页会被画成空白；而 UI 只在解码宽度变化时
 * 才重新取图，于是表现为「放大后偶发空白、缩小后才恢复」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfPageBitmapCacheTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File.createTempFile("pdf-page-cache", "").let {
            it.delete()
            it.mkdirs()
            it
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun writePng(name: String, width: Int = 40, height: Int = 60): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = File(dir, name)
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        return file.absolutePath
    }

    @Test
    fun retainPaths_keepsReferencedBitmapAlive_andOnlyDropsOthers() {
        val firstPath = writePng("page-1.png")
        val secondPath = writePng("page-2.png")
        val cache = PdfPageBitmapCache(maxEntries = 4)
        val first = cache.decode(firstPath, 40)
        val second = cache.decode(secondPath, 40)
        assertNotNull(first)
        assertNotNull(second)

        // 模拟这两张图正在屏幕上绘制（Compose 持有同一 Bitmap 实例），随后 p1 离开视口。
        cache.retainPaths(setOf(secondPath))

        assertFalse("被淘汰的页图不得被 recycle，否则仍在上屏的页面会空白", first!!.isRecycled)
        assertFalse(second!!.isRecycled)
    }

    @Test
    fun lruEviction_doesNotRecycleEvictedBitmap() {
        val firstPath = writePng("page-1.png")
        val secondPath = writePng("page-2.png")
        val cache = PdfPageBitmapCache(maxEntries = 1)
        val first = cache.decode(firstPath, 40)
        assertNotNull(first)

        // maxEntries=1：插入第二张时淘汰第一张。
        cache.decode(secondPath, 40)

        assertFalse("LRU 淘汰只应丢弃引用，不得 recycle", first!!.isRecycled)
    }

    @Test
    fun clear_doesNotBreakOngoingDrawing() {
        val path = writePng("page-1.png")
        val cache = PdfPageBitmapCache(maxEntries = 2)
        val decoded = cache.decode(path, 40)
        assertNotNull(decoded)

        cache.clear()

        assertFalse("清理缓存（如退出阅读页）也不得 recycle 正在绘制的位图", decoded!!.isRecycled)
    }

    @Test
    fun decode_sameWidth_returnsCachedInstance() {
        val path = writePng("page-1.png")
        val cache = PdfPageBitmapCache(maxEntries = 2)
        val first = cache.decode(path, 40)
        val second = cache.decode(path, 40)
        assertSame(first, second)
    }

    @Test
    fun decode_changesWidth_reusesLiveBitmapWithoutRecyclingPrevious() {
        val path = writePng("page-1.png", width = 80, height = 120)
        val cache = PdfPageBitmapCache(maxEntries = 2)
        val base = cache.decode(path, 40)
        val sharp = cache.decode(path, 80)
        assertNotNull(base)
        assertNotNull(sharp)
        assertFalse("放大重解码不得回收上一倍率的页图", base!!.isRecycled)
        assertTrue(sharp!!.width >= base.width)
    }
}
