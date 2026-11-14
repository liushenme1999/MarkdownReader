package space.liushenme.markdownreader.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.liushenme.markdownreader.importing.ImportedBookFormat

class ReadingFormatProfileTest {

    @Test
    fun mapsEveryBookFormatToItsOwnProfile() {
        assertEquals(ReadingFormatProfile.MARKDOWN, ReadingFormatProfile.fromBookFormat(ImportedBookFormat.MARKDOWN))
        assertEquals(ReadingFormatProfile.PLAIN_TEXT, ReadingFormatProfile.fromBookFormat(ImportedBookFormat.TXT))
        assertEquals(ReadingFormatProfile.HTML, ReadingFormatProfile.fromBookFormat(ImportedBookFormat.HTML))
        assertEquals(ReadingFormatProfile.PDF, ReadingFormatProfile.fromBookFormat(ImportedBookFormat.PDF))
        assertEquals(ReadingFormatProfile.CODE, ReadingFormatProfile.fromBookFormat(ImportedBookFormat.CODE))
    }

    @Test
    fun profilesAreDistinctAndRoundTrip() {
        val keys = ReadingFormatProfile.entries.map { it.storageKey }
        assertEquals(keys.size, keys.toSet().size)
        ReadingFormatProfile.entries.forEach { profile ->
            assertEquals(profile, ReadingFormatProfile.fromStorageKey(profile.storageKey))
        }
        assertNull(ReadingFormatProfile.fromStorageKey("unknown"))
    }

    @Test
    fun pdfHasNoCodeStyleOrTypography() {
        assertFalse(ReadingFormatProfile.PDF.usesCodeStyle)
        assertFalse(ReadingFormatProfile.PDF.usesTypography)
        assertTrue(ReadingFormatProfile.MARKDOWN.usesCodeStyle)
        assertTrue(ReadingFormatProfile.CODE.usesCodeStyle)
        assertTrue(ReadingFormatProfile.PLAIN_TEXT.usesTypography)
        assertFalse(ReadingFormatProfile.PLAIN_TEXT.usesCodeStyle)
    }
}
