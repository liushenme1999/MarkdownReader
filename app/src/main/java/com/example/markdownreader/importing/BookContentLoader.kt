package com.example.markdownreader.importing

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

private const val MAX_TEXT_SCAN_BYTES = 8 * 1024 * 1024
private const val MAX_URL_BYTES = 15 * 1024 * 1024

/**
 * 从 content://、file:// 或已下载字节加载正文；文本类走编码探测，EPUB/DOCX/MOBI/AZW3 做结构化提取。
 */
object BookContentLoader {

    fun loadFromUri(context: Context, uri: Uri, format: ImportedBookFormat): String =
        loadExtractedFromUri(context, uri, format).body

    fun loadExtractedFromUri(context: Context, uri: Uri, format: ImportedBookFormat): ExtractedBookText {
        return when (format) {
            ImportedBookFormat.MARKDOWN, ImportedBookFormat.TXT ->
                ExtractedBookText.plainBody(readPlainTextFromUri(context, uri))

            ImportedBookFormat.EPUB -> {
                val file = copyUriToCacheFile(context, uri, ".epub") ?: return ExtractedBookText.plainBody("")
                try {
                    EpubBookExtractor.extract(file)
                        ?: ExtractedBookText.plainBody(placeholder(format))
                } finally {
                    file.delete()
                }
            }

            ImportedBookFormat.DOCX -> {
                val file = copyUriToCacheFile(context, uri, ".docx") ?: return ExtractedBookText.plainBody("")
                try {
                    DocxBookExtractor.extract(file)
                        ?: ExtractedBookText.plainBody(placeholder(format))
                } finally {
                    file.delete()
                }
            }

            ImportedBookFormat.MOBI -> loadMobiExtracted(context, uri, ".mobi", format)
            ImportedBookFormat.AZW3 -> loadMobiExtracted(context, uri, ".azw3", format)

            ImportedBookFormat.PDF -> {
                val file = copyUriToCacheFile(context, uri, ".pdf") ?: return ExtractedBookText.plainBody("")
                try {
                    PdfBookExtractor.extract(file)
                        ?: ExtractedBookText.plainBody(placeholder(format))
                } finally {
                    file.delete()
                }
            }

            ImportedBookFormat.DOC ->
                ExtractedBookText.plainBody(placeholder(format))
        }
    }

    fun loadFromUrlBytes(
        bytes: ByteArray,
        format: ImportedBookFormat,
        httpCharsetName: String?
    ): String = loadExtractedFromUrlBytes(bytes, format, httpCharsetName).body

    fun loadExtractedFromUrlBytes(
        bytes: ByteArray,
        format: ImportedBookFormat,
        httpCharsetName: String?
    ): ExtractedBookText {
        return when (format) {
            ImportedBookFormat.MARKDOWN, ImportedBookFormat.TXT ->
                ExtractedBookText.plainBody(decodeTextBytes(bytes, httpCharsetName))

            ImportedBookFormat.EPUB -> {
                val tmp = File.createTempFile("epub_", ".epub")
                try {
                    tmp.writeBytes(bytes)
                    EpubBookExtractor.extract(tmp)
                        ?: ExtractedBookText.plainBody(placeholder(format))
                } finally {
                    tmp.delete()
                }
            }

            ImportedBookFormat.DOCX -> {
                val tmp = File.createTempFile("docx_", ".docx")
                try {
                    tmp.writeBytes(bytes)
                    DocxBookExtractor.extract(tmp)
                        ?: ExtractedBookText.plainBody(placeholder(format))
                } finally {
                    tmp.delete()
                }
            }

            ImportedBookFormat.MOBI, ImportedBookFormat.AZW3 ->
                MobiAzw3Extractor.extractFromBytes(bytes)
                    ?: ExtractedBookText.plainBody(placeholder(format))

            ImportedBookFormat.PDF -> {
                val tmp = File.createTempFile("pdf_", ".pdf")
                try {
                    tmp.writeBytes(bytes)
                    PdfBookExtractor.extract(tmp)
                        ?: ExtractedBookText.plainBody(placeholder(format))
                } finally {
                    tmp.delete()
                }
            }

            ImportedBookFormat.DOC ->
                ExtractedBookText.plainBody(placeholder(format))
        }
    }

    private fun loadMobiExtracted(
        context: Context,
        uri: Uri,
        suffix: String,
        format: ImportedBookFormat
    ): ExtractedBookText {
        val file = copyUriToCacheFile(context, uri, suffix) ?: return ExtractedBookText.plainBody("")
        return try {
            MobiAzw3Extractor.extractFromFile(file)
                ?: ExtractedBookText.plainBody(placeholder(format))
        } finally {
            file.delete()
        }
    }

