package space.liushenme.markdownreader.document

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import space.liushenme.markdownreader.data.backup.BookContentSync
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.repository.BookRepository
import space.liushenme.markdownreader.importing.BookContentLoader
import space.liushenme.markdownreader.importing.BookTocEnricher
import space.liushenme.markdownreader.importing.ExtractedBookText
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.ParsedBookStorage
import space.liushenme.markdownreader.importing.PdfReaderContent
import space.liushenme.markdownreader.importing.UrlBookDownloader
import space.liushenme.markdownreader.markdown.MarkdownPreprocessor

enum class DocumentOpenFailure {
    BookNotFound,
    MissingSource,
    BrokenBundle,
    NetworkRequired,
    UnsupportedFormat,
    PermissionDenied,
    Failed,
}

sealed interface DocumentOpenResult {
    data class Ready(val book: BookEntity, val snapshot: DocumentSnapshot) : DocumentOpenResult
    data class Failed(
        val book: BookEntity?,
        val reason: DocumentOpenFailure,
        val detail: String? = null,
    ) : DocumentOpenResult
}

internal sealed interface DocumentSourceLoad {
    data class Ready(
        val extracted: ExtractedBookText,
        val descriptor: DocumentSourceDescriptor,
        val bundleDir: File? = null,
    ) : DocumentSourceLoad

    data class Failed(val reason: DocumentOpenFailure, val detail: String? = null) : DocumentSourceLoad
}

internal interface DocumentSource {
    val descriptor: DocumentSourceDescriptor
    fun load(): DocumentSourceLoad
}

private class ParsedBundleDocumentSource(
    private val book: BookEntity,
    private val dir: File,
) : DocumentSource {
    override val descriptor = DocumentSourceDescriptor(
        type = DocumentSourceType.ParsedBundle,
        location = dir.absolutePath,
        version = book.contentHash,
    )

    override fun load(): DocumentSourceLoad {
        val extracted = ParsedBookStorage.readBundle(dir)
            ?.takeIf { it.body.isNotEmpty() && ParsedBookStorage.isCompleteBundle(dir, book.importFormat) }
            ?: return DocumentSourceLoad.Failed(DocumentOpenFailure.BrokenBundle)
        return DocumentSourceLoad.Ready(extracted, descriptor, dir)
    }
}

private class UriDocumentSource(
    private val context: Context,
    private val book: BookEntity,
) : DocumentSource {
    override val descriptor = DocumentSourceDescriptor(
        type = DocumentSourceType.ContentUri,
        location = book.filePath,
        version = book.contentHash,
    )

    override fun load(): DocumentSourceLoad = try {
        val format = ImportedBookFormat.fromStored(book.importFormat)
        DocumentSourceLoad.Ready(
            BookContentLoader.loadExtractedFromUri(context, book.filePath.toUri(), format),
            descriptor,
        )
    } catch (error: SecurityException) {
        DocumentSourceLoad.Failed(DocumentOpenFailure.PermissionDenied, error.message)
    } catch (error: FileNotFoundException) {
        DocumentSourceLoad.Failed(DocumentOpenFailure.MissingSource, error.message)
    } catch (error: Exception) {
        DocumentSourceLoad.Failed(DocumentOpenFailure.Failed, error.message)
    }
}

private class RemoteDocumentSource(
    private val context: Context,
    private val book: BookEntity,
) : DocumentSource {
    override val descriptor = DocumentSourceDescriptor(
        type = DocumentSourceType.RemoteUrl,
        location = book.filePath,
        version = book.contentHash,
    )

    override fun load(): DocumentSourceLoad = try {
        val format = ImportedBookFormat.fromStored(book.importFormat)
        val downloaded = UrlBookDownloader.download(book.filePath)
        DocumentSourceLoad.Ready(
            BookContentLoader.loadExtractedFromUrlBytes(
                context,
                downloaded.bytes,
                format,
                downloaded.charsetFromHeader,
            ),
            descriptor,
        )
    } catch (error: IOException) {
        DocumentSourceLoad.Failed(DocumentOpenFailure.NetworkRequired, error.message)
    } catch (error: Exception) {
        DocumentSourceLoad.Failed(DocumentOpenFailure.Failed, error.message)
    }
}

