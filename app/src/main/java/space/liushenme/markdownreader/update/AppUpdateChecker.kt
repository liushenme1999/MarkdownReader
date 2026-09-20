package space.liushenme.markdownreader.update

import android.util.Log
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import space.liushenme.markdownreader.BuildConfig

object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"

    /** 主源：GitHub raw。国内网络常被拦截，失败后会改走镜像。 */
    const val LATEST_JSON_URL =
        "https://raw.githubusercontent.com/liushenme1999/MarkdownReader/main/latest.json"

    internal val CANDIDATE_URLS = listOf(
        LATEST_JSON_URL,
        "https://cdn.jsdelivr.net/gh/liushenme1999/MarkdownReader@main/latest.json",
        "https://fastly.jsdelivr.net/gh/liushenme1999/MarkdownReader@main/latest.json",
        "https://github.com/liushenme1999/MarkdownReader/raw/main/latest.json",
    )

    private const val MAX_BYTES = 8 * 1024
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 10_000

    fun fetchLatest(): AppUpdateInfo {
        var lastError: Exception? = null
        for (url in CANDIDATE_URLS) {
            try {
                return fetchFrom(url)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "fetch latest.json failed: $url (${e.javaClass.simpleName}: ${e.message})")
            }
        }
        throw lastError ?: IllegalStateException("update check failed")
    }

    private fun fetchFrom(url: String): AppUpdateInfo {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty(
                "User-Agent",
                "MarkdownReader/${BuildConfig.VERSION_NAME} (Android)",
            )
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("HTTP $code")
            }
            val body = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(256)
                val buf = ByteArray(1024)
                var total = 0
                while (total < MAX_BYTES) {
                    val n = input.read(buf, 0, (MAX_BYTES - total).coerceAtMost(buf.size))
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                out.toString(Charsets.UTF_8.name())
            }
            parseLatestJson(body) ?: error("invalid latest.json")
        } finally {
            conn.disconnect()
        }
    }

    fun parseLatestJson(json: String): AppUpdateInfo? {
        return runCatching {
            val obj = JsonParser.parseString(json).asJsonObject
            val versionName = obj.get("versionName")?.asString?.trim().orEmpty()
            if (!obj.has("versionCode") || obj.get("versionCode").isJsonNull) return null
            val versionCode = obj.get("versionCode").asInt
            val pageUrl = obj.get("pageUrl")?.asString?.trim().orEmpty()
            if (versionName.isEmpty() || versionCode < 0 || !isHttpUrl(pageUrl)) {
                return null
            }
            AppUpdateInfo(
                versionName = versionName,
                versionCode = versionCode,
                pageUrl = pageUrl,
            )
        }.getOrNull()
    }

    private fun isHttpUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.startsWith("https://") || lower.startsWith("http://")) &&
            url.length in 10..2048
    }
}
