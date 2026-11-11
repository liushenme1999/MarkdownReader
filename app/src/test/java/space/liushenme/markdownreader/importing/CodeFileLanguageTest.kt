package space.liushenme.markdownreader.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFileLanguageTest {

    @Test
    fun mapsEverySupportedExtensionIgnoringCase() {
        val expectedTokens = mapOf(
            "py" to "python",
            "pyw" to "python",
            "java" to "java",
            "kt" to "kotlin",
            "kts" to "kotlin",
            "vue" to "vue",
            "js" to "javascript",
            "jsx" to "javascript",
            "mjs" to "javascript",
            "cjs" to "javascript",
            "ts" to "typescript",
            "tsx" to "typescript",
            "html" to "html",
            "htm" to "html",
            "css" to "css",
            "scss" to "scss",
            "less" to "less",
            "xml" to "xml",
            "json" to "json",
            "yaml" to "yaml",
            "yml" to "yaml",
            "toml" to "toml",
            "ini" to "ini",
            "properties" to "properties",
            "sh" to "shell",
            "bash" to "shell",
            "zsh" to "shell",
            "ps1" to "powershell",
            "c" to "c",
            "h" to "c",
            "cpp" to "cpp",
            "cc" to "cpp",
            "cxx" to "cpp",
            "hpp" to "cpp",
            "go" to "go",
            "rs" to "rust",
            "rb" to "ruby",
            "php" to "php",
            "swift" to "swift",
        )

        expectedTokens.forEach { (extension, token) ->
            assertEquals(".$extension", token, CodeFileLanguage.fromFileName("Source.${extension.uppercase()}")?.token)
        }
    }

    @Test
    fun vueUsesStableTokenAndDisplayLabel() {
        assertEquals(CodeLanguage("vue", "Vue"), CodeFileLanguage.fromFileName("components/App.vue"))
        assertTrue(CodeFileLanguage.isCodeFile("page.html"))
        assertFalse(CodeFileLanguage.isCodeFile("README.unknown"))
        assertNull(CodeFileLanguage.fromFileName("archive.tar.gz"))
        assertNull(CodeFileLanguage.fromFileName("no-extension"))
    }

    @Test
    fun regularImporterKeepsExistingUnknownExtensionFallback() {
        assertEquals(ImportedBookFormat.TXT, ImportedBookFormat.fromFileName("App.vue"))
        assertEquals(ImportedBookFormat.TXT, ImportedBookFormat.fromFileName("Main.kt"))
        assertEquals(ImportedBookFormat.HTML, ImportedBookFormat.fromFileName("page.html"))
    }
}
