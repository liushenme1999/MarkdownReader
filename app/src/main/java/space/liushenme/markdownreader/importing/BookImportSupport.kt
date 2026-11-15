package space.liushenme.markdownreader.importing

import android.content.Context
import android.net.Uri
import java.net.URI

/** 书架导入相关的可测试工具（格式识别、文件名、作者元数据等）。 */
object BookImportSupport {

    /**
     * 系统文件选择器 MIME 过滤：Markdown / TXT / PDF / HTML + 常见源码文件。
     * 不含 application/octet-stream，避免其它二进制文件混入列表；目录仍可正常浏览进入。
     * 代码文件扩展名与语言映射见 [CodeFileLanguage]（单一清单来源）。
     */
    val importMimeTypes: Array<String> = arrayOf(
        "text/markdown",
        "text/x-markdown",
        "text/plain",
        "application/pdf",
        "text/html",
        "text/x-html",
        *CodeFileLanguage.importMimeTypes,
        // 兜底：Android 无法识别的扩展名（.kt/.go/.rs/.sh/.yaml…）常被系统报成
        // application/octet-stream。不放行这一项时，系统文件选择器会把这些文件置灰/隐藏，
        // 表现为「明明有文件却选不中 / 导入不了」。真正是否可导入仍由扩展名白名单判定。
        "application/octet-stream",
    )

    private val SUPPORTED_EXTENSIONS = setOf(
        "md", "markdown", "mdown", "mkd",
        "txt", "text", "log",
        "pdf",
        "html", "htm",
    ) + CodeFileLanguage.supportedExtensions

    private val REMOVED_EXTENSIONS = setOf(
        "epub", "docx", "doc", "mobi", "prc", "azw3", "azw"
    )

    private val STRIP_SUFFIXES = listOf(
        ".markdown", ".mdown", ".mkd", ".md",
        ".txt", ".text", ".log",
        ".pdf",
        ".html", ".htm",
    )

    /** 已移除的格式（EPUB / Word / Kindle），用于导入前拦截。 */
    fun isRemovedFormat(fileName: String?, mime: String? = null): Boolean {
        val ext = fileName?.substringAfterLast('.', "")?.trim()?.lowercase().orEmpty()
        if (ext in REMOVED_EXTENSIONS) return true
        val m = mime?.lowercase().orEmpty()
        return m.contains("epub") ||
            m.contains("mobi") ||
            m.contains("azw") ||
            m.contains("wordprocessingml") ||
            (m.contains("msword") && !m.contains("openxml"))
    }

    /**
     * 扩展名与 MIME 都无法判定为受支持格式（例如未知扩展名 + octet-stream）。
     * 文件选择器放开 octet-stream 后，用它拦截 APK / 压缩包等误选文件。
     */
    fun isUnsupportedPick(fileName: String?, mime: String?): Boolean {
        val ext = fileName?.substringAfterLast('.', "")?.trim()?.lowercase().orEmpty()
        if (ext in SUPPORTED_EXTENSIONS) return false
        val normalizedMime = mime?.lowercase()?.trim().orEmpty()
        if (ext.isEmpty()) return false
        return normalizedMime.isEmpty() || normalizedMime == "application/octet-stream"
    }

    fun detectFormat(fileName: String?, mime: String?): ImportedBookFormat {
        val ext = fileName?.substringAfterLast('.', "")?.trim()?.lowercase().orEmpty()
        return if (ext in SUPPORTED_EXTENSIONS) {
            ImportedBookFormat.fromFileName(fileName)
        } else {
            ImportedBookFormat.fromMimeType(mime)
        }
    }

    fun stripKnownExtension(fileName: String): String {
        var n = fileName.trim()
        for (s in STRIP_SUFFIXES) {
            if (n.endsWith(s, ignoreCase = true)) {
                n = n.dropLast(s.length)
                break
            }
        }
        return n.ifBlank { fileName }
    }

    fun extractAuthorFromContent(content: String): String? {
        val authorRegex = Regex("^[Aa]uthor:\\s*(.+)$", RegexOption.MULTILINE)
        return authorRegex.find(content)?.groupValues?.get(1)?.trim()
    }

    fun normalizeImportUrl(urlRaw: String): String? {
        val trimmed = urlRaw.trim()
        if (trimmed.isEmpty()) return null
        val candidate = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            hasNonHttpSchemePrefix(trimmed) -> return null
            else -> "https://$trimmed"
        }
        return if (isAllowedDownloadUrl(candidate)) candidate else null
    }

    /** 仅允许带有效 host 的 http/https，拒绝 file、javascript 等 scheme。 */
    fun isAllowedDownloadUrl(url: String): Boolean {
        return runCatching {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }

    private fun hasNonHttpSchemePrefix(url: String): Boolean {
        val colon = url.indexOf(':')
        if (colon <= 0) return false
        val scheme = url.substring(0, colon).lowercase()
        return scheme != "http" && scheme != "https"
    }

    fun displayNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut >= 0) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }
}
