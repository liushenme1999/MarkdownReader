package com.example.markdownreader.markdown

import android.net.Uri

/**
 * Markdown 链接点击回调：由 [com.example.markdownreader.MainActivity] 注册，用于应用内 WebView 打开网页。
 */
object MarkdownLinkDispatcher {

    var openUrl: ((String) -> Unit)? = null

    fun openWebUrl(raw: String) {
        val normalized = raw.trim()
        if (!isWebUrl(normalized)) return
        openUrl?.invoke(normalized)
    }

    fun isWebUrl(link: String): Boolean {
        val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }
}
