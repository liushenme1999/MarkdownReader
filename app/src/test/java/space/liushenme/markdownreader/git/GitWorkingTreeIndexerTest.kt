package space.liushenme.markdownreader.git

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        listOf(
            "script.py",
            "Main.java",
            "App.vue",
            "page.html",
            "styles.css",
            "data.json",
            "client.ts",
            "server.go",
        ).forEach { name ->
            File(root, "docs/$name").writeText("source")
        }
        File(root, "docs/pic.png").writeBytes(byteArrayOf(1))
        File(root, "docs/archive.bin").writeBytes(byteArrayOf(2))
        File(root, "node_modules").mkdirs()
        File(root, "node_modules/x.md").writeText("skip")
        File(root, "build").mkdirs()
        File(root, "build/Generated.java").writeText("skip")
        File(root, ".git").mkdirs()

        val tree = GitWorkingTreeIndexer.index(root)
        val paths = GitWorkingTreeIndexer.listDocumentPaths(root)
        val docs = tree.filterIsInstance<GitTreeNode.Folder>()
            .single { it.name == "docs" }
            .children
            .filterIsInstance<GitTreeNode.Document>()
            .associateBy { it.name }

        assertTrue(paths.contains("README.md"))
        assertTrue(paths.contains("docs/guide.md"))
        assertTrue(paths.contains("docs/note.txt"))
        listOf(
            "script.py",
            "Main.java",
            "App.vue",
            "page.html",
            "styles.css",
            "data.json",
            "client.ts",
            "server.go",
        ).forEach { name ->
            assertTrue("$name should be indexed", paths.contains("docs/$name"))
            assertEquals(GitTreeNode.DocFormat.CODE, docs.getValue(name).format)
        }
        assertFalse(paths.any { it.startsWith("node_modules") })
        assertFalse(paths.any { it.startsWith("build") })
        assertFalse(paths.any { it.endsWith(".png") })
        assertFalse(paths.any { it.endsWith(".bin") })
        assertTrue(tree.any { it is GitTreeNode.Folder && it.name == "docs" })
    }
}
