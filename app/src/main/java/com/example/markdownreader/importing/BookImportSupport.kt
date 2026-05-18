package com.example.markdownreader.importing

import android.content.Context
import android.net.Uri
import java.net.URI

/** 书架导入相关的可测试工具（格式识别、文件名、作者元数据等）。 */
object BookImportSupport {

    val importMimeTypes: Array<String> = arrayOf(
        "text/markdown",
        "text/x-markdown",
        "text/plain",
        "application/pdf",
        "application/octet-stream"
    )

    private val SUPPORTED_EXTENSIONS = setOf(
        "md", "markdown", "mdown", "mkd",
        "txt", "text", "log",
        "pdf"
    )

    private val REMOVED_EXTENSIONS = setOf(
        "epub", "docx", "doc", "mobi", "prc", "azw3", "azw"
    )

    private val STRIP_SUFFIXES = listOf(
        ".markdown", ".mdown", ".mkd", ".md",
        ".txt", ".text", ".log",
        ".pdf"
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
