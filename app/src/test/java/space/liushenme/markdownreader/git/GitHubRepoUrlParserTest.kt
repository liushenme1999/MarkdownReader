package space.liushenme.markdownreader.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubRepoUrlParserTest {

    @Test
    fun parse_httpsUrl() {
        val p = GitHubRepoUrlParser.parse("https://github.com/owner/repo")!!
        assertEquals("owner", p.owner)
        assertEquals("repo", p.repo)
        assertEquals("https://github.com/owner/repo.git", p.cloneUrl)
    }

    @Test
    fun parse_httpsUrlWithGitSuffixAndPath() {
        val p = GitHubRepoUrlParser.parse("https://github.com/owner/repo.git/tree/main")!!
        assertEquals("repo", p.repo)
        assertEquals("https://github.com/owner/repo.git", p.cloneUrl)
    }

    @Test
    fun parse_sshStyle() {
        val p = GitHubRepoUrlParser.parse("git@github.com:owner/repo.git")!!
        assertEquals("owner/repo", p.displayName)
    }

    @Test
    fun parse_ownerRepoShorthand() {
        val p = GitHubRepoUrlParser.parse("torvalds/linux")!!
        assertEquals("torvalds", p.owner)
        assertEquals("linux", p.repo)
    }

    @Test
    fun parse_rejectsNonGithub() {
        assertNull(GitHubRepoUrlParser.parse("https://gitlab.com/owner/repo"))
        assertNull(GitHubRepoUrlParser.parse(""))
        assertNull(GitHubRepoUrlParser.parse("not a url"))
    }
}
