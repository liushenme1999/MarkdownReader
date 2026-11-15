package space.liushenme.markdownreader.document

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import space.liushenme.markdownreader.data.repository.IBookRepository
import space.liushenme.markdownreader.importing.BookContentLoader
import space.liushenme.markdownreader.importing.BookTocEnricher
import space.liushenme.markdownreader.importing.CodeFileLanguage
import space.liushenme.markdownreader.importing.ExtractedBookText
import space.liushenme.markdownreader.importing.TxtBodyNormalizer
import space.liushenme.markdownreader.importing.TxtTextWindow
import space.liushenme.markdownreader.importing.TxtSegmentIndex
import space.liushenme.markdownreader.document.DocumentFormat
import java.nio.charset.StandardCharsets
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.ImportedTocEntry
import space.liushenme.markdownreader.importing.HtmlDocumentSupport
import space.liushenme.markdownreader.importing.ParsedBookStorage
import space.liushenme.markdownreader.importing.PlainTextTocParser
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
        val extracted = ParsedBookStorage.readBundle(
            dir = dir,
            importFormat = ImportedBookFormat.fromStored(book.importFormat),
        )
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
    private val bookRepository: IBookRepository,
    private val bookContentSync: BookContentSync,
) {
    /**
     * 打开书籍。
     *
     * 大文件（几 MB 的 TXT/Markdown）的读取、预处理、TOC 对齐与分块解析都很重，
     * 必须在 IO 线程执行：此前整段跑在主线程，会让首帧卡住触发系统 ANR（日志中
     * "Input dispatching timed out" 后被系统杀掉，表现为闪退）。
     * 解析结果按 (bookId, 内容哈希) 缓存，重复打开同一本书时直接复用。
     */
    suspend fun open(bookId: Long): DocumentOpenResult {
        val stored = bookRepository.getBookById(bookId)
            ?: return DocumentOpenResult.Failed(null, DocumentOpenFailure.BookNotFound)
        return try {
            withContext(Dispatchers.IO) { openBlocking(stored) }
        } catch (_: OutOfMemoryError) {
            // 超大文件解压/解析可能 OOM：给出可读错误而不是让进程崩溃。
            DocumentOpenResult.Failed(
                stored,
                DocumentOpenFailure.Failed,
                context.getString(space.liushenme.markdownreader.R.string.reader_error_out_of_memory),
            )
        }
    }

    private suspend fun openBlocking(stored: BookEntity): DocumentOpenResult {
        // 大 TXT 窗口模式：只读阅读位置附近的分段，不加载全文（参考 Legado 的按章随机读）。
        // 必须在缓存之前：缓存里存的是「上一次打开时的那一小扇窗口」，
        // 直接复用会让重新打开停留在旧章节位置（切换章节后重进像是回到开头）。
        openTxtWindowMode(stored)?.let { return it }
        val cached = cachedSnapshot(stored)
        if (cached != null) {
            return DocumentOpenResult.Ready(
                bookRepository.getBookById(stored.id) ?: stored,
                cached,
            )
        }
        val sourceLoad = resolveSource(stored)
        if (sourceLoad is DocumentSourceLoad.Failed) {
            return DocumentOpenResult.Failed(stored, sourceLoad.reason, sourceLoad.detail)
        }
        sourceLoad as DocumentSourceLoad.Ready

        var latest = bookRepository.getBookById(stored.id) ?: stored
        val storedFormat = ImportedBookFormat.fromStored(latest.importFormat)
        // 纯文本没有 Markdown 图片引用，跳过对全文的正则重写（几 MB 文本上开销明显）。
        val text = if (storedFormat.isCode || storedFormat == ImportedBookFormat.TXT) {
            sourceLoad.extracted.body
        } else {
            MarkdownPreprocessor.stripLocalRelativeImages(sourceLoad.extracted.body)
        }
        val detectedFormat = when {
            storedFormat.isCode -> ImportedBookFormat.CODE
            PdfReaderContent.looksLikePdfBody(text) -> ImportedBookFormat.PDF
            HtmlDocumentSupport.looksLikeHtmlBody(text) -> ImportedBookFormat.HTML
            else -> storedFormat
        }
        // 超大纯文本（小说等）：段落块的 substring 化与全文目录扫描会让首帧等十几秒。
        // 这里改走快速路径：不做段落分块（分块索引退化为固定窗口），目录只解析开头一段，
        // 覆盖绝大多数章节标题；两者都只是阅读体验的增强，不影响正文完整显示。
        val hugePlainText = detectedFormat == ImportedBookFormat.TXT &&
            text.length >= HUGE_PLAIN_TEXT_CHARS
        val aligned = when {
            detectedFormat.isCode -> sourceLoad.extracted.copy(body = text, toc = emptyList())
            hugePlainText -> sourceLoad.extracted.copy(
                body = text,
                toc = PlainTextTocParser.parse(
                    text.substring(0, minOf(text.length, HUGE_PLAIN_TEXT_TOC_PREFIX_CHARS)),
                ),
            )
            else -> BookTocEnricher.alignToBody(
                detectedFormat,
                sourceLoad.extracted.copy(body = text),
            )
        }

        val canonical = ParsedBookStorage.bundleDir(context, stored.id)
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
            ImportedBookFormat.HTML -> DocumentFormat.Html
            ImportedBookFormat.CODE -> DocumentFormat.Code
        }
        val codeLanguage = if (detectedFormat.isCode) {
            // 本地导入的代码文件 filePath 是 content:// URI（没有扩展名），回退到书名推断语言。
            (
                CodeFileLanguage.fromFileName(latest.gitRelativePath ?: latest.filePath)
                    ?: CodeFileLanguage.fromFileName(latest.title)
                )?.token
        } else {
            null
        }
        val blocks = if (hugePlainText) {
            emptyList()
        } else {
            DocumentBlockParser.parse(text, documentFormat, codeLanguage)
        }
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
        storeSnapshot(latest, snapshot)
        return DocumentOpenResult.Ready(latest, snapshot)
    }

    /**
     * 大 TXT 窗口模式：用分段索引按阅读位置读取前后几段，避免把十几 MB 正文读进内存。
     * 返回 null 表示不走窗口模式（小文件、无索引、或非 TXT）。
     */
    private suspend fun openTxtWindowMode(stored: BookEntity): DocumentOpenResult? {
        val format = ImportedBookFormat.fromStored(stored.importFormat)
        if (format != ImportedBookFormat.TXT) return null
        val dir = ParsedBookStorage.bundleDir(context, stored.id)
        if (!dir.isDirectory) return null
        val body = java.io.File(dir, ParsedBookStorage.BODY_FILE)
        upgradeTxtBodyIfNeeded(dir, body)
        val segments = ParsedBookStorage.readTxtSegments(dir)
            // 存量书籍没有索引：首次打开时就地补建一次（IO 线程，之后复用）。
            ?: TxtSegmentIndex.build(body)?.also { TxtSegmentIndex.write(dir, it) }
            ?: return null
        val totalChars = TxtSegmentIndex.totalChars(segments)
        if (totalChars <= 0) return null

        val anchor = resolveStoredGlobalChar(stored, totalChars)
        // 首屏只加载小窗口（约数万字）：TextView 布局耗时与字符数近似线性，
        // 80 万字的窗口会让每次进入都卡 3~4 秒。真正到边界时再由阅读页追加。
        val window = ParsedBookStorage.readTxtWindow(dir, anchor, before = 1, after = 6)
            ?: return null
        val toc = resolveTxtToc(dir, body)
        val latest = bookRepository.getBookById(stored.id) ?: stored
        val snapshot = DocumentSnapshot(
            document = latest.toDocument().copy(format = DocumentFormat.PlainText),
            source = DocumentSourceDescriptor(
                type = DocumentSourceType.ParsedBundle,
                location = dir.absolutePath,
                version = latest.contentHash,
            ),
            content = window.text,
            toc = toc,
            blocks = emptyList(),
            chunkIndex = DocumentBlockParser.buildChunkIndex(window.text.length, emptyList()),
            windowStartChar = window.startChar,
            totalCharsGlobal = totalChars,
        )
        storeSnapshot(latest, snapshot)
        return DocumentOpenResult.Ready(latest, snapshot)
    }

    /**
     * 窗口模式的目录：优先用已保存目录；缺失或条目过少时，对全文跑一次标题解析并落盘
     * （只在首次打开发生一次，之后直接读 toc.json）。
     */
    private fun resolveTxtToc(dir: java.io.File, body: java.io.File): List<ImportedTocEntry> {
        // 目录解析算法升级后（旧规则会漏章），旧目录不再可信：按版本标记触发一次性重建。
        if (ParsedBookStorage.readTocVersion(dir) >= ParsedBookStorage.TOC_VERSION) {
            ParsedBookStorage.readToc(dir)?.let { if (it.size >= 2) return it }
        }
        val text = runCatching { body.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return emptyList()
        val parsed = PlainTextTocParser.parse(text)
        if (parsed.isNotEmpty()) {
            ParsedBookStorage.writeToc(dir, parsed)
            ParsedBookStorage.writeTocVersion(dir)
        }
        return parsed
    }

    /** 窗口模式：读取 [fromGlobalChar] 起的下一批文本（供阅读页追加）。 */
    fun readNextTxtWindow(bookId: Long, fromGlobalChar: Int, segments: Int = 6): TxtTextWindow? {
        val dir = ParsedBookStorage.bundleDir(context, bookId)
        if (!dir.isDirectory) return null
        return ParsedBookStorage.readTxtWindow(dir, fromGlobalChar, before = 0, after = segments)
    }

    /** 窗口模式：加载以 [globalChar] 为中心的窗口（目录跳转用）。 */
    fun reloadTxtWindowAt(bookId: Long, globalChar: Int): TxtTextWindow? {
        val dir = ParsedBookStorage.bundleDir(context, bookId)
        if (!dir.isDirectory) return null
        return ParsedBookStorage.readTxtWindow(dir, globalChar, before = 1, after = 6)
    }

    /** 旧正文包一次性规范化：去掉章首重复标题并重建分段索引与目录（只在首次打开发生）。 */
    private fun upgradeTxtBodyIfNeeded(dir: java.io.File, body: java.io.File) {
        if (ParsedBookStorage.readBodyVersion(dir) >= ParsedBookStorage.BODY_VERSION) return
        val raw = runCatching { body.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return
        val normalized = TxtBodyNormalizer.dedupeRepeatedHeadingLines(
            TxtBodyNormalizer.normalizeLineEndings(raw),
        )
        if (normalized != raw) {
            runCatching { body.writeText(normalized, StandardCharsets.UTF_8) }
            // 正文变了：索引、目录都失效，清掉让后续步骤重建
            runCatching { java.io.File(dir, ParsedBookStorage.TOC_FILE).delete() }
            runCatching { java.io.File(dir, ParsedBookStorage.TOC_VERSION_FILE).delete() }
            runCatching { java.io.File(dir, TxtSegmentIndex.FILE_NAME).delete() }
        }
        ParsedBookStorage.writeBodyVersion(dir)
    }

    /** 已保存的全局字符位置（优先 currentPosition；位置与进度明显不一致时以进度为准）。 */
    private fun resolveStoredGlobalChar(book: BookEntity, totalChars: Int): Int =
        resolveReadingOffset(
            position = book.currentPosition,
            progress = book.readingProgress,
            totalChars = totalChars,
        ).coerceIn(0, (totalChars - 1).coerceAtLeast(0))

    /** 已解析快照缓存：key = bookId，value = 内容哈希 + 快照。只保留最近两本，避免占内存。 */
    private val snapshotCache = object : LinkedHashMap<Long, Pair<String, DocumentSnapshot>>(4, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Long, Pair<String, DocumentSnapshot>>?,
        ): Boolean = size > SNAPSHOT_CACHE_ENTRIES
    }

    @Synchronized
    private fun cachedSnapshot(book: BookEntity): DocumentSnapshot? {
        val entry = snapshotCache[book.id] ?: return null
        return if (entry.first == book.contentHash) entry.second else null
    }

    @Synchronized
    private fun storeSnapshot(book: BookEntity, snapshot: DocumentSnapshot) {
        snapshotCache[book.id] = book.contentHash to snapshot
    }

    companion object {
        private const val SNAPSHOT_CACHE_ENTRIES = 2

        /** 超过该字符数的纯文本走快速打开路径（约 2 MB）。 */
        private const val HUGE_PLAIN_TEXT_CHARS = 2_000_000

        /** 超大纯文本仅解析开头这么多字符的目录标题。 */
        private const val HUGE_PLAIN_TEXT_TOC_PREFIX_CHARS = 500_000
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
