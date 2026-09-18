package space.liushenme.markdownreader.importing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import space.liushenme.markdownreader.R

/**
 * 用 Android 自带 [PdfRenderer] 把 PDF 每一页栅格化成 JPEG，并把生成的资源 id 串成
 * 一份 HTML 正文（每页一个带尺寸的 `<img>`，页与页之间仅换行、无标题与空段）。
 *
 * 页图直接写入 [assetsDir]，避免把整本 PNG 堆在内存里。
 */
internal object PdfBookExtractor {

    private const val TAG = "PdfBookExtractor"
    private const val MAX_PAGES = 800
    private const val JPEG_QUALITY = 82
    const val PAGE_EXT = "jpg"

    fun extractToAssetsDir(
        context: Context,
        file: File,
        assetsDir: File,
        onProgress: (done: Int, total: Int, coverJpeg: ByteArray?) -> Unit = { _, _, _ -> },
    ): ExtractedBookText? {
        if (!file.isFile || file.length() <= 0) return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount.coerceAtMost(MAX_PAGES)
                    if (pageCount <= 0) return null
                    renderPages(
                        context = context,
                        renderer = renderer,
                        pageCount = pageCount,
                        assetsDir = assetsDir,
                        onProgress = onProgress,
                    )
                }
            }
        }.onFailure { Log.w(TAG, "PDF extract failed: ${file.absolutePath}", it) }
            .getOrNull()
    }

    private fun renderPages(
        context: Context,
        renderer: PdfRenderer,
        pageCount: Int,
        assetsDir: File,
        onProgress: (done: Int, total: Int, coverJpeg: ByteArray?) -> Unit,
    ): ExtractedBookText? {
        assetsDir.mkdirs()
        val body = StringBuilder()
        val toc = mutableListOf<ImportedTocEntry>()
        val pad = pageCount.toString().length.coerceAtLeast(3)
        val targetWidth = PdfPageRenderSize.targetWidth(context)
        var coverJpeg: ByteArray? = null
        var written = 0

        onProgress(0, pageCount, null)
        for (i in 0 until pageCount) {
            val pageId = "pdf_page_${(i + 1).toString().padStart(pad, '0')}.$PAGE_EXT"
            val outFile = File(assetsDir, pageId)
            val rendered = renderer.openPage(i).use { page ->
                renderPageToJpeg(page, targetWidth, outFile)
            }
            if (rendered == null) {
                onProgress(i + 1, pageCount, coverJpeg)
                continue
            }
            written++
            if (coverJpeg == null) {
                coverJpeg = runCatching { outFile.readBytes() }.getOrNull()
            }
            val title = context.getString(R.string.pdf_page_title, i + 1)
            val sourceOffset = body.length
            if (body.isNotEmpty()) body.append('\n')
            body.append(
                PdfReaderContent.buildPageImgTag(
                    src = "book-asset://$pageId",
                    width = rendered.width,
                    height = rendered.height,
                ),
            )
            toc += ImportedTocEntry(level = 2, title = title, sourceOffset = sourceOffset)
            onProgress(i + 1, pageCount, coverJpeg)
        }
        if (written <= 0 || body.isBlank()) return null
        return ExtractedBookText(
            body = body.toString().trim(),
            toc = toc,
            coverImageBytes = coverJpeg,
            assets = emptyMap(),
        )
    }

    private data class RenderedSize(val width: Int, val height: Int)

    private fun renderPageToJpeg(
        page: PdfRenderer.Page,
        targetWidth: Int,
        outFile: File,
    ): RenderedSize? {
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
            Canvas(bitmap).drawColor(Color.WHITE)
            val dest = Rect(0, 0, dstW, dstH)
            page.render(bitmap, dest, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            FileOutputStream(outFile).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    outFile.delete()
                    return null
                }
            }
            if (outFile.length() <= 0L) {
                outFile.delete()
                return null
            }
            RenderedSize(dstW, dstH)
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM while compressing ${outFile.name}", oom)
            outFile.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }
}
