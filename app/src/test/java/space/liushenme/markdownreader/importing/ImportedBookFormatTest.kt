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
}
