package space.liushenme.markdownreader.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import space.liushenme.markdownreader.data.local.BookContentHasher

class GitLinkedBookRestoreTest {

    private val url = "https://github.com/owner/repo"
    private val path = "docs/readme.md"
    private val hash = BookContentHasher.hashForGitDocument(url, path)

    @Test
    fun standaloneBook_alwaysRestores() {
        assertTrue(
            GitLinkedBookRestore.shouldRestore(
                gitRelativePath = null,
                contentHash = "abc",
                deletedBookHashes = emptySet(),
                deletedProjectUrls = listOf(url),
                liveProjectUrls = emptyList(),
            ),
        )
    }

    @Test
    fun gitBook_skipsWhenProjectTombstoned() {
        assertFalse(
            GitLinkedBookRestore.shouldRestore(
                gitRelativePath = path,
                contentHash = hash,
                deletedBookHashes = emptySet(),
                deletedProjectUrls = listOf(url),
                liveProjectUrls = emptyList(),
            ),
        )
    }

    @Test
    fun gitBook_skipsWhenBookHashTombstonedEvenIfHashStyleDiffersFromProject() {
        assertFalse(
            GitLinkedBookRestore.shouldRestore(
                gitRelativePath = path,
                contentHash = hash,
                deletedBookHashes = setOf(hash),
                deletedProjectUrls = emptyList(),
                liveProjectUrls = emptyList(),
            ),
        )
    }

    @Test
    fun gitBook_skipsWhenProjectGoneEvenIfBookHashUnknown() {
        assertFalse(
            GitLinkedBookRestore.shouldRestore(
                gitRelativePath = path,
                contentHash = "not-a-git-hash",
                deletedBookHashes = emptySet(),
                deletedProjectUrls = listOf(url),
                liveProjectUrls = emptyList(),
            ),
        )
    }

    @Test
    fun gitBook_neverRestoresStandaloneWhenLiveProjectMissing() {
        assertFalse(
            GitLinkedBookRestore.shouldRestore(
                gitRelativePath = path,
                contentHash = hash,
                deletedBookHashes = emptySet(),
                deletedProjectUrls = emptyList(),
                liveProjectUrls = emptyList(),
            ),
        )
    }

    @Test
    fun gitBook_tombstoneWinsEvenIfSameUrlStillListedLive() {
        assertFalse(
            GitLinkedBookRestore.shouldRestore(
                gitRelativePath = path,
                contentHash = hash,
                deletedBookHashes = emptySet(),
                deletedProjectUrls = listOf(url),
                liveProjectUrls = listOf(url),
            ),
        )
    }

    @Test
    fun gitBook_restoresWhenLiveProjectMatches() {
        assertTrue(
            GitLinkedBookRestore.shouldRestore(
                gitRelativePath = path,
                contentHash = hash,
                deletedBookHashes = emptySet(),
                deletedProjectUrls = emptyList(),
                liveProjectUrls = listOf("$url.git"),
            ),
        )
    }
}
