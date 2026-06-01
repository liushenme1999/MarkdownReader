package space.liushenme.markdownreader.markdown

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.TextView
import space.liushenme.markdownreader.MarkdownReaderApp
import space.liushenme.markdownreader.ui.screens.reader.SafeReaderTextView
import space.liushenme.markdownreader.ui.screens.reader.captureTextViewScrollAnchor
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.ui.screens.reader.reapplyPendingScrollCharOffsetIfAny
import space.liushenme.markdownreader.ui.screens.reader.schedulePendingScrollReapply
import space.liushenme.markdownreader.ui.screens.reader.scrollTextViewPreservingScrollY
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** 异步加载 diagram:// 图片并更新 [AsyncDrawable]（不阻塞主线程）。 */
internal object DiagramImageLoader {

    private const val TAG = "DiagramImageLoader"
    private const val DISK_CACHE_DIR = "diagram_image_cache"
    private val DIAGRAM_URI = Regex("""diagram://([A-Za-z0-9_-]+)/([A-Za-z0-9._-]+)""")

    private val mainHandler = Handler(Looper.getMainLooper())
    private val decodeExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "diagram-decode").apply { isDaemon = true }
    }
    private val inflight = ConcurrentHashMap<String, AtomicInteger>()
    private val cancelled = Collections.newSetFromMap(ConcurrentHashMap<AsyncDrawable, Boolean>())
    private val bitmapCache = LruCache<String, android.graphics.Bitmap>(24)

    fun placeholder(context: Context, contentWidthPx: Int): DiagramPlaceholderDrawable {
        val density = context.resources.displayMetrics.density
        val h = (density * 160).toInt().coerceAtLeast(80)
        return DiagramPlaceholderDrawable(contentWidthPx.coerceAtLeast(1), h)
    }

    fun cachedDrawable(context: Context, destination: String, contentWidthPx: Int): Drawable? {
        val payloadId = payloadIdFromDestination(destination) ?: return null
        val bitmap = bitmapCache.get(payloadId)?.takeIf { !it.isRecycled }
            ?: loadDiskBitmap(context, payloadId)?.also { bitmapCache.put(payloadId, it) }
            ?: return null
        val cropped = DiagramBitmapUtils.cropTrailingWhiteStrip(bitmap)
        val width = contentWidthPx.coerceAtLeast(1)
        val height = (width * cropped.height.toFloat() / cropped.width.coerceAtLeast(1)).toInt()
            .coerceAtLeast(1)
        return BitmapDrawable(context.resources, cropped).apply {
            setBounds(0, 0, width, height)
        }
    }

    fun clearDiskCache(context: Context): Long {
        bitmapCache.evictAll()
        val root = diskRoot(context)
        if (!root.exists()) return 0L
        val bytes = directorySize(root)
        root.deleteRecursively()
        return bytes
    }

    /** Markwon setText 完成后扫描 diagram 占位并启动渲染。 */
    fun scheduleForTextView(textView: TextView, attempt: Int = 0) {
        if (attempt > 16) return
        if (attempt == 0) {
            // 等首帧绘制完成后再批量调度，避免与页面进入动画争抢主线程。
            Looper.getMainLooper().queue.addIdleHandler {
                scheduleForTextView(textView, 1)
                false
            }
            return
        }
        textView.post {
            if (textView.width <= 0 && attempt < 16) {
                scheduleForTextView(textView, attempt + 1)
                return@post
            }
            val text = textView.text
            if (text !is android.text.Spanned) return@post
            val spans = text.getSpans(0, text.length, AsyncDrawableSpan::class.java) ?: return@post
            var count = 0
            for (span in spans) {
                val drawable = span.drawable
                if (drawable.destination.startsWith("${DiagramSchemeHandler.SCHEME}://")) {
                    count++
                    loadAsync(textView.context, drawable, textView)
                }
            }
            if (count > 0) {
                Log.d(TAG, "scheduled $count diagram(s) on TextView")
            }
        }
    }

    fun loadAsync(context: Context, drawable: AsyncDrawable, hostView: TextView? = null) {
        val dest = drawable.destination
        if (!dest.startsWith("${DiagramSchemeHandler.SCHEME}://")) return
        val payloadId = payloadIdFromDestination(dest) ?: return

        cancelled.remove(drawable)
        inflight.compute(dest) { _, count -> (count ?: AtomicInteger(0)).also { it.incrementAndGet() } }

        decodeExecutor.execute {
            if (drawable in cancelled) {
                releaseInflight(dest)
                return@execute
            }
            val payload = DiagramPayloadStore.get(payloadId) ?: run {
                Log.w(TAG, "payload missing for $dest")
                releaseInflight(dest)
                return@execute
            }
            val cached = bitmapCache.get(payloadId)
            if (cached != null && !cached.isRecycled) {
                releaseInflight(dest)
                applyBitmap(drawable, hostView, hostView?.context ?: context, cached)
                return@execute
            }
            val diskCached = loadDiskBitmap(context, payloadId)
            if (diskCached != null && !diskCached.isRecycled) {
                bitmapCache.put(payloadId, diskCached)
                releaseInflight(dest)
                applyBitmap(drawable, hostView, hostView?.context ?: context, diskCached)
                return@execute
            }
            val source = payload.source
            val diagramType = payload.type
            val renderContext = (hostView?.context ?: context).findActivityContext()
                ?: MarkdownReaderApp.foregroundActivity()
            if (renderContext == null) {
                Log.w(TAG, "No Activity for diagram render: $dest")
                releaseInflight(dest)
                return@execute
            }
            val width = contentWidthForHost(hostView, context)
            DiagramWebViewRenderer.renderAsync(renderContext, diagramType, source, width) { bitmap ->
                releaseInflight(dest)
                if (drawable in cancelled) {
                    cancelled.remove(drawable)
                    return@renderAsync
                }
                cancelled.remove(drawable)
                if (bitmap != null) {
                    bitmapCache.put(payloadId, bitmap)
                    saveDiskBitmap(context, payloadId, bitmap)
                }
                applyBitmap(drawable, hostView, hostView?.context ?: context, bitmap)
            }
        }
    }

    /** 导入阶段预渲染图表到磁盘；无 Activity 时静默跳过，阅读阶段仍会兜底渲染。 */
    suspend fun preloadFromMarkdown(context: Context, markdown: String): Int {
        if (!markdown.contains("diagram://")) return 0
        val ids = DIAGRAM_URI.findAll(markdown)
            .map { it.groupValues[2] }
            .distinct()
            .toList()
        var warmed = 0
        val width = context.resources.displayMetrics.widthPixels.coerceAtLeast(320)
        for (id in ids) {
            if (loadDiskBitmap(context, id) != null) {
                warmed++
                continue
            }
            val payload = DiagramPayloadStore.get(id) ?: continue
            if (renderToDisk(context, id, payload, width)) {
                warmed++
            }
        }
        return warmed
    }

    fun cachedBitmapForDestination(destination: String?): android.graphics.Bitmap? {
        val payloadId = payloadIdFromDestination(destination) ?: return null
        return bitmapCache.get(payloadId)?.takeIf { !it.isRecycled }
    }

    private suspend fun renderToDisk(
        context: Context,
        payloadId: String,
        payload: DiagramPayloadStore.Payload,
        width: Int,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val renderContext = context.findActivityContext()
            ?: MarkdownReaderApp.foregroundActivity()
        if (renderContext == null) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        DiagramWebViewRenderer.renderAsync(renderContext, payload.type, payload.source, width) { bitmap ->
            if (!continuation.isActive) return@renderAsync
            if (bitmap == null) {
                continuation.resume(false)
                return@renderAsync
            }
            bitmapCache.put(payloadId, bitmap)
            saveDiskBitmap(context, payloadId, bitmap)
            continuation.resume(true)
        }
    }

    private fun loadDiskBitmap(context: Context, payloadId: String): Bitmap? =
        runCatching {
            val file = diskFile(context, payloadId)
            if (!file.isFile || file.length() <= 0L) return@runCatching null
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()

    private fun saveDiskBitmap(context: Context, payloadId: String, bitmap: Bitmap) {
        runCatching {
            val file = diskFile(context, payloadId)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.parentFile?.mkdirs()
            tmp.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun payloadIdFromDestination(destination: String?): String? {
        val dest = destination ?: return null
        if (!dest.startsWith("${DiagramSchemeHandler.SCHEME}://")) return null
        val uri = Uri.parse(dest)
        if (uri.host.isNullOrEmpty()) return null
        return uri.path?.removePrefix("/")?.takeIf { it.isNotEmpty() }
    }

    private fun diskFile(context: Context, payloadId: String): File {
        val safeId = payloadId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .take(80)
            .ifBlank { "diagram" }
        return File(diskRoot(context), "$safeId.png")
    }

    private fun diskRoot(context: Context): File =
        File(context.applicationContext.filesDir, DISK_CACHE_DIR).apply { mkdirs() }

    private fun directorySize(dir: File): Long {
        if (!dir.isDirectory) return dir.length().coerceAtLeast(0L)
        return dir.listFiles()?.sumOf { entry ->
            if (entry.isDirectory) directorySize(entry) else entry.length()
        } ?: 0L
    }

    private fun contentWidthForHost(hostView: TextView?, context: Context): Int {
        val pad = hostView?.let { it.paddingLeft + it.paddingRight } ?: 0
        val viewWidth = hostView?.width?.takeIf { it > pad }?.minus(pad)
        return viewWidth ?: context.resources.displayMetrics.widthPixels.coerceAtLeast(320)
    }

    private fun applyBitmap(
        drawable: AsyncDrawable,
        hostView: TextView?,
        context: Context,
        bitmap: android.graphics.Bitmap?,
    ) {
        mainHandler.post {
            if (drawable in cancelled) return@post
            if (bitmap == null) {
                Log.w(TAG, "bitmap null for ${drawable.destination}")
                return@post
            }
            val cropped = DiagramBitmapUtils.cropTrailingWhiteStrip(bitmap)
            val host = hostView ?: run {
                val width = context.resources.displayMetrics.widthPixels.coerceAtLeast(320)
                applyScaledDiagramResult(drawable, context, cropped, width, 15f)
                return@post
            }
            if (host is SafeReaderTextView) {
                host.beginAsyncScrollSuppression()
            }
            val lineWidth = contentWidthForHost(host, context)
            applyScaledDiagramResult(drawable, context, cropped, lineWidth, host.textSize.coerceAtLeast(1f))
            val scrollAnchor = captureTextViewScrollAnchor(host, windowStart = 0)
            host.invalidate()
            host.requestLayout()
            host.post {
                try {
                    if (host.layout == null) return@post
                    if (host.getTag(R.id.reader_pending_scroll_char_offset) != null) {
                        reapplyPendingScrollCharOffsetIfAny(host)
                        schedulePendingScrollReapply(host)
                    } else {
                        scrollTextViewPreservingScrollY(host, scrollAnchor.scrollY)
                    }
                } finally {
                    if (host is SafeReaderTextView) {
                        host.endAsyncScrollSuppression()
                    }
                }
            }
            Log.d(TAG, "applied bitmap ${cropped.width}x${cropped.height} to ${drawable.destination}")
        }
    }

    /** 先设定 canvas 宽度再 setResult，避免 AsyncDrawable 沿用占位符时期的全像素 bounds 撑高行距。 */
    private fun applyScaledDiagramResult(
        drawable: AsyncDrawable,
        context: Context,
        bitmap: Bitmap,
        lineWidthPx: Int,
        textSizePx: Float,
    ) {
        val (width, height) = DiagramBitmapUtils.scaledDrawableBounds(bitmap, lineWidthPx)
        val bmpDrawable = BitmapDrawable(context.resources, bitmap).apply {
            setBounds(0, 0, width, height)
        }
        drawable.initWithKnownDimensions(width, textSizePx)
        drawable.setResult(bmpDrawable)
        drawable.invalidateSelf()
    }

    fun cancel(drawable: AsyncDrawable) {
        cancelled.add(drawable)
    }

    private fun releaseInflight(dest: String) {
        inflight.compute(dest) { _, count ->
            if (count == null || count.decrementAndGet() <= 0) null else count
        }
    }

    private fun Context.findActivityContext(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
