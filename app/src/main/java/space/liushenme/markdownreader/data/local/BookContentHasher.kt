package space.liushenme.markdownreader.data.local

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
}
