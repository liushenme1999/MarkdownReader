package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 网络图片磁盘缓存（`filesDir/network_image_cache/`），按 URL 哈希命名。
 * 二次进入阅读页时命中本地文件，避免重复下载与 layout 抖动。
 */
internal object NetworkImageCache {

    private const val CACHE_DIR = "network_image_cache"
    private val urlLocks = ConcurrentHashMap<String, Any>()

    private val MD_IMAGE = Regex("""!\[([^\]]*)\]\((https?://[^)\s]+)(?:\s+"[^"]*")?\)""")
    private val HTML_IMG_TAG = Regex("""<img\s+([^>]*?)\s*/?>""", RegexOption.IGNORE_CASE)
    private val HTML_IMG_SRC = Regex(
        """\bsrc\s*=\s*("([^"]*)"|'([^']*)'|([^"'\s>]+))""",
        RegexOption.IGNORE_CASE,
    )

    data class PreloadResult(
        val requested: Int,
        val cached: Int,
        val failed: Int,
    )

    fun cacheRoot(context: Context): File =
        File(context.applicationContext.filesDir, CACHE_DIR).apply { mkdirs() }

    fun getCachedFile(context: Context, url: String): File? {
        val file = cacheFileFor(context, url)
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    /** 删除全部网络图片磁盘缓存，返回释放的字节数。 */
    fun clearAll(context: Context): Long {
        urlLocks.clear()
        val root = File(context.applicationContext.filesDir, CACHE_DIR)
        if (!root.exists()) return 0L
        val bytes = directorySize(root)
        root.deleteRecursively()
        return bytes
    }

    fun save(context: Context, url: String, bytes: ByteArray, contentType: String?): File {
        val file = cacheFileFor(context, url, contentType, url)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.parentFile?.mkdirs()
        tmp.writeBytes(bytes)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        return file
    }

    /**
     * 导入阶段主动下载正文里的网络图片。失败不影响导入，阅读阶段仍可按原 URL 兜底加载。
     */
    fun preloadFromMarkdown(context: Context, markdown: String): PreloadResult {
        val urls = extractNetworkImageUrls(markdown)
        var cached = 0
        var failed = 0
        for (url in urls) {
            if (getCachedFile(context, url) != null) {
                cached++
                continue
            }
            val ok = runCatching {
                val lock = lockFor(url)
                synchronized(lock) {
                    if (getCachedFile(context, url) == null) {
                        val (bytes, contentType) = download(url)
                        save(context, url, bytes, contentType)
                    }
                }
            }.isSuccess
            if (ok) cached++ else failed++
        }
        return PreloadResult(requested = urls.size, cached = cached, failed = failed)
    }

    internal fun extractNetworkImageUrls(markdown: String): List<String> {
        if (!markdown.contains("http://", ignoreCase = true) &&
            !markdown.contains("https://", ignoreCase = true)
        ) {
            return emptyList()
        }
        val out = LinkedHashSet<String>()
        MD_IMAGE.findAll(markdown).forEach { match ->
            match.groupValues.getOrNull(2)?.trim()?.takeIf(::isHttpUrl)?.let(out::add)
        }
        HTML_IMG_TAG.findAll(markdown).forEach { match ->
            val attrs = match.groupValues[1]
            HTML_IMG_SRC.find(attrs)
                ?.groupValues
                ?.drop(2)
                ?.firstOrNull { it.isNotEmpty() }
                ?.trim()
                ?.takeIf(::isHttpUrl)
                ?.let(out::add)
        }
        return out.toList()
    }

    /**
     * 若本地已有缓存，将 Markdown / HTML 中的 `http(s)` 图片 URL 改写为 `file://`，
     * 走 [io.noties.markwon.image.file.FileSchemeHandler] 快速加载。
     */
    fun rewriteCachedUrls(context: Context, markdown: String): String =
        rewriteCachedUrls(markdown) { url -> getCachedFile(context, url) }

    internal fun rewriteCachedUrls(
        markdown: String,
        cacheLookup: (String) -> File?,
    ): String {
        if (!markdown.contains("://")) return markdown
        var out = MD_IMAGE.replace(markdown) { match ->
            val alt = match.groupValues[1]
            val raw = match.groupValues[2].trim()
            val local = localFileUri(raw, cacheLookup) ?: return@replace match.value
            "![$alt]($local)"
        }
        if (!out.contains("<img", ignoreCase = true)) return out
        out = HTML_IMG_TAG.replace(out) { match ->
            val attrs = match.groupValues[1]
            val newAttrs = HTML_IMG_SRC.replace(attrs) { srcMatch ->
                val raw = srcMatch.groupValues.drop(2).firstOrNull { it.isNotEmpty() }?.trim()
                    ?: return@replace srcMatch.value
                val local = localFileUri(raw, cacheLookup) ?: return@replace srcMatch.value
                val quote = if ("'" in srcMatch.value) "'" else "\""
                "src=$quote$local$quote"
            }
            "<img $newAttrs/>"
        }
        return out
    }

    fun decodeBoundsFromFile(file: File): Pair<Int, Int>? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        if (w > 0 && h > 0) w to h else null
    }.getOrNull()

    fun guessContentType(contentTypeHeader: String?, url: String): String? {
        val header = contentTypeHeader?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
        if (header != null) return header
        return extensionFromUrl(url)?.let { mimeFromExtension(it) }
    }

    fun extensionFromContentType(contentType: String?): String? = when (contentType?.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        "image/svg+xml" -> "svg"
        else -> null
    }

    internal fun cacheFileFor(
        context: Context,
        url: String,
        contentType: String? = null,
        urlForExt: String = url,
    ): File {
        val ext = extensionFromContentType(contentType)
            ?: extensionFromUrl(urlForExt)
            ?: "img"
        return File(cacheRoot(context), "${sha256(url)}.$ext")
    }

    internal fun lockFor(url: String): Any = urlLocks.getOrPut(url) { Any() }

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
            return bytes to guessContentType(contentType, raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readBytesCompat(): ByteArray =
        BufferedInputStream(this).readBytes()

    private fun parseContentType(contentType: String?): String? =
        contentType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun localFileUri(url: String, cacheLookup: (String) -> File?): String? {
        if (!isHttpUrl(url)) {
            return null
        }
        val file = cacheLookup(url) ?: return null
        return "file://${file.absolutePath}"
    }

    private fun isHttpUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun extensionFromUrl(url: String): String? {
        val withoutQuery = url.substringBefore('?').substringBefore('#')
        val ext = withoutQuery.substringAfterLast('.', "").lowercase()
        return ext.takeIf { it in IMAGE_EXTENSIONS }
    }

    private fun mimeFromExtension(ext: String): String? = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        else -> null
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun directorySize(dir: File): Long {
        if (!dir.isDirectory) return dir.length().coerceAtLeast(0L)
        return dir.listFiles()?.sumOf { entry ->
            if (entry.isDirectory) directorySize(entry) else entry.length()
        } ?: 0L
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
}
