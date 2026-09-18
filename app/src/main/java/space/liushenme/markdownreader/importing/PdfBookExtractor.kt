package space.liushenme.markdownreader.importing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import space.liushenme.markdownreader.R

/**
 * 用 Android 自带 [PdfRenderer] 把 PDF 每一页栅格化成 PNG，并把生成的资源 id 串成
 * 一份 HTML 正文（每页一个带尺寸的 `<img>`，页与页之间仅换行、无标题与空段）。
 *
 * 设计取舍：
 * - 不做文本层提取（移动端做精确文本提取需要 MinerU / Marker 这种模型驱动方案，
 *   不适合放在客户端）。此处只承担「能在书架里翻、能滑、能看清排版」的预览职责。
 * - 渲染分辨率按屏幕宽度 ×2（夹在 1080–2160），等比缩放高度；阅读时 Markwon 的图片
 *   插件会按容器宽度二次缩放。
 * - 限制页数 [MAX_PAGES] 与渲染像素 [PdfPageRenderSize.MAX_PIXELS] 防止超大 PDF 撑爆 IO / 内存。
 */
internal object PdfBookExtractor {

    private const val TAG = "PdfBookExtractor"
    private const val MAX_PAGES = 800
    /** PNG 压缩质量：PNG 是无损，参数对体积影响有限，保留默认。 */
    private const val PNG_QUALITY = 100

    fun extract(context: Context, file: File): ExtractedBookText? {
        if (!file.isFile || file.length() <= 0) return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount.coerceAtMost(MAX_PAGES)
                    if (pageCount <= 0) return null
                    renderPagesToMarkdown(context, renderer, pageCount)
                }
            }
        }.onFailure { Log.w(TAG, "PDF extract failed: ${file.absolutePath}", it) }
            .getOrNull()
    }

    private fun renderPagesToMarkdown(
        context: Context,
        renderer: PdfRenderer,
        pageCount: Int,
    ): ExtractedBookText {
        val assets = LinkedHashMap<String, ByteArray>(pageCount)
        val body = StringBuilder()
        val toc = mutableListOf<ImportedTocEntry>()
        val pad = pageCount.toString().length.coerceAtLeast(3)
        val targetWidth = PdfPageRenderSize.targetWidth(context)

        for (i in 0 until pageCount) {
            val pageId = "pdf_page_${(i + 1).toString().padStart(pad, '0')}.png"
            val rendered = renderer.openPage(i).use { page ->
                renderPageToPng(page, targetWidth)
            } ?: continue
            assets[pageId] = rendered.png

            val title = context.getString(R.string.pdf_page_title, i + 1)
            val sourceOffset = body.length
            if (i > 0) body.append('\n')
            body.append(
                PdfReaderContent.buildPageImgTag(
                    src = "book-asset://${pageId}",
                    width = rendered.width,
                    height = rendered.height,
                ),
            )
            toc += ImportedTocEntry(level = 2, title = title, sourceOffset = sourceOffset)
        }
        return ExtractedBookText(
            body = body.toString().trim(),
            toc = toc,
            coverImageBytes = assets.values.firstOrNull(),
            assets = assets
        )
    }

    private data class RenderedPage(val png: ByteArray, val width: Int, val height: Int)

    private fun renderPageToPng(page: PdfRenderer.Page, targetWidth: Int): RenderedPage? {
        val fitted = PdfPageRenderSize.fitPage(
            srcWidth = page.width.coerceAtLeast(1),
            srcHeight = page.height.coerceAtLeast(1),
            targetWidth = targetWidth,
        )
        val dstW = fitted.width
        val dstH = fitted.height
        val bitmap = try {
            Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM while allocating bitmap ${dstW}x${dstH}", oom)
            return null
        }
        return try {
            // PdfRenderer 默认透明背景，部分 PDF 没有显式白底，先填白避免阅读时显示发灰
            Canvas(bitmap).drawColor(Color.WHITE)
            val dest = Rect(0, 0, dstW, dstH)
            page.render(bitmap, dest, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val out = ByteArrayOutputStream(64 * 1024)
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) return null
            RenderedPage(png = out.toByteArray(), width = dstW, height = dstH)
        } finally {
            bitmap.recycle()
        }
    }
}
