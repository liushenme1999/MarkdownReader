package space.liushenme.markdownreader.document

import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.ImportedTocEntry

enum class DocumentFormat { Markdown, PlainText, Pdf }

enum class DocumentSourceType { ParsedBundle, ContentUri, RemoteUrl, GitWorkingTree, Missing }

data class Document(
    val documentId: Long,
    val title: String,
    val format: DocumentFormat,
    val contentHash: String,
)

data class DocumentSourceDescriptor(
    val type: DocumentSourceType,
    val location: String,
    val version: String = "",
)

data class SourceRange(val start: Int, val endExclusive: Int) {
    init {
        require(start >= 0)
        require(endExclusive >= start)
    }

    val length: Int get() = endExclusive - start
    operator fun contains(offset: Int): Boolean = offset in start until endExclusive
}

sealed interface DocumentBlock {
    val id: String
    val range: SourceRange
    val source: String

    data class Heading(
        override val id: String,
        override val range: SourceRange,
        override val source: String,
        val level: Int,
        val title: String,
    ) : DocumentBlock

    data class Paragraph(
        override val id: String,
        override val range: SourceRange,
        override val source: String,
    ) : DocumentBlock

    data class CodeBlock(
        override val id: String,
        override val range: SourceRange,
        override val source: String,
        val language: String?,
    ) : DocumentBlock

    data class Image(
        override val id: String,
        override val range: SourceRange,
        override val source: String,
        val destination: String,
        val altText: String,
        val pdfPageIndex: Int? = null,
    ) : DocumentBlock

    data class Formula(
        override val id: String,
        override val range: SourceRange,
        override val source: String,
        val expression: String,
        val block: Boolean,
    ) : DocumentBlock

    data class Table(
        override val id: String,
        override val range: SourceRange,
        override val source: String,
    ) : DocumentBlock

    data class Diagram(
        override val id: String,
        override val range: SourceRange,
        override val source: String,
        val destination: String,
    ) : DocumentBlock

    data class Quote(
        override val id: String,
        override val range: SourceRange,
        override val source: String,
    ) : DocumentBlock

    data class ListBlock(
        override val id: String,
        override val range: SourceRange,
        override val source: String,
        val ordered: Boolean,
    ) : DocumentBlock
}

data class DocumentChunk(
    val index: Int,
    val range: SourceRange,
    val firstBlockIndex: Int,
    val lastBlockIndex: Int,
)

data class DocumentChunkIndex(
    val contentLength: Int,
    val chunks: List<DocumentChunk>,
) {
    val boundaries: IntArray
        get() = buildList {
            add(0)
            chunks.forEach { add(it.range.start); add(it.range.endExclusive) }
            add(contentLength)
        }.distinct().sorted().toIntArray()

    fun windowAround(position: Int, neighborChunks: Int = 1): SourceRange {
        if (contentLength <= 0) return SourceRange(0, 0)
        if (chunks.isEmpty()) return SourceRange(0, contentLength)
        val safe = position.coerceIn(0, contentLength - 1)
        val center = chunks.indexOfFirst { safe in it.range }
            .takeIf { it >= 0 }
            ?: chunks.indexOfLast { it.range.start <= safe }.coerceAtLeast(0)
        val first = (center - neighborChunks.coerceAtLeast(0)).coerceAtLeast(0)
        val last = (center + neighborChunks.coerceAtLeast(0)).coerceAtMost(chunks.lastIndex)
        return SourceRange(chunks[first].range.start, chunks[last].range.endExclusive)
    }

    companion object {
        val EMPTY = DocumentChunkIndex(0, emptyList())
    }
}

data class DocumentArtifact(
    val relativePath: String,
    val kind: Kind,
    val byteSize: Long,
    val checksum: String = "",
) {
    enum class Kind { Body, Toc, Cover, Image, PdfPage, DiagramPayload, BlockIndex }
}

data class DocumentSnapshot(
    val document: Document,
    val source: DocumentSourceDescriptor,
    val content: String,
    val toc: List<ImportedTocEntry>,
    val blocks: List<DocumentBlock>,
    val chunkIndex: DocumentChunkIndex,
    val artifacts: List<DocumentArtifact> = emptyList(),
)

fun BookEntity.toDocument(): Document = Document(
    documentId = id,
    title = title,
    format = when (ImportedBookFormat.fromStored(importFormat)) {
        ImportedBookFormat.MARKDOWN -> DocumentFormat.Markdown
        ImportedBookFormat.TXT -> DocumentFormat.PlainText
        ImportedBookFormat.PDF -> DocumentFormat.Pdf
    },
    contentHash = contentHash,
)

fun BookEntity.toDocumentSourceDescriptor(): DocumentSourceDescriptor {
    val path = parsedBundlePath?.takeIf { it.isNotBlank() } ?: filePath
    val type = when {
        !parsedBundlePath.isNullOrBlank() -> DocumentSourceType.ParsedBundle
        gitProjectId != null -> DocumentSourceType.GitWorkingTree
        filePath.startsWith("content://", ignoreCase = true) -> DocumentSourceType.ContentUri
        filePath.startsWith("http://", ignoreCase = true) ||
            filePath.startsWith("https://", ignoreCase = true) -> DocumentSourceType.RemoteUrl
        else -> DocumentSourceType.Missing
    }
    return DocumentSourceDescriptor(type = type, location = path, version = contentHash)
}