@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val bookContentSync: BookContentSync,
) {
    suspend fun open(bookId: Long): DocumentOpenResult {
        val stored = bookRepository.getBookById(bookId)
            ?: return DocumentOpenResult.Failed(null, DocumentOpenFailure.BookNotFound)
        val sourceLoad = resolveSource(stored)
        if (sourceLoad is DocumentSourceLoad.Failed) {
            return DocumentOpenResult.Failed(stored, sourceLoad.reason, sourceLoad.detail)
        }
        sourceLoad as DocumentSourceLoad.Ready

        var latest = bookRepository.getBookById(bookId) ?: stored
        val text = MarkdownPreprocessor.stripLocalRelativeImages(sourceLoad.extracted.body)
        val detectedFormat = when {
            PdfReaderContent.looksLikePdfBody(text) -> ImportedBookFormat.PDF
            else -> ImportedBookFormat.fromStored(latest.importFormat)
        }
        val aligned = BookTocEnricher.alignToBody(
            detectedFormat,
            sourceLoad.extracted.copy(body = text),
        )

        val canonical = ParsedBookStorage.bundleDir(context, bookId)
        if (sourceLoad.bundleDir?.absolutePath == canonical.absolutePath) {
            latest = persistCanonicalPathsIfNeeded(latest, canonical)
        }
        if (text.length != latest.totalChars || detectedFormat.storedKey != latest.importFormat) {
            latest = latest.copy(
                totalChars = text.length,
                importFormat = detectedFormat.storedKey,
            )
            bookRepository.updateBook(latest)
        }

        val documentFormat = when (detectedFormat) {
            ImportedBookFormat.MARKDOWN -> DocumentFormat.Markdown
            ImportedBookFormat.TXT -> DocumentFormat.PlainText
            ImportedBookFormat.PDF -> DocumentFormat.Pdf
        }
        val blocks = DocumentBlockParser.parse(text, documentFormat)
        val chunkIndex = DocumentBlockParser.buildChunkIndex(text.length, blocks)
        val artifacts = sourceLoad.bundleDir
            ?.let(DocumentArtifactManifest::read)
            ?.artifacts
            .orEmpty()
        val snapshot = DocumentSnapshot(
            document = latest.toDocument().copy(format = documentFormat),
            source = sourceLoad.descriptor,
            content = text,
            toc = aligned.toc,
            blocks = blocks,
            chunkIndex = chunkIndex,
            artifacts = artifacts,
        )
        return DocumentOpenResult.Ready(latest, snapshot)
    }

    private suspend fun resolveSource(book: BookEntity): DocumentSourceLoad {
        val canonical = ParsedBookStorage.bundleDir(context, book.id)
        val candidates = buildList<DocumentSource> {
            add(ParsedBundleDocumentSource(book, canonical))
            book.parsedBundlePath
                ?.takeIf { it.isNotBlank() && File(it).absolutePath != canonical.absolutePath }
                ?.let { add(ParsedBundleDocumentSource(book, File(it))) }
        }
        candidates.forEach { source ->
            val loaded = source.load()
            if (loaded is DocumentSourceLoad.Ready) return loaded
        }

        if (bookContentSync.ensureLocalBookContent(book.id)) {
            ParsedBundleDocumentSource(book, canonical).load().let { loaded ->
                if (loaded is DocumentSourceLoad.Ready) return loaded
            }
        }

        if (ImportedBookFormat.isRemovedStoredKey(book.importFormat)) {
            return DocumentSourceLoad.Ready(
                extracted = ExtractedBookText.plainBody(
                    BookContentLoader.removedFormatPlaceholder(context, book.importFormat),
                ),
                descriptor = DocumentSourceDescriptor(
                    DocumentSourceType.Missing,
                    book.filePath,
                    book.contentHash,
                ),
            )
        }
        if (ImportedBookFormat.fromStored(book.importFormat).isPdf) {
            return DocumentSourceLoad.Failed(DocumentOpenFailure.BrokenBundle)
        }
        val fallback = when {
            book.filePath.startsWith("http://", ignoreCase = true) ||
                book.filePath.startsWith("https://", ignoreCase = true) -> RemoteDocumentSource(context, book)
            book.filePath.startsWith("content://", ignoreCase = true) -> UriDocumentSource(context, book)
            else -> null
        } ?: return DocumentSourceLoad.Failed(DocumentOpenFailure.MissingSource)
        return fallback.load()
    }

    private suspend fun persistCanonicalPathsIfNeeded(book: BookEntity, canonical: File): BookEntity {
        val bodyFile = File(canonical, ParsedBookStorage.BODY_FILE)
        if (!bodyFile.isFile) return book
        val cover = when {
            File(canonical, ParsedBookStorage.COVER_JPG).isFile ->
                File(canonical, ParsedBookStorage.COVER_JPG).absolutePath
            File(canonical, ParsedBookStorage.COVER_PNG).isFile ->
                File(canonical, ParsedBookStorage.COVER_PNG).absolutePath
            else -> book.coverImagePath
        }
        val updated = book.copy(
            parsedBundlePath = canonical.absolutePath,
            filePath = bodyFile.absolutePath,
            coverImagePath = cover,
        )
        if (updated != book) bookRepository.updateBook(updated)
        return updated
    }
}
