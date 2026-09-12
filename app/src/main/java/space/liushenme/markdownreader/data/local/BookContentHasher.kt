package space.liushenme.markdownreader.data.local

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import space.liushenme.markdownreader.git.GitHubRepoUrlParser
import space.liushenme.markdownreader.importing.ParsedBookStorage

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
    ): String {
        val text = body.orEmpty()
        return if (text.isNotEmpty()) {
            hashBody(text)
        } else {
            hashEmptyFallback(importFormat, title, filePath)
        }
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
