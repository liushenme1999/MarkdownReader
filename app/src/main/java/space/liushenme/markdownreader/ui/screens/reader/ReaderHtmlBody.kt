package space.liushenme.markdownreader.ui.screens.reader

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.text.InputType
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.widget.EditText
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max

/**
 * 用系统对话框承接 WebView 的 JS 弹窗。
 *
 * 必须在主线程创建；[configure] 里绑定按钮回调，取消/返回键统一走 [JsResult.cancel]，
 * 避免 JS 端 confirm/prompt 永远挂起或直接返回 false。
 */
private fun showJsDialog(
    webView: WebView,
    onFailure: () -> Unit,
    configure: (AlertDialog.Builder) -> Unit,
) {
    val shown = runCatching {
        val builder = AlertDialog.Builder(webView.context)
            .setTitle(android.R.string.dialog_alert_title)
        configure(builder)
        builder.create().apply {
            setCanceledOnTouchOutside(true)
            setOnDismissListener(DialogInterface.OnDismissListener { })
            show()
        }
    }.isSuccess
    // 对话框没能弹出时也要给 JS 一个确定结果，否则页面会一直等着回调。
    if (!shown) onFailure()
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
internal fun ReaderHtmlBody(
    html: String,
    modifier: Modifier = Modifier,
    onScrollProgress: (Float) -> Unit,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).also { webView ->
                webViewRef = webView
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = true
                    allowFileAccess = false
                    allowContentAccess = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    supportZoom()
                }
                webView.isVerticalScrollBarEnabled = true
                webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val scheme = request.url.scheme?.lowercase()
                        return scheme != "http" && scheme != "https"
                    }
                }
                // 页面内的 alert / confirm / prompt 默认会被 WebView 直接丢弃（onJsAlert 返回 false
                // 等价于取消），表现为「按钮点了没反应」。这里统一用系统对话框承接，
                // 并把结果回填给 JS，页面里的交互逻辑才能继续执行。
                webView.webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult,
                    ): Boolean {
                        showJsDialog(view, onFailure = { result.confirm() }) { builder ->
                            builder.setMessage(message)
                                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                                .setOnCancelListener { result.cancel() }
                        }
                        return true
                    }

                    override fun onJsConfirm(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult,
                    ): Boolean {
                        showJsDialog(view, onFailure = { result.cancel() }) { builder ->
                            builder.setMessage(message)
                                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                                .setOnCancelListener { result.cancel() }
                        }
                        return true
                    }

                    override fun onJsPrompt(
                        view: WebView,
                        url: String,
                        message: String,
                        defaultValue: String?,
                        result: JsPromptResult,
                    ): Boolean {
                        val input = EditText(view.context).apply {
                            setText(defaultValue.orEmpty())
                            inputType = InputType.TYPE_CLASS_TEXT
                            setSingleLine()
                        }
                        showJsDialog(view, onFailure = { result.cancel() }) { builder ->
                            builder.setMessage(message)
                                .setView(input)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    result.confirm(input.text?.toString().orEmpty())
                                }
                                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                                .setOnCancelListener { result.cancel() }
                        }
                        return true
                    }
                }
                webView.setOnScrollChangeListener { view, _, scrollY, _, _ ->
                    val target = view as WebView
                    val contentHeight = target.contentHeight * target.scale
                    val maxScroll = max(1f, contentHeight - target.height)
                    onScrollProgress((scrollY / maxScroll).coerceIn(0f, 1f))
                }
                webView.tag = html
                webView.loadDataWithBaseURL(
                    null,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(
                    null,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        onRelease = { webView ->
            if (webViewRef == webView) webViewRef = null
            webView.destroy()
        },
    )
}
