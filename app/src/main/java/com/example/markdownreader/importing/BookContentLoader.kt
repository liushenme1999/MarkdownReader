package com.example.markdownreader.importing

import android.content.Context
import android.net.Uri
import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

private const val MAX_TEXT_SCAN_BYTES = 8 * 1024 * 1024
private const val MAX_URL_BYTES = 15 * 1024 * 1024

/**
 * 从 content://、file:// 或已下载字节加载正文；文本类走编码探测，EPUB/DOCX 做基础解压提取。
 */
object BookContentLoader {

    fun loadFromUri(context: Context, uri: Uri, format: ImportedBookFormat): String {
        return when (format) {
            ImportedBookFormat.MARKDOWN, ImportedBookFormat.TXT ->
                readPlainTextFromUri(context, uri)

            ImportedBookFormat.EPUB ->
                ZipFormatExtractors.extractEpubText(context, uri) ?: placeholder(format)

            ImportedBookFormat.DOCX ->
                ZipFormatExtractors.extractDocxText(context, uri) ?: placeholder(format)

            ImportedBookFormat.PDF, ImportedBookFormat.DOC,
            ImportedBookFormat.MOBI, ImportedBookFormat.AZW3 -> placeholder(format)
        }
    }

    fun loadFromUrlBytes(
        bytes: ByteArray,
        format: ImportedBookFormat,
        httpCharsetName: String?
    ): String {
        return when (format) {
            ImportedBookFormat.MARKDOWN, ImportedBookFormat.TXT ->
                decodeTextBytes(bytes, httpCharsetName)

            ImportedBookFormat.EPUB ->
                ZipFormatExtractors.extractEpubFromBytes(bytes) ?: placeholder(format)

            ImportedBookFormat.DOCX ->
                ZipFormatExtractors.extractDocxFromBytes(bytes) ?: placeholder(format)

            ImportedBookFormat.PDF, ImportedBookFormat.DOC,
            ImportedBookFormat.MOBI, ImportedBookFormat.AZW3 -> placeholder(format)
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

private object ZipFormatExtractors {

    fun extractEpubText(context: Context, uri: Uri): String? {
        val file = copyUriToCacheFile(context, uri, ".epub") ?: return null
        return try {
            extractEpubWithZipFile(file)
        } finally {
            file.delete()
        }
    }

    fun extractEpubFromBytes(bytes: ByteArray): String? {
        val tmp = File.createTempFile("epub_", ".epub")
        return try {
            tmp.writeBytes(bytes)
            extractEpubWithZipFile(tmp)
        } finally {
            tmp.delete()
        }
    }

    fun extractDocxText(context: Context, uri: Uri): String? {
        val file = copyUriToCacheFile(context, uri, ".docx") ?: return null
        return try {
            extractDocxWithZipFile(file)
        } finally {
            file.delete()
        }
    }

    fun extractDocxFromBytes(bytes: ByteArray): String? {
        val tmp = File.createTempFile("docx_", ".docx")
        return try {
            tmp.writeBytes(bytes)
            extractDocxWithZipFile(tmp)
        } finally {
            tmp.delete()
        }
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

    private fun extractEpubWithZipFile(zipPath: File): String? {
        return ZipFile(zipPath).use { zip ->
            val containerEntry = zip.entries().asSequence()
                .firstOrNull { it.name.equals("META-INF/container.xml", ignoreCase = true) }
                ?: return null
            val containerXml = zip.getInputStream(containerEntry).use { it.readBytes().decodeToString() }
            val opfPath = parseContainerRootfile(containerXml) ?: return null
            val opfEntry = zip.entries().asSequence()
                .firstOrNull { it.name.equals(opfPath, ignoreCase = true) }
                ?: return null
            val opfXml = zip.getInputStream(opfEntry).use { it.readBytes().decodeToString() }
            val opfDir = opfPath.substringBeforeLast('/', "")
            val parsed = parseOpfManifestAndSpine(opfXml) ?: return null
            val sb = StringBuilder()
            for (id in parsed.spine) {
                val href = parsed.manifest[id] ?: continue
                val entryPath = resolveRelativePath(opfDir, href)
                val ent = zip.entries().asSequence()
                    .firstOrNull { it.name.equals(entryPath, ignoreCase = true) }
                    ?: continue
                val raw = zip.getInputStream(ent).use { it.readBytes() }
                val charset = sniffXmlCharset(raw) ?: StandardCharsets.UTF_8
                val html = try {
                    String(raw, charset)
                } catch (_: Exception) {
                    String(raw, StandardCharsets.UTF_8)
                }
                val plain = htmlToPlainText(html)
                if (plain.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append("\n\n")
                    sb.append(plain.trim())
                }
            }
            sb.toString().ifBlank { null }
        }
    }

    private fun extractDocxWithZipFile(zipPath: File): String? {
        return ZipFile(zipPath).use { zip ->
            val entry = zip.entries().asSequence()
                .firstOrNull { it.name.equals("word/document.xml", ignoreCase = true) }
                ?: return null
            val xml = zip.getInputStream(entry).use { it.readBytes().decodeToString() }
            extractDocxRuns(xml).ifBlank { null }
        }
    }

    private fun parseContainerRootfile(xml: String): String? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val p = factory.newPullParser()
            p.setInput(xml.reader())
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val name = p.name?.lowercase() ?: ""
                    if (name == "rootfile") {
                        val path = p.getAttributeValue(null, "full-path")
                        if (!path.isNullOrBlank()) return path.replace('\\', '/')
                    }
                }
                event = p.next()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private data class OpfParse(val manifest: Map<String, String>, val spine: List<String>)

    private fun parseOpfManifestAndSpine(xml: String): OpfParse? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val p = factory.newPullParser()
            p.setInput(xml.reader())
            val manifest = linkedMapOf<String, String>()
            val spine = mutableListOf<String>()
            var inManifest = false
            var inSpine = false
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val rawName = p.name ?: ""
                    val name = rawName.substringAfter(':').lowercase()
                    when {
                        name == "manifest" -> inManifest = true
                        name == "spine" -> inSpine = true
                        inManifest && name == "item" -> {
                            val id = p.getAttributeValue(null, "id") ?: continue
                            val href = p.getAttributeValue(null, "href") ?: continue
                            manifest[id] = href.replace('\\', '/')
                        }
                        inSpine && name == "itemref" -> {
                            val idref = p.getAttributeValue(null, "idref")
                            if (!idref.isNullOrBlank()) spine.add(idref)
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    val name = (p.name ?: "").substringAfter(':').lowercase()
                    if (name == "manifest") inManifest = false
                    if (name == "spine") inSpine = false
                }
                event = p.next()
            }
            if (manifest.isEmpty() || spine.isEmpty()) null else OpfParse(manifest, spine)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveRelativePath(opfDir: String, href: String): String {
        val base = opfDir.trimEnd('/')
        val combined = if (base.isEmpty()) href else "$base/$href"
        val parts = combined.split('/').filter { it.isNotEmpty() && it != "." }
        val stack = ArrayDeque<String>()
        for (part in parts) {
            when (part) {
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun sniffXmlCharset(bytes: ByteArray): Charset? {
        val head = bytes.decodeToString(0, bytes.size.coerceAtMost(200)).lowercase()
        val m = Regex("encoding\\s*=\\s*[\"']([^\"']+)[\"']").find(head)
        val name = m?.groupValues?.get(1) ?: return null
        return try {
            Charset.forName(name)
        } catch (_: Exception) {
            null
        }
    }

    private fun htmlToPlainText(html: String): String {
        val noScript = html.replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
        val noTags = noScript.replace(Regex("<[^>]+>"), "\n")
        return HtmlCompat.fromHtml(
            noTags.replace("\n", "<br/>"),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString().trim()
    }

    private fun extractDocxRuns(xml: String): String {
        val re = Regex("<w:t[^>]*>([^<]*)</w:t>")
        return re.findAll(xml).joinToString("") { it.groupValues[1] }
            .replace(Regex("[ \\t\\r]+"), " ")
            .trim()
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
