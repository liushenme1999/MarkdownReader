package space.liushenme.markdownreader.git

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitProjectStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun hasValidRepo_requiresGitDir() {
        assertFalse(GitProjectStorage.hasValidRepo(null))
        assertFalse(GitProjectStorage.hasValidRepo(""))
        assertFalse(GitProjectStorage.hasValidRepo("/path/does/not/exist"))

        val emptyDir = tmp.newFolder("empty")
        assertFalse(GitProjectStorage.hasValidRepo(emptyDir.absolutePath))

        val repo = tmp.newFolder("repo")
        File(repo, ".git").mkdir()
        assertTrue(GitProjectStorage.hasValidRepo(repo.absolutePath))
    }
}
