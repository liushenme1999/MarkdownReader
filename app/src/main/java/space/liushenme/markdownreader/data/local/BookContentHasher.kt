package space.liushenme.markdownreader.data.local

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import space.liushenme.markdownreader.git.GitHubRepoUrlParser
import space.liushenme.markdownreader.importing.ParsedBookStorage
import space.liushenme.markdownreader.importing.PdfReaderContent

object BookContentHasher {
    const val LEGACY_PREFIX = "legacy_"

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun sha256Hex(text: String): String =
        sha256Hex(text.toByteArray(StandardCharsets.UTF_8))

    fun hashBody(body: String): String = sha256Hex(body)

    fun hashEmptyFallback(importFormat: String, title: String, filePath: String): String =
        sha256Hex("empty|$importFormat|$title|$filePath")

    fun hashFromBodyFile(bodyFile: File): String? {
        if (!bodyFile.isFile) return null
        return runCatching { sha256Hex(bodyFile.readBytes()) }.getOrNull()
    }

    fun hashForBook(
        body: String?,
        importFormat: String,
        title: String,
        filePath: String,
        assets: Map<String, ByteArray> = emptyMap(),
    ): String {
        val text = body.orEmpty()
        val pdfAssets = assets.filterKeys { it.contains("pdf_page_", ignoreCase = true) }
        if (text.isNotEmpty() && pdfAssets.isNotEmpty()) {
            return hashPdfContent(text, pdfAssets)
        }
        return if (text.isNotEmpty()) {
            hashBody(text)
        } else {
            hashEmptyFallback(importFormat, title, filePath)
        }
    }

    /**
     * PDF 跨设备身份：正文 + 页图字节。仅 hash HTML 骨架时，相同页数的不同 PDF 会撞车。
     */
    fun hashPdfContent(body: String, assets: Map<String, ByteArray>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(body.toByteArray(StandardCharsets.UTF_8))
        for (name in assets.keys.sorted()) {
            digest.update(0)
            digest.update(name.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(assets.getValue(name))
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    /** 从磁盘流式哈希 PDF 页图，避免把整本读进内存。 */
    fun hashPdfFromAssetsDir(body: String, assetsDir: File): String? {
        val names = PdfReaderContent.referencedPageAssetNames(body)
        if (names.isEmpty() || !assetsDir.isDirectory) return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(body.toByteArray(StandardCharsets.UTF_8))
        val buf = ByteArray(8192)
        for (name in names.sorted()) {
            val file = File(assetsDir, name)
            if (!file.isFile) return null
            digest.update(0)
            digest.update(name.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            runCatching {
                file.inputStream().use { ins ->
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        digest.update(buf, 0, n)
                    }
                }
            }.getOrElse { return null }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    fun hashFromPdfBundle(bundleDir: File): String? {
        val bodyFile = File(bundleDir, ParsedBookStorage.BODY_FILE)
        if (!bodyFile.isFile) return null
        val body = runCatching { bodyFile.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return null
        return hashPdfFromAssetsDir(body, File(bundleDir, ParsedBookStorage.ASSETS_DIR))
    }

    fun matchesStoredHash(expectedHash: String, bundleDir: File): Boolean {
        if (expectedHash.isBlank() || isLegacy(expectedHash)) return true
        val bodyHash = hashFromBodyFile(File(bundleDir, ParsedBookStorage.BODY_FILE))
        if (bodyHash == expectedHash) return true
        return hashFromPdfBundle(bundleDir) == expectedHash
    }

    /**
     * WebDAV `books/{name}.zip` 下载候选。
     * 已有真实 contentHash 时，不得用本机主键去撞 `{localId}.zip`（常是另一本旧 Markdown）。
     */
    fun remoteContentZipNames(
        contentHash: String,
        localId: Long,
        remoteId: Long? = null,
        extraHashes: List<String> = emptyList(),
    ): List<String> {
        val names = linkedSetOf<String>()
        if (contentHash.isNotBlank() && !isLegacy(contentHash)) {
            names += contentHash
        }
        extraHashes.filter { it.isNotBlank() }.forEach { names += it }
        val numericIds = linkedSetOf<Long>()
        if (remoteId != null && remoteId > 0L) numericIds += remoteId
        if (contentHash.isBlank() || isLegacy(contentHash)) {
            if (localId > 0L) numericIds += localId
        }
        for (id in numericIds) {
            names += legacyHash(id)
            names += id.toString()
        }
        return names.toList()
    }

    fun hashFromBundleOrFallback(
        bundleDir: File,
        importFormat: String,
        title: String,
        filePath: String,
    ): String {
        val fromFile = hashFromBodyFile(File(bundleDir, ParsedBookStorage.BODY_FILE))
        if (fromFile != null) return fromFile
        return hashEmptyFallback(importFormat, title, filePath)
    }

    fun isLegacy(hash: String): Boolean = hash.startsWith(LEGACY_PREFIX)

    fun legacyHash(bookId: Long): String = "$LEGACY_PREFIX$bookId"

    /** Git 文档稳定身份：不随 pull 后正文变化，避免唯一索引冲突。 */
    fun hashForGitDocument(remoteUrl: String, relativePath: String): String =
        sha256Hex("git|${canonicalGitRemoteUrl(remoteUrl)}|$relativePath")

    /** 兼容旧备份里按原始 URL 计算的哈希。 */
    fun matchesGitDocument(contentHash: String, remoteUrl: String, relativePath: String): Boolean {
        if (contentHash.isBlank() || relativePath.isBlank()) return false
        if (hashForGitDocument(remoteUrl, relativePath) == contentHash) return true
        if (sha256Hex("git|$remoteUrl|$relativePath") == contentHash) return true
        return GitHubRepoUrlParser.lookupUrls(remoteUrl).any { candidate ->
            sha256Hex("git|$candidate|$relativePath") == contentHash
        }
    }

    private fun canonicalGitRemoteUrl(remoteUrl: String): String =
        GitHubRepoUrlParser.canonicalBrowseUrl(remoteUrl) ?: remoteUrl.trim()
}
