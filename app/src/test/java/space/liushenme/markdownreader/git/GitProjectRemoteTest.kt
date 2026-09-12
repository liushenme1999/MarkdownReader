package space.liushenme.markdownreader.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitProjectRemoteTest {

    @Test
    fun shaFromRefs_picksMatchingHead() {
        val refs = listOf(
            "refs/heads/dev" to "aaa111",
            "refs/heads/main" to "bbb222",
        )
        assertEquals("bbb222", GitProjectRemote.shaFromRefs(refs, "main"))
        assertEquals("aaa111", GitProjectRemote.shaFromRefs(refs, "  dev  "))
    }

    @Test
    fun shaFromRefs_missingBranchReturnsNull() {
        val refs = listOf("refs/heads/main" to "bbb222")
        assertNull(GitProjectRemote.shaFromRefs(refs, "release"))
        assertNull(GitProjectRemote.shaFromRefs(refs, "   "))
        assertNull(GitProjectRemote.shaFromRefs(emptyList(), "main"))
    }

    @Test
    fun shouldMarkRemoteUpdate_whenShaDiffers() {
        assertTrue(
            GitProjectRemote.shouldMarkRemoteUpdate(
                localSha = "abc",
                remoteSha = "def",
                hasLocalRepo = true,
            ),
        )
    }

    @Test
    fun shouldMarkRemoteUpdate_whenShaMatchesIgnoreCase() {
        assertFalse(
            GitProjectRemote.shouldMarkRemoteUpdate(
                localSha = "ABCDEF",
                remoteSha = "abcdef",
                hasLocalRepo = true,
            ),
        )
    }

    @Test
    fun shouldMarkRemoteUpdate_whenLocalRepoMissing() {
        assertTrue(
            GitProjectRemote.shouldMarkRemoteUpdate(
                localSha = "abc",
                remoteSha = "abc",
                hasLocalRepo = false,
            ),
        )
    }
}
