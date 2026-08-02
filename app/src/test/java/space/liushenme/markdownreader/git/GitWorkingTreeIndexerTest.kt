package space.liushenme.markdownreader.git

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class GitWorkingTreeIndexerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun index_buildsTreeAndSkipsNoise() {
        val root = tmp.root
        File(root, "README.md").writeText("# hi")
        File(root, "docs").mkdirs()
        File(root, "docs/guide.md").writeText("guide")
        File(root, "docs/note.txt").writeText("note")
        File(root, "docs/pic.png").writeBytes(byteArrayOf(1))
        File(root, "node_modules").mkdirs()
        File(root, "node_modules/x.md").writeText("skip")
        File(root, ".git").mkdirs()

        val tree = GitWorkingTreeIndexer.index(root)
        val paths = GitWorkingTreeIndexer.listDocumentPaths(root)

        assertTrue(paths.contains("README.md"))
        assertTrue(paths.contains("docs/guide.md"))
        assertTrue(paths.contains("docs/note.txt"))
        assertEquals(false, paths.any { it.startsWith("node_modules") })
        assertTrue(tree.any { it is GitTreeNode.Folder && it.name == "docs" })
    }
}
