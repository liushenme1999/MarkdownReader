package space.liushenme.markdownreader.markdown

import android.net.Uri

/**
 * Markdown 链接点击回调：由 [space.liushenme.markdownreader.MainActivity] 注册，用于应用内 WebView 打开网页。
 */
object MarkdownLinkDispatcher {

    var openUrl: ((String) -> Unit)? = null

    /**
     * 在真正打开网页前请求用户确认。
     *
     * 回调收到的第二个参数只有在用户确认后执行，执行时才会触发
     * [onBeforeOpenWebUrl] 和 [openUrl]。未注册时保持原有直接打开行为。
     */
    var onRequestOpenWebUrl: ((String, () -> Unit) -> Unit)? = null

    /** 打开应用内 WebView 前同步落盘阅读位置（由 [ReaderScreen] 注册）。 */
    var onBeforeOpenWebUrl: (() -> Unit)? = null

    fun openWebUrl(raw: String) {
        val normalized = raw.trim()
        if (!isWebUrl(normalized)) return
        val proceed = {
            onBeforeOpenWebUrl?.invoke()
            openUrl?.invoke(normalized)
            Unit
        }
        onRequestOpenWebUrl?.invoke(normalized, proceed) ?: proceed()
    }

    fun isWebUrl(link: String): Boolean {
        val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }
}
