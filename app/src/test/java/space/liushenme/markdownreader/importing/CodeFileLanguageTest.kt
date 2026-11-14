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
    fun regularImporterDetectsCodeFilesButKeepsDocumentFormats() {
        // 本地导入现在也支持源码文件（全屏代码阅读 + 高亮），语言按扩展名推断。
        assertEquals(ImportedBookFormat.CODE, ImportedBookFormat.fromFileName("App.vue"))
        assertEquals(ImportedBookFormat.CODE, ImportedBookFormat.fromFileName("Main.kt"))
        // 文档类扩展名仍走各自格式，不被代码分支吞掉。
        assertEquals(ImportedBookFormat.HTML, ImportedBookFormat.fromFileName("page.html"))
        assertEquals(ImportedBookFormat.MARKDOWN, ImportedBookFormat.fromFileName("notes.md"))
        // 完全未知的扩展名仍回退纯文本。
        assertEquals(ImportedBookFormat.TXT, ImportedBookFormat.fromFileName("data.unknown"))
    }
}
