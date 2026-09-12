package space.liushenme.markdownreader.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookContentHasherTest {

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
}
