package space.liushenme.markdownreader.document

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import space.liushenme.markdownreader.importing.ExtractedBookText
import space.liushenme.markdownreader.importing.ImportedTocEntry
import space.liushenme.markdownreader.importing.ParsedBookStorage

class DocumentArtifactManifestTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writeBundle_createsValidatedManifestAndBlockIndex() {
        val dir = tmp.newFolder("bundle")
        val written = ParsedBookStorage.writeBundle(
            dir,
            ExtractedBookText(
                body = "# Heading\n\nBody text.",
                toc = listOf(ImportedTocEntry(1, "Heading", 0)),
                assets = mapOf("image.png" to byteArrayOf(1, 2, 3)),
            ),
            coverBytes = null,
        )

        assertTrue(written)
        assertTrue(File(dir, DocumentArtifactManifest.FILE_NAME).isFile)
        assertTrue(File(dir, DocumentArtifactManifest.BLOCK_INDEX_FILE).isFile)
        assertEquals(
            DocumentArtifactManifest.Validation.Valid,
            DocumentArtifactManifest.validation(dir),
        )
        assertNotNull(ParsedBookStorage.readBundle(dir))
    }

    @Test
    fun changedBody_isRejectedInsteadOfReturningCorruptDerivedData() {
        val dir = tmp.newFolder("tampered")
        ParsedBookStorage.writeBundle(
            dir,
            ExtractedBookText.plainBody("# Original"),
            coverBytes = null,
        )
        File(dir, ParsedBookStorage.BODY_FILE).appendText("\nchanged")

        assertEquals(
            DocumentArtifactManifest.Validation.BodyMismatch,
            DocumentArtifactManifest.validation(dir),
        )
        assertNull(ParsedBookStorage.readBundle(dir))
    }

    @Test
    fun legacyBundle_withoutManifestRemainsReadableAndGetsSidecars() {
        val dir = tmp.newFolder("legacy")
        File(dir, ParsedBookStorage.BODY_FILE).writeText("# Legacy\n\nCompatible body")

        assertEquals(
            DocumentArtifactManifest.Validation.MissingManifest,
            DocumentArtifactManifest.validation(dir),
        )
        assertNotNull(ParsedBookStorage.readBundle(dir))
        assertEquals(
            DocumentArtifactManifest.Validation.Valid,
            DocumentArtifactManifest.validation(dir),
        )
    }
}
