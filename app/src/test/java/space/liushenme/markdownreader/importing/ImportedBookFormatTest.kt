package space.liushenme.markdownreader.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedBookFormatTest {
    @Test
    fun detectsHtmlFileAndMimeTypes() {
        assertEquals(ImportedBookFormat.HTML, BookImportSupport.detectFormat("page.html", null))
        assertEquals(ImportedBookFormat.HTML, BookImportSupport.detectFormat("page.htm", null))
        assertEquals(ImportedBookFormat.HTML, BookImportSupport.detectFormat(null, "text/html"))
        assertEquals("html", ImportedBookFormat.HTML.storedKey)
    }

    @Test
    fun detectsFullHtmlBodiesForLegacyImports() {
        assertTrue(HtmlDocumentSupport.looksLikeHtmlBody("<!DOCTYPE html><html></html>"))
        assertTrue(HtmlDocumentSupport.looksLikeHtmlBody("  <html><body></body></html>"))
    }

    @Test
    fun detectsLocalSourceCodeFiles() {
        // 本地导入的源码文件走 CODE 格式（全屏代码阅读 + 语法高亮）。
        val samples = mapOf(
            "short_demo.py" to "python",
            "Main.java" to "java",
            "app.ts" to "typescript",
            "index.js" to "javascript",
            "config.json" to "json",
            "build.gradle.kts" to "kotlin",
            "main.go" to "go",
            "lib.rs" to "rust",
            "style.css" to "css",
            "script.sh" to "shell",
        )
        for ((name, token) in samples) {
            assertEquals("$name 应识别为代码文件", ImportedBookFormat.CODE, BookImportSupport.detectFormat(name, null))
            assertEquals("$name 的语言", token, CodeFileLanguage.fromFileName(name)?.token)
        }
    }

    @Test
    fun htmlAndDocumentsKeepTheirOwnFormat() {
        // .html 仍走 WebView 预览，不能被代码分支吞掉。
        assertEquals(ImportedBookFormat.HTML, BookImportSupport.detectFormat("report.html", "text/html"))
        assertEquals(ImportedBookFormat.MARKDOWN, BookImportSupport.detectFormat("a.md", null))
        assertEquals(ImportedBookFormat.TXT, BookImportSupport.detectFormat("a.txt", null))
        assertEquals(ImportedBookFormat.PDF, BookImportSupport.detectFormat("a.pdf", null))
    }

    @Test
    fun unknownExtensionStillFallsBackToPlainText() {
        assertEquals(ImportedBookFormat.TXT, BookImportSupport.detectFormat("a.unknownext", null))
    }

    @Test
    fun codeFileTitleKeepsExtensionForLanguageInference() {
        // 本地代码文件的 filePath 是 content:// URI（无扩展名），语言靠保留扩展名的书名推断。
        assertEquals("short_demo.py", BookImportSupport.stripKnownExtension("short_demo.py"))
        assertEquals("notes", BookImportSupport.stripKnownExtension("notes.md"))
    }
}
