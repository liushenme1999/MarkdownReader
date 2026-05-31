package space.liushenme.markdownreader.markdown

import android.content.Context
import android.net.Uri
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Arrays

/**
 * 带磁盘缓存的 http(s) 图片加载：命中本地则直接读盘，否则下载后落盘再解码。
 */
internal class CachedNetworkSchemeHandler(
    private val context: Context,
) : SchemeHandler() {

    override fun handle(raw: String, uri: Uri): ImageItem {
        NetworkImageCache.getCachedFile(context, raw)?.let { cached ->
            val contentType = NetworkImageCache.guessContentType(null, raw)
            return ImageItem.withDecodingNeeded(contentType, FileInputStream(cached))
        }

        val lock = NetworkImageCache.lockFor(raw)
        synchronized(lock) {
            NetworkImageCache.getCachedFile(context, raw)?.let { cached ->
                val contentType = NetworkImageCache.guessContentType(null, raw)
                return ImageItem.withDecodingNeeded(contentType, FileInputStream(cached))
            }

            val (bytes, contentType) = download(raw)
            val file = NetworkImageCache.save(context, raw, bytes, contentType)
            return ImageItem.withDecodingNeeded(contentType, FileInputStream(file))
        }
    }

    override fun supportedSchemes(): Collection<String> =
        Arrays.asList(SCHEME_HTTP, SCHEME_HTTPS)

    private fun download(raw: String): Pair<ByteArray, String?> {
        val connection = (URL(raw).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            connect()
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Bad response code: $responseCode, url: $raw")
            }
            val contentType = parseContentType(connection.getHeaderField("Content-Type"))
            val bytes = connection.inputStream.use { stream ->
                stream.readBytesCompat()
            }
            if (bytes.isEmpty()) {
                throw IOException("Empty response body: $raw")
            }
            return bytes to NetworkImageCache.guessContentType(contentType, raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readBytesCompat(): ByteArray {
        val buffer = BufferedInputStream(this)
        return buffer.readBytes()
    }

    private fun parseContentType(contentType: String?): String? {
        if (contentType.isNullOrEmpty()) return null
        val index = contentType.indexOf(';')
        return if (index > 0) contentType.substring(0, index) else contentType
    }

    companion object {
        private const val SCHEME_HTTP = "http"
        private const val SCHEME_HTTPS = "https"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        fun create(context: Context): CachedNetworkSchemeHandler =
            CachedNetworkSchemeHandler(context.applicationContext)
    }
}
