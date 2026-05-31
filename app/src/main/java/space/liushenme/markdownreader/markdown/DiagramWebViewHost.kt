package space.liushenme.markdownreader.markdown

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * 将离屏 WebView 挂到 Activity 窗口。
 * 须保持 VISIBLE（可极低透明度），否则 WebView 内 DOM 无法完成 layout/绘制。
 * 使用真实渲染尺寸但保持极低透明度（不用 translationX 移出屏幕），避免 WebView 视口被 1px 容器扰乱。
 */
internal object DiagramWebViewHost {

    private var hostContainer: FrameLayout? = null
    private var hostActivity: Activity? = null

    fun attach(webView: WebView, contentWidthPx: Int) {
        val activity = webView.context.findActivity() ?: return
        if (hostActivity != null && hostActivity !== activity) {
            releaseHost()
        }
        hostActivity = activity
        val root = activity.window.decorView as ViewGroup
        val width = contentWidthPx.coerceAtLeast(320)
        val host = hostContainer ?: FrameLayout(activity).apply {
            clipChildren = true
            clipToPadding = true
            visibility = View.VISIBLE
            alpha = 0.001f
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }.also { hostContainer = it }
        val hostLp = FrameLayout.LayoutParams(width, 1, Gravity.BOTTOM or Gravity.START)
        if (host.parent == null) {
            root.addView(host, hostLp)
        } else {
            host.layoutParams = hostLp
        }
        attachWebView(host, webView, width)
    }

    fun resize(webView: WebView, widthPx: Int, heightPx: Int) {
        val host = webView.parent as? FrameLayout ?: return
        host.layoutParams = FrameLayout.LayoutParams(
            widthPx.coerceAtLeast(1),
            heightPx.coerceAtLeast(1),
            Gravity.BOTTOM or Gravity.START,
        )
        webView.layoutParams = FrameLayout.LayoutParams(
            widthPx.coerceAtLeast(1),
            heightPx.coerceAtLeast(1),
        )
        host.requestLayout()
    }

    fun detach(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
    }

    fun releaseForActivity(activity: Activity) {
        if (hostActivity === activity) {
            releaseHost()
        }
    }

    private fun releaseHost() {
        hostContainer?.let { host ->
            (host.parent as? ViewGroup)?.removeView(host)
            host.removeAllViews()
        }
        hostContainer = null
        hostActivity = null
    }

    private fun attachWebView(host: FrameLayout, webView: WebView, width: Int) {
        if (webView.parent !== host) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            host.addView(
                webView,
                FrameLayout.LayoutParams(
                    width,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        } else {
            webView.layoutParams = FrameLayout.LayoutParams(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
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
