package space.liushenme.markdownreader.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentMediaResourceTest {
    @Test
    fun snapshot_exposesImagesDiagramsFormulasAndPdfPagesThroughOneModel() {
        val blocks = listOf<DocumentBlock>(
            DocumentBlock.Image("n", SourceRange(0, 1), "n", "https://example.com/a.png", "a"),
            DocumentBlock.Diagram("d", SourceRange(1, 2), "d", "diagram://mermaid/id"),
            DocumentBlock.Formula("f", SourceRange(2, 3), "f", "x+y", true),
            DocumentBlock.Image("p", SourceRange(3, 4), "p", "file:///page.png", "page", 2),
        )
        val snapshot = DocumentSnapshot(
            document = Document(1L, "doc", DocumentFormat.Markdown, "hash"),
            source = DocumentSourceDescriptor(DocumentSourceType.ParsedBundle, "/bundle"),
            content = "ndfp",
            toc = emptyList(),
            blocks = blocks,
            chunkIndex = DocumentBlockParser.buildChunkIndex(4, blocks),
        )

        val resources = snapshot.mediaResources()

        assertEquals(4, resources.size)
        assertTrue(resources[0] is DocumentMediaResource.NetworkImage)
        assertTrue(resources[1] is DocumentMediaResource.Diagram)
        assertTrue(resources[2] is DocumentMediaResource.Formula)
        assertEquals(2, (resources[3] as DocumentMediaResource.PdfPage).pageIndex)
    }
}
