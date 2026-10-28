package space.liushenme.markdownreader.ui.screens.reader

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
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
                webView.webChromeClient = WebChromeClient()
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
