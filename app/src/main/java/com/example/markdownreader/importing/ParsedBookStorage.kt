package com.example.markdownreader.importing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 导入后统一落盘：每本书一个目录 `filesDir/parsed_books/{id}/`，内含
 * [BODY_FILE] UTF-8 正文、[TOC_FILE] 目录 JSON、可选 [COVER_JPG]/[COVER_PNG]，
 * 以及 PDF 等格式抽出的内嵌图片 `assets/<id>.<ext>`。
 *
 * 正文写盘时仍保留 `book-asset://<id>` 占位（解析时填入的相对引用），读盘时再把占位
 * 实化为 `file://<dir>/assets/<id>` 绝对路径——这样万一 bundle 目录搬迁，body 仍可重建链接。
 */
object ParsedBookStorage {

    const val BODY_FILE = "body.txt"
    const val TOC_FILE = "toc.json"
    const val COVER_JPG = "cover.jpg"
    const val COVER_PNG = "cover.png"
    const val ASSETS_DIR = "assets"

    private const val ASSET_SCHEME = "book-asset://"

    fun bundleDir(context: Context, bookId: Long): File =
        File(context.filesDir, "parsed_books/$bookId")

    fun writeBundle(dir: File, extracted: ExtractedBookText, coverBytes: ByteArray?): Boolean {
        return runCatching {
            dir.mkdirs()
            File(dir, BODY_FILE).writeText(extracted.body, StandardCharsets.UTF_8)
            File(dir, TOC_FILE).writeText(tocToJson(extracted.toc), StandardCharsets.UTF_8)
            when {
                coverBytes == null || coverBytes.isEmpty() -> Unit
                isJpegMagic(coverBytes) -> File(dir, COVER_JPG).writeBytes(coverBytes)
                isPngMagic(coverBytes) -> File(dir, COVER_PNG).writeBytes(coverBytes)
                else -> File(dir, COVER_JPG).writeBytes(coverBytes)
            }
            if (extracted.assets.isNotEmpty()) {
                val assetsDir = File(dir, ASSETS_DIR).apply { mkdirs() }
                for ((id, bytes) in extracted.assets) {
                    if (bytes.isEmpty()) continue
                    val safeName = sanitizeAssetId(id) ?: continue
                    File(assetsDir, safeName).writeBytes(bytes)
                }
            }
            true
        }.getOrDefault(false)
    }

    fun readBundle(dir: File): ExtractedBookText? {
        val bodyFile = File(dir, BODY_FILE)
        if (!bodyFile.isFile) return null
        val rawBody = runCatching { bodyFile.readText(StandardCharsets.UTF_8) }.getOrNull()
            ?: return null
        val body = materializeAssetUrls(rawBody, dir)
        val tocFile = File(dir, TOC_FILE)
        val toc = if (tocFile.isFile) {
            runCatching { tocFromJson(tocFile.readText(StandardCharsets.UTF_8)) }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        return ExtractedBookText(body = body, toc = toc, coverImageBytes = null)
    }

    /** 把 body 内的 `book-asset://xxx` 替换为 `file:///abs/path/to/assets/xxx`。 */
    private fun materializeAssetUrls(body: String, dir: File): String {
        if (!body.contains(ASSET_SCHEME)) return body
        val assetsDir = File(dir, ASSETS_DIR)
        val base = assetsDir.absolutePath
        val re = Regex("book-asset://([A-Za-z0-9._-]+)")
        return re.replace(body) { match ->
            val id = match.groupValues[1]
            val f = File(assetsDir, id)
            if (f.isFile) "file://$base/$id" else match.value
        }
    }

    /** 防御目录穿越；仅允许字母数字 + 限定标点。 */
    private fun sanitizeAssetId(id: String): String? {
        if (id.isBlank() || id.length > 80) return null
        for (ch in id) {
            if (!(ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-')) return null
        }
        if (".." in id || id.startsWith(".")) return null
        return id
    }

    fun deleteBundleDir(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).deleteRecursively() }
    }

    private fun tocToJson(entries: List<ImportedTocEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject().apply {
                    put("level", e.level)
                    put("title", e.title)
                    put("sourceOffset", e.sourceOffset)
                }
            )
        }
        return arr.toString()
    }

    private fun tocFromJson(json: String): List<ImportedTocEntry> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        val out = ArrayList<ImportedTocEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val level = o.optInt("level", 1)
            val title = o.optString("title", "").trim()
            val off = o.optInt("sourceOffset", -1)
            if (title.isNotEmpty() && off >= 0) {
                out += ImportedTocEntry(level = level, title = title, sourceOffset = off)
            }
        }
        return out
    }

    private fun isJpegMagic(b: ByteArray): Boolean =
        b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()

    private fun isPngMagic(b: ByteArray): Boolean =
        b.size >= 8 &&
            b[0] == 0x89.toByte() &&
            b[1] == 0x50.toByte() &&
            b[2] == 0x4E.toByte() &&
            b[3] == 0x47.toByte()
}
