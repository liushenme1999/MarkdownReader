package space.liushenme.markdownreader.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import space.liushenme.markdownreader.importing.ParsedBookStorage
import space.liushenme.markdownreader.importing.PdfReaderContent

class BookContentHasherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun hashForGitDocument_usesCanonicalUrl() {
        val fromBrowse = BookContentHasher.hashForGitDocument(
            "https://github.com/owner/repo",
            "docs/readme.md",
        )
        val fromGitSuffix = BookContentHasher.hashForGitDocument(
            "https://github.com/owner/repo.git",
            "docs/readme.md",
        )
        assertEquals(fromBrowse, fromGitSuffix)
    }

    @Test
    fun matchesGitDocument_acceptsLegacyRawUrlHash() {
        val raw = "https://github.com/owner/repo.git"
        val path = "README.md"
        val legacy = BookContentHasher.sha256Hex("git|$raw|$path")
        assertTrue(BookContentHasher.matchesGitDocument(legacy, raw, path))
        assertTrue(
            BookContentHasher.matchesGitDocument(
                BookContentHasher.hashForGitDocument(raw, path),
                "https://github.com/owner/repo",
                path,
            ),
        )
    }

    @Test
    fun remoteContentZipNames_hashedBookDoesNotUseLocalId() {
        val names = BookContentHasher.remoteContentZipNames(
            contentHash = "abc123",
            localId = 1L,
            remoteId = 5L,
        )
        assertEquals(listOf("abc123", "legacy_5", "5"), names)
        assertFalse(names.contains("1"))
        assertFalse(names.contains("legacy_1"))
    }

    @Test
    fun remoteContentZipNames_legacyUsesRemoteAndLocalIds() {
        val names = BookContentHasher.remoteContentZipNames(
            contentHash = "legacy_9",
            localId = 2L,
            remoteId = 9L,
        )
        assertEquals(listOf("legacy_9", "9", "legacy_2", "2"), names)
    }

    @Test
    fun hashPdfContent_differsWhenPageImagesDiffer() {
        val body = PdfReaderContent.buildPageImgTag("book-asset://pdf_page_001.png")
        val a = BookContentHasher.hashPdfContent(body, mapOf("pdf_page_001.png" to byteArrayOf(1, 2, 3)))
        val b = BookContentHasher.hashPdfContent(body, mapOf("pdf_page_001.png" to byteArrayOf(9, 9, 9)))
        assertNotEquals(a, b)
        assertNotEquals(BookContentHasher.hashBody(body), a)
    }

    @Test
    fun matchesStoredHash_acceptsBodyHashOrPdfHash() {
        val dir = tmp.newFolder("bundle")
        val assets = File(dir, ParsedBookStorage.ASSETS_DIR).also { it.mkdirs() }
        val body = PdfReaderContent.buildPageImgTag("book-asset://pdf_page_001.png")
        File(dir, ParsedBookStorage.BODY_FILE).writeText(body)
        File(assets, "pdf_page_001.png").writeBytes(byteArrayOf(1, 2, 3, 4))
        val bodyHash = BookContentHasher.hashBody(body)
        val pdfHash = BookContentHasher.hashFromPdfBundle(dir)!!
        assertTrue(BookContentHasher.matchesStoredHash(bodyHash, dir))
        assertTrue(BookContentHasher.matchesStoredHash(pdfHash, dir))
        assertFalse(BookContentHasher.matchesStoredHash("deadbeef", dir))
    }
}
