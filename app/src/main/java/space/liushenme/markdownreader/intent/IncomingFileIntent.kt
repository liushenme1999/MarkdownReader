package space.liushenme.markdownreader.intent

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * 从「用其他应用打开」/ 分享等外部 Intent 解析可读文件 [Uri]。
 */
object IncomingFileIntent {

    fun extractOpenableUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> extractSendStream(intent)
            else -> intent.data
        }?.takeIf { isSupportedScheme(it) }
    }

    private fun extractSendStream(intent: Intent): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { return it }
        } else {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { return it }
        }
        return intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    private fun isSupportedScheme(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme == "content" || scheme == "file"
    }
}
