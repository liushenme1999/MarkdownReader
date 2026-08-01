package space.liushenme.markdownreader.ui.screens.bookshelf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.ShelfGroups
import space.liushenme.markdownreader.data.repository.BookRepository
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.data.repository.ShelfGroupRepository
import space.liushenme.markdownreader.model.BookshelfGridColumns
import space.liushenme.markdownreader.model.BookshelfLayoutMode
import space.liushenme.markdownreader.importing.BookContentLoader
import space.liushenme.markdownreader.importing.BookImportSupport
import space.liushenme.markdownreader.importing.BookTocEnricher
import space.liushenme.markdownreader.importing.ExtractedBookText
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.ParsedBookStorage
import space.liushenme.markdownreader.importing.UrlBookDownloader
import space.liushenme.markdownreader.markdown.DiagramImageLoader
import space.liushenme.markdownreader.markdown.MarkdownPreprocessor
import space.liushenme.markdownreader.markdown.NetworkImageCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val shelfGroupRepository: ShelfGroupRepository,
    private val readerSettingsRepository: ReaderSettingsRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val books = bookRepository.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shelfGroups = shelfGroupRepository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val layoutMode = readerSettingsRepository.bookshelfLayoutMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfLayoutMode.Grid)

    val gridColumns = readerSettingsRepository.bookshelfGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfGridColumns.DEFAULT)

    private val toastChannel = Channel<String>(Channel.BUFFERED)
    val toastMessages = toastChannel.receiveAsFlow()

    private val readerOpenRequestChannel = Channel<Long>(Channel.BUFFERED)
    val readerOpenRequests = readerOpenRequestChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            shelfGroupRepository.syncFromBooks()
        }
    }

    fun importFromLocalUris(
        context: Context,
        uris: List<Uri>,
        target: BookImportTarget = BookImportTarget.None,
    ) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            importFromLocalUri(context, uris[0], target = target)
            return
        }
        viewModelScope.launch {
            var successCount = 0
            var duplicateCount = 0
            var failCount = 0
            for (uri in uris) {
                when (
                    importLocalUriInternal(
                        context = context,
                        uri = uri,
                        notify = false,
                        target = target,
                    )
                ) {
                    is LocalImportOutcome.Success -> successCount++
                    is LocalImportOutcome.Duplicate -> duplicateCount++
                    is LocalImportOutcome.Failed -> failCount++
                }
            }
            val skippedCount = duplicateCount + failCount
            toastChannel.trySend(
                when {
                    successCount > 0 && skippedCount == 0 ->
                        appContext.getString(R.string.toast_import_batch_success, successCount)
                    successCount > 0 ->
                        appContext.getString(
                            R.string.toast_import_batch_partial,
                            successCount,
                            skippedCount,
                        )
                    duplicateCount > 0 && failCount == 0 ->
                        appContext.getString(R.string.toast_import_already_exists)
                    else -> appContext.getString(R.string.toast_import_batch_failed)
                }
            )
        }
    }

    fun importFromLocalUri(
        context: Context,
        uri: Uri,
        openReaderWhenDone: Boolean = false,
        target: BookImportTarget = BookImportTarget.None,
    ) {
        viewModelScope.launch {
            val outcome = importLocalUriInternal(
                context = context,
                uri = uri,
                notify = true,
                target = target,
            )
            if (openReaderWhenDone &&
                outcome is LocalImportOutcome.Success &&
                outcome.bookId > 0L
            ) {
                readerOpenRequestChannel.trySend(outcome.bookId)
            }
        }
    }

    private suspend fun importLocalUriInternal(
        context: Context,
        uri: Uri,
        notify: Boolean,
        target: BookImportTarget,
    ): LocalImportOutcome {
        return try {
            val filePath = uri.toString()
            if (bookRepository.getBookByFilePath(filePath) != null) {
                if (notify) {
                    toastChannel.trySend(
                        appContext.getString(R.string.toast_import_already_exists)
                    )
                }
                return LocalImportOutcome.Duplicate
            }
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // 部分来源不支持持久权限，仍尝试当前会话内读取
            }
            val fileName = BookImportSupport.displayNameFromUri(context, uri)
                ?: appContext.getString(R.string.book_untitled)
            val mime = context.contentResolver.getType(uri)
            if (BookImportSupport.isRemovedFormat(fileName, mime)) {
                if (notify) {
                    toastChannel.trySend(
                        appContext.getString(R.string.toast_import_unsupported_format)
                    )
                }
                return LocalImportOutcome.Failed
            }
            val format = BookImportSupport.detectFormat(fileName, mime)
            val extracted = withContext(Dispatchers.IO) {
                BookContentLoader.loadExtractedFromUri(context, uri, format)
            }
            if (notify && extracted.body.isBlank() && format.hasBuiltInTextExtract) {
                toastChannel.trySend(appContext.getString(R.string.toast_import_parse_failed))
            }
            val bookId = persistImportedBook(
                importContext = context,
                title = BookImportSupport.stripKnownExtension(fileName),
                extracted = extracted,
                filePath = filePath,
                format = format,
                notify = notify,
                target = target,
            )
            if (bookId > 0L) {
                LocalImportOutcome.Success(bookId)
            } else {
                LocalImportOutcome.Failed
            }
        } catch (e: Exception) {
            if (notify) {
                toastChannel.trySend(
                    appContext.getString(
                        R.string.toast_import_failed,
                        e.message ?: appContext.getString(R.string.error_unknown),
                    )
                )
            }
            LocalImportOutcome.Failed
        }
    }

    fun importFromUrl(
        urlRaw: String,
        target: BookImportTarget = BookImportTarget.None,
    ) {
        val url = BookImportSupport.normalizeImportUrl(urlRaw)
        if (url == null) {
            toastChannel.trySend(
                if (urlRaw.trim().isEmpty()) {
                    appContext.getString(R.string.toast_import_url_empty)
                } else {
                    appContext.getString(R.string.toast_import_url_invalid_scheme)
                }
            )
            return
        }
        viewModelScope.launch {
            try {
                if (bookRepository.getBookByFilePath(url) != null) {
                    toastChannel.trySend(
                        appContext.getString(R.string.toast_import_already_exists)
                    )
                    return@launch
                }
                val result = withContext(Dispatchers.IO) { UrlBookDownloader.download(url) }
                val name = result.suggestedFileName ?: url.substringAfterLast('/').substringBefore('?')
                if (BookImportSupport.isRemovedFormat(name, result.contentType)) {
                    toastChannel.trySend(
                        appContext.getString(R.string.toast_import_unsupported_format)
                    )
                    return@launch
                }
                val format = BookImportSupport.detectFormat(name, result.contentType)
                val extracted = withContext(Dispatchers.IO) {
                    BookContentLoader.loadExtractedFromUrlBytes(
                        appContext,
                        result.bytes,
                        format,
                        result.charsetFromHeader
                    )
                }
                if (extracted.body.isBlank() && format.hasBuiltInTextExtract) {
                    toastChannel.trySend(appContext.getString(R.string.toast_import_download_empty))
                }
                persistImportedBook(
                    importContext = appContext,
                    title = BookImportSupport.stripKnownExtension(
                        name.ifBlank { appContext.getString(R.string.book_from_network) }
                    ),
                    extracted = extracted,
                    filePath = url,
                    format = format,
                    target = target,
                )
            } catch (e: Exception) {
                toastChannel.trySend(
                    appContext.getString(
                        R.string.toast_import_from_url_failed,
                        e.message ?: appContext.getString(R.string.error_network),
                    )
                )
            }
        }
    }

    private suspend fun persistImportedBook(
        importContext: Context,
        title: String,
        extracted: ExtractedBookText,
        filePath: String,
        format: ImportedBookFormat,
        notify: Boolean = true,
        target: BookImportTarget = BookImportTarget.None,
    ): Long {
        val shelfGroup = target.shelfGroup.trim()
        if (shelfGroup.isNotEmpty()) {
            shelfGroupRepository.ensureGroup(shelfGroup)
        }
        val enriched = BookTocEnricher.enrichIfEmpty(format, extracted)
        val enrichedForStore = prepareImportedContent(importContext, format, enriched)
        val content = enrichedForStore.body
        val author = BookImportSupport.extractAuthorFromContent(content)
        val book = BookEntity(
            title = title.ifBlank { appContext.getString(R.string.book_untitled) },
            author = author,
            filePath = filePath,
            importFormat = format.storedKey,
            coverColor = Random.nextInt(BOOK_COVER_COLOR_COUNT),
            totalChars = content.length,
            addTime = Date(),
            shelfGroup = shelfGroup,
            isFavorite = target.isFavorite,
        )
        val id = bookRepository.addBook(book)
        if (id <= 0L) {
            if (notify) {
                toastChannel.trySend(appContext.getString(R.string.toast_import_write_failed))
            }
            return 0L
        }
        val writeOk = withContext(Dispatchers.IO) {
            val dir = ParsedBookStorage.bundleDir(appContext, id)
            val ok = ParsedBookStorage.writeBundle(
                dir = dir,
                extracted = enrichedForStore,
                coverBytes = enriched.coverImageBytes,
            )
            if (!ok) {
                ParsedBookStorage.deleteBundleDir(dir.absolutePath)
                return@withContext false
            }
            val coverFile = when {
                File(dir, ParsedBookStorage.COVER_JPG).isFile ->
                    File(dir, ParsedBookStorage.COVER_JPG)
                File(dir, ParsedBookStorage.COVER_PNG).isFile ->
                    File(dir, ParsedBookStorage.COVER_PNG)
                else -> null
            }
            bookRepository.updateBook(
                book.copy(
                    id = id,
                    totalChars = content.length,
                    parsedBundlePath = dir.absolutePath,
                    coverImagePath = coverFile?.absolutePath
                )
            )
            true
        }
        if (notify) {
            toastChannel.trySend(
                if (writeOk) {
                    appContext.getString(R.string.toast_import_success, book.title)
                } else {
                    appContext.getString(R.string.toast_import_cache_write_failed, book.title)
                }
            )
        }
        return id
    }

    private suspend fun prepareImportedContent(
        importContext: Context,
        format: ImportedBookFormat,
        extracted: ExtractedBookText,
    ): ExtractedBookText {
        if (format.usesReaderPlainBody) {
            return extracted.copy(body = MarkdownPreprocessor.stripLocalRelativeImages(extracted.body))
        }
        var body = MarkdownPreprocessor.prepare(extracted.body, appContext)
        withContext(Dispatchers.IO) {
            NetworkImageCache.preloadFromMarkdown(appContext, body)
        }
        body = NetworkImageCache.rewriteCachedUrls(appContext, body)
        DiagramImageLoader.preloadFromMarkdown(importContext, body)
        return extracted.copy(body = body)
    }

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch {
            bookRepository.toggleFavorite(book.id, !book.isFavorite)
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            bookRepository.deleteBook(book)
        }
    }

    fun deleteBooks(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            bookRepository.deleteBooksByIds(ids)
        }
    }

    fun togglePinForSelection(ids: Set<Long>) {
        viewModelScope.launch {
            if (ids.isEmpty()) return@launch
            val idList = ids.toList()
            val loaded = idList.mapNotNull { bookRepository.getBookById(it) }
            if (loaded.isEmpty()) return@launch
            val allPinned = loaded.all { it.isPinned }
            if (allPinned) {
                bookRepository.unpinBooksByIds(idList)
            } else {
                bookRepository.pinBooksByIds(idList, System.currentTimeMillis())
            }
        }
    }

    fun moveSelectedToGroup(ids: Set<Long>, groupName: String) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val name = groupName.trim()
            if (name.isNotEmpty()) {
                shelfGroupRepository.ensureGroup(name)
            }
            bookRepository.updateShelfGroupByIds(ids, name)
        }
    }

    private sealed class LocalImportOutcome {
        data class Success(val bookId: Long) : LocalImportOutcome()
        data object Duplicate : LocalImportOutcome()
        data object Failed : LocalImportOutcome()
    }

    companion object {
        /** 封面颜色数量，与 [space.liushenme.markdownreader.ui.theme.BookCoverColors] 保持同步 */
        const val BOOK_COVER_COLOR_COUNT = 16
    }
}

/** 导入时的归属：当前书架标签对应的分组 / 收藏。 */
data class BookImportTarget(
    val shelfGroup: String = "",
    val isFavorite: Boolean = false,
) {
    companion object {
        val None = BookImportTarget()

        fun fromSelectedGroup(selectedGroup: String?): BookImportTarget = when (selectedGroup) {
            null -> None
            ShelfGroups.FAVORITES_SENTINEL -> BookImportTarget(isFavorite = true)
            else -> BookImportTarget(shelfGroup = selectedGroup)
        }
    }
}
