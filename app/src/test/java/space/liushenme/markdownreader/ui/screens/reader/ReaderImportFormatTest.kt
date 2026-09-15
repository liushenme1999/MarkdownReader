package space.liushenme.markdownreader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.importing.ImportedBookFormat

class ReaderImportFormatTest {

    @Test
    fun legacyMarkdownRecord_withTxtSource_usesPlainTextRenderer() {
        val book = book(importFormat = "markdown", filePath = "/books/legacy.TXT")

        assertEquals(ImportedBookFormat.TXT, resolveReaderImportFormat(book))
    }

    @Test
    fun legacyMarkdownRecord_withTxtGitPath_usesPlainTextRenderer() {
        val book = book(
            importFormat = "markdown",
            filePath = "/internal/parsed/body",
            gitRelativePath = "novels/chapter.txt",
        )

        assertEquals(ImportedBookFormat.TXT, resolveReaderImportFormat(book))
    }

    @Test
    fun markdownRecord_withMarkdownSource_staysMarkdown() {
        val book = book(importFormat = "markdown", filePath = "/books/readme.md")

        assertEquals(ImportedBookFormat.MARKDOWN, resolveReaderImportFormat(book))
    }

    @Test
    fun explicitPdfRecord_isNotOverriddenByTxtLookingTitle() {
        val book = book(
            importFormat = "pdf",
            filePath = "/books/document.pdf",
            title = "document.txt",
        )

        assertEquals(ImportedBookFormat.PDF, resolveReaderImportFormat(book))
    }

    private fun book(
        importFormat: String,
        filePath: String,
        title: String = "book",
        gitRelativePath: String? = null,
    ) = BookEntity(
        title = title,
        filePath = filePath,
        importFormat = importFormat,
        gitRelativePath = gitRelativePath,
    )
}
