package space.liushenme.markdownreader.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitPathUtilsTest {

    @Test
    fun ancestorPaths_nestedFile() {
        val ancestors = GitPathUtils.ancestorPaths("docs/guide/intro.md")
        assertEquals(setOf("docs", "docs/guide"), ancestors)
    }

    @Test
    fun ancestorPaths_rootFile() {
        assertTrue(GitPathUtils.ancestorPaths("README.md").isEmpty())
    }

    @Test
    fun fileName_extractsLastSegment() {
        assertEquals("intro.md", GitPathUtils.fileName("docs/guide/intro.md"))
        assertEquals("README.md", GitPathUtils.fileName("README.md"))
    }
}
