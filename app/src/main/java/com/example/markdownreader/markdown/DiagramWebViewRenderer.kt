package com.example.markdownreader.markdown

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.markdownreader.MarkdownReaderApp
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal object DiagramWebViewRenderer {

    private const val TAG = "DiagramWebViewRenderer"
    private const val CAPTURE_DELAY_MS = 220L
    private const val BRIDGE_NAME = "AndroidDiagram"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val svgExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "diagram-svg-render").apply { isDaemon = true }
    }
    private val rendering = AtomicBoolean(false)
    private val pendingQueue = ArrayDeque<RenderJob>()
    private var pooledWebView: WebView? = null
    private var pooledActivity: Activity? = null
    private var slowDrawEnabled = false

    private data class RenderJob(
        val activity: Activity,
        val type: String,
        val sourceB64: String,
        val contentWidthPx: Int,
        val onResult: (Bitmap?) -> Unit,
    )

    fun renderAsync(
        context: Context,
        type: String,
        source: String,
        contentWidthPx: Int,
        onResult: (Bitmap?) -> Unit,
    ) {
        val sourceB64 = android.util.Base64.encodeToString(
            source.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        mainHandler.post {
            val activity = context.findActivity()
                ?: MarkdownReaderApp.foregroundActivity()
            if (activity == null) {
                Log.w(TAG, "No Activity available for diagram render")
                onResult(null)
                return@post
            }
            val job = RenderJob(activity, type, sourceB64, contentWidthPx, onResult)
            if (!rendering.compareAndSet(false, true)) {
                pendingQueue.addLast(job)
                return@post
            }
            renderOnMainThread(job)
        }
    }

    fun releaseForActivity(activity: Activity) {
        mainHandler.post {
            if (pooledActivity === activity) {
                destroyPooledWebView()
            }
            DiagramWebViewHost.releaseForActivity(activity)
        }
    }

    private fun finishJob(job: RenderJob, bitmap: Bitmap?) {
        job.onResult(bitmap)
        val next = pendingQueue.removeFirstOrNull()
        if (next != null) {
            renderOnMainThread(next)
        } else {
            rendering.set(false)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun renderOnMainThread(job: RenderJob) {
        val type = job.type
        val sourceB64 = job.sourceB64
        val contentWidthPx = job.contentWidthPx

        val assetPage = when (type) {
            "mermaid" -> "file:///android_asset/diagram/mermaid.html"
            "echarts" -> "file:///android_asset/diagram/echarts.html"
            else -> {
                finishJob(job, null)
                return
            }
        }

        val width = contentWidthPx.coerceAtLeast(320)
        enableSlowWholeDocumentDrawOnce()

        val webView = obtainWebView(job.activity)
        DiagramWebViewHost.attach(webView, width)

        var finished = false
        fun complete(bitmap: Bitmap?) {
            if (finished) return
            finished = true
            if (bitmap == null) {
                Log.w(TAG, "diagram bitmap null type=$type")
            } else {
                Log.d(TAG, "diagram bitmap ready type=$type ${bitmap.width}x${bitmap.height}")
            }
            recycleWebView(webView)
            finishJob(job, bitmap)
        }

        val timeoutRunnable = Runnable {
            Log.w(TAG, "diagram render timeout type=$type")
            complete(null)
        }
        mainHandler.postDelayed(timeoutRunnable, 90_000)

        val bridge = object {
            @JavascriptInterface
            fun getSourceB64(): String = sourceB64

            @JavascriptInterface
            fun getContentWidth(): Int = width

            @JavascriptInterface
            fun onRendered(w: Int, h: Int) {
                mainHandler.post {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    val capW = when (type) {
                        "mermaid" -> width
                        else -> w.coerceIn(64, width)
                    }
                    val capH = h.coerceIn(80, 8192)
                    DiagramWebViewHost.resize(webView, capW, capH)
                    webView.postDelayed({
                        complete(captureWebViewBitmap(webView, capW, capH))
                    }, CAPTURE_DELAY_MS)
                }
            }

            @JavascriptInterface
            fun onRenderedSvg(w: Int, h: Int, svgB64: String) {
                mainHandler.post {
                    mainHandler.removeCallbacks(timeoutRunnable)
                }
                svgExecutor.execute {
                    val capW = w.coerceAtLeast(64)
                    val capH = h.coerceIn(80, 8192)
                    val bitmap = decodeUtf8B64(svgB64)?.let { svg ->
                        renderSvgBitmap(svg, capW, capH)
                    }
                    mainHandler.post {
                        complete(bitmap)
                    }
                }
            }

            @JavascriptInterface
            fun onError(message: String) {
                mainHandler.post {
                    Log.w(TAG, "diagram render failed: $message")
                    mainHandler.removeCallbacks(timeoutRunnable)
                    complete(null)
                }
            }
        }

        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.addJavascriptInterface(bridge, BRIDGE_NAME)

        var renderAttempts = 0
        fun invokeRenderDiagram() {
            renderAttempts++
            webView.evaluateJavascript(
                """
                (function() {
                  if (typeof renderDiagram !== 'function') return 'no-fn';
                  if (typeof mermaid === 'undefined' && typeof echarts === 'undefined') return 'no-lib';
                  renderDiagram();
                  return 'ok';
                })()
                """.trimIndent(),
            ) { result ->
                if (result == "\"no-fn\"" || result == "\"no-lib\"") {
                    if (renderAttempts < 40) {
                        mainHandler.postDelayed({ invokeRenderDiagram() }, 100)
                    } else {
                        complete(null)
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                mainHandler.postDelayed({ invokeRenderDiagram() }, 80)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    Log.w(
                        TAG,
                        "WebView error ${error.errorCode}: ${error.description} (${request.url})",
                    )
                    complete(null)
                }
            }
        }

        webView.loadUrl(assetPage)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun obtainWebView(activity: Activity): WebView {
        val existing = pooledWebView
        if (existing != null && pooledActivity === activity) {
            existing.stopLoading()
            existing.removeJavascriptInterface(BRIDGE_NAME)
            return existing
        }
        destroyPooledWebView()
        val webView = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            @Suppress("DEPRECATION")
            run {
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
            }
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.defaultTextEncodingName = "utf-8"
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setBackgroundColor(Color.WHITE)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        pooledWebView = webView
        pooledActivity = activity
        return webView
    }

    private fun recycleWebView(webView: WebView) {
        webView.stopLoading()
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.webViewClient = WebViewClient()
        DiagramWebViewHost.detach(webView)
    }

    private fun destroyPooledWebView() {
        pooledWebView?.let { webView ->
            DiagramWebViewHost.detach(webView)
            webView.stopLoading()
            webView.destroy()
        }
        pooledWebView = null
        pooledActivity = null
    }

    private fun enableSlowWholeDocumentDrawOnce() {
        if (slowDrawEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw()
        }
        slowDrawEnabled = true
    }

    private fun decodeUtf8B64(value: String): String? = runCatching {
        val bytes = android.util.Base64.decode(value, android.util.Base64.DEFAULT)
        String(bytes, Charsets.UTF_8)
    }.getOrNull()

    private fun renderSvgBitmap(svgMarkup: String, width: Int, height: Int): Bitmap? {
        return try {
            val svg = SVG.getFromString(svgMarkup).apply {
                documentWidth = width.toFloat()
                documentHeight = height.toFloat()
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            svg.renderToCanvas(canvas)
            cropTrailingWhiteStrip(bitmap)
        } catch (e: SVGParseException) {
            Log.w(TAG, "SVG parse failed", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "SVG render failed", e)
            null
        }
    }

    /** 直接从已渲染内容的 WebView 截图（保留 Mermaid CSS / foreignObject 文字）。 */
    private fun captureWebViewBitmap(webView: WebView, width: Int, height: Int): Bitmap? {
        return try {
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            webView.setPadding(0, 0, 0, 0)
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
            )
            webView.layout(0, 0, w, h)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            webView.draw(canvas)
            cropTrailingWhiteStrip(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "WebView capture failed", e)
            null
        }
    }

    /** 裁掉底部多余白边（WebView 偶发比 SVG 高出数像素）。 */
    private fun cropTrailingWhiteStrip(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 4) return source
        var lastContentRow = h - 1
        scan@ for (y in h - 1 downTo 0) {
            var nonWhite = false
            for (x in 0 until w step 8) {
                if (source.getPixel(x, y) != Color.WHITE) {
                    nonWhite = true
                    break
                }
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

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
