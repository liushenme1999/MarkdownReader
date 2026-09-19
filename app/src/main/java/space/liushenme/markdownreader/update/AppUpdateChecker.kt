package space.liushenme.markdownreader.update

import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import space.liushenme.markdownreader.BuildConfig

object AppUpdateChecker {

    const val LATEST_JSON_URL =
        "https://raw.githubusercontent.com/liushenme1999/MarkdownReader/main/latest.json"

    private const val MAX_BYTES = 8 * 1024
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    fun fetchLatest(): AppUpdateInfo {
        val conn = (URL(LATEST_JSON_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
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