    fun readPlainTextFromUri(context: Context, uri: Uri): String {
        val bytes = readBytesCapped(context, uri, MAX_TEXT_SCAN_BYTES) ?: return ""
        return TextEncodingDetector.decode(bytes)
    }

    fun readBytesCapped(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
        readBytesViaFileDescriptor(context, uri, maxBytes)?.let { return it }
        return readBytesViaInputStream(context, uri, maxBytes)
    }

    private fun readBytesViaFileDescriptor(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    copyStreamToByteArrayWithCap(fis, maxBytes)
                }
            }
        }.getOrNull()
    }

    private fun readBytesViaInputStream(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { raw ->
            BufferedInputStream(raw).use { input ->
                copyStreamToByteArrayWithCap(input, maxBytes)
            }
        }
    }

    private fun copyStreamToByteArrayWithCap(input: InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(
            (32 * 1024).coerceAtMost(maxBytes).coerceAtLeast(256)
        )
        val buf = ByteArray(8192)
        var total = 0
        while (total < maxBytes) {
            val toRead = (maxBytes - total).coerceAtMost(buf.size)
            val n = input.read(buf, 0, toRead)
            if (n <= 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private fun copyUriToCacheFile(context: Context, uri: Uri, suffix: String): File? {
        val out = File(context.cacheDir, "import_${System.currentTimeMillis()}$suffix")
        return try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                out.outputStream().use { outs -> ins.copyTo(outs) }
            }
            if (out.length() > 0L) out else null
        } catch (_: Exception) {
            out.delete()
            null
        }
    }

    private fun decodeTextBytes(bytes: ByteArray, httpCharsetName: String?): String {
        if (httpCharsetName.isNullOrBlank()) {
            return TextEncodingDetector.decode(bytes)
        }
        return try {
            val cs = Charset.forName(httpCharsetName.trim())
            String(bytes, cs)
        } catch (_: Exception) {
            TextEncodingDetector.decode(bytes)
        }
    }

    fun placeholder(format: ImportedBookFormat): String {
        val label = when (format) {
            ImportedBookFormat.PDF -> "PDF"
            ImportedBookFormat.DOC -> "DOC（旧版 Word）"
            ImportedBookFormat.MOBI -> "MOBI"
            ImportedBookFormat.AZW3 -> "AZW3 / Kindle"
            else -> format.name
        }
        return buildString {
            appendLine("# 暂不支持直接阅读此格式")
            appendLine()
            appendLine("当前版本尚未为 **$label** 内置正文提取，书架已保存该书以便后续升级。")
            appendLine()
            appendLine("建议：将内容另存为 **Markdown**、**TXT** 或 **DOCX** 后再导入。")
        }
    }
}

/** 从 URL 下载（协程 IO 中调用）。 */
object UrlBookDownloader {
    data class Result(
        val bytes: ByteArray,
        val suggestedFileName: String?,
        val contentType: String?,
        val charsetFromHeader: String?
    )

    fun download(urlStr: String): Result {
        require(BookImportSupport.isAllowedDownloadUrl(urlStr)) {
            "仅支持 http 或 https 链接"
        }
        val conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty(
            "User-Agent",
            "MarkdownReader/1.0 (Android)"
        )
        conn.connect()
        val type = conn.contentType
        val charset = parseCharsetFromContentType(type)
        val disp = conn.getHeaderField("Content-Disposition")
        val nameFromDisp = parseFilenameFromContentDisposition(disp)
        val bytes = conn.inputStream.use { ins ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                val room = MAX_URL_BYTES - total
                if (room <= 0) break
                val take = n.coerceAtMost(room)
                out.write(buf, 0, take)
                total += take
            }
            out.toByteArray()
        }
        conn.disconnect()
        val urlName = urlStr.substringBefore('?').substringAfterLast('/').takeIf { it.isNotBlank() }
        return Result(
            bytes = bytes,
            suggestedFileName = nameFromDisp ?: urlName,
            contentType = type,
            charsetFromHeader = charset
        )
    }

    private fun parseCharsetFromContentType(contentType: String?): String? {
        if (contentType.isNullOrBlank()) return null
        val m = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(contentType)
        return m?.groupValues?.get(1)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
    }

    private fun parseFilenameFromContentDisposition(disposition: String?): String? {
        if (disposition.isNullOrBlank()) return null
        val m = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(disposition)
        if (m != null) {
            return try {
                java.net.URLDecoder.decode(m.groupValues[1].trim(), StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                m.groupValues[1]
            }
        }
        val m2 = Regex("filename=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(disposition)
        if (m2 != null) return m2.groupValues[1]
        val m3 = Regex("filename=([^;]+)", RegexOption.IGNORE_CASE).find(disposition)
        return m3?.groupValues?.get(1)?.trim()
    }
}
