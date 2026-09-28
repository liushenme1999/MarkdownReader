package space.liushenme.markdownreader.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.liushenme.markdownreader.data.local.entity.BookEntity

class LegacyBookDocumentMigrationTest {

    @Test
    fun legacyMarkdownRow_mapsWithoutDatabaseRewrite() {
        val legacy = BookEntity(
            id = 12L,
            title = "Legacy",
            filePath = "content://provider/legacy.md",
            importFormat = "markdown",
            contentHash = "",
        )

        assertEquals(DocumentFormat.Markdown, legacy.toDocument().format)
        assertEquals(DocumentSourceType.ContentUri, legacy.toDocumentSourceDescriptor().type)
        assertEquals("", legacy.toDocument().contentHash)
    }

    @Test
    fun parsedBundlePath_takesPriorityForRestoredLegacyRows() {
        val restored = BookEntity(
            id = 13L,
            title = "Restored",
            filePath = "/foreign/device/body.txt",
            importFormat = "pdf",
            parsedBundlePath = "/local/parsed_books/13",
            contentHash = "legacy_13",
        )

        val source = restored.toDocumentSourceDescriptor()
        assertEquals(DocumentFormat.Pdf, restored.toDocument().format)
        assertEquals(DocumentSourceType.ParsedBundle, source.type)
        assertTrue(source.location.endsWith("parsed_books/13"))
    }
}
