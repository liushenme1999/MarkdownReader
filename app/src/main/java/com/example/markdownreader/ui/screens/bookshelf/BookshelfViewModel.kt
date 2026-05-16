package com.example.markdownreader.ui.screens.bookshelf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.data.repository.BookRepository
import com.example.markdownreader.importing.BookContentLoader
import com.example.markdownreader.importing.BookImportSupport
import com.example.markdownreader.importing.BookTocEnricher
import com.example.markdownreader.importing.ExtractedBookText
import com.example.markdownreader.importing.ImportedBookFormat
import com.example.markdownreader.importing.ParsedBookStorage
import com.example.markdownreader.importing.UrlBookDownloader
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
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val books = bookRepository.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val toastChannel = Channel<String>(Channel.BUFFERED)
    val toastMessages = toastChannel.receiveAsFlow()

    fun importFromLocalUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // 部分来源不支持持久权限，仍尝试当前会话内读取
                }
                val fileName = BookImportSupport.displayNameFromUri(context, uri) ?: "未命名书籍"
                val mime = context.contentResolver.getType(uri)
                val format = BookImportSupport.detectFormat(fileName, mime)
                val extracted = withContext(Dispatchers.IO) {
                    BookContentLoader.loadExtractedFromUri(context, uri, format)
                }
                if (extracted.body.isBlank() && format.hasBuiltInTextExtract) {
                    toastChannel.trySend("未能解析出正文，请确认文件未损坏。")
                }
                persistImportedBook(
                    title = BookImportSupport.stripKnownExtension(fileName),
                    extracted = extracted,
                    filePath = uri.toString(),
                    format = format
                )
            } catch (e: Exception) {
                toastChannel.trySend("导入失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun importFromUrl(urlRaw: String) {
        val url = BookImportSupport.normalizeImportUrl(urlRaw)
        if (url == null) {
            toastChannel.trySend(
                if (urlRaw.trim().isEmpty()) "请输入有效的网址。"
                else "仅支持 http 或 https 链接。"
            )
            return
        }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { UrlBookDownloader.download(url) }
                val name = result.suggestedFileName ?: url.substringAfterLast('/').substringBefore('?')
                val format = BookImportSupport.detectFormat(name, result.contentType)
                val extracted = withContext(Dispatchers.IO) {
                    BookContentLoader.loadExtractedFromUrlBytes(
                        result.bytes,
                        format,
                        result.charsetFromHeader
                    )
                }
                if (extracted.body.isBlank() && format.hasBuiltInTextExtract) {
                    toastChannel.trySend("下载成功但未解析出正文。")
                }
                persistImportedBook(
                    title = BookImportSupport.stripKnownExtension(name.ifBlank { "网络书籍" }),
                    extracted = extracted,
                    filePath = url,
                    format = format
                )
            } catch (e: Exception) {
                toastChannel.trySend("从网址导入失败：${e.message ?: "网络错误"}")
            }
        }
    }

    private suspend fun persistImportedBook(
        title: String,
        extracted: ExtractedBookText,
        filePath: String,
        format: ImportedBookFormat
    ) {
        val enriched = BookTocEnricher.enrichIfEmpty(format, extracted)
        val content = enriched.body
        val author = BookImportSupport.extractAuthorFromContent(content)
        val book = BookEntity(
            title = title.ifBlank { "未命名书籍" },
            author = author,
            filePath = filePath,
            importFormat = format.storedKey,
            coverColor = Random.nextInt(BOOK_COVER_COLOR_COUNT),
            totalChars = content.length,
            addTime = Date()
        )
        val id = bookRepository.addBook(book)
        if (id <= 0L) {
            toastChannel.trySend("导入失败：无法写入书架。")
            return
        }
        val writeOk = withContext(Dispatchers.IO) {
            val dir = ParsedBookStorage.bundleDir(appContext, id)
            val ok = ParsedBookStorage.writeBundle(dir, enriched, enriched.coverImageBytes)
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
        toastChannel.trySend(
            if (writeOk) "已导入「${book.title}」"
            else "「${book.title}」已加入书架，但解析缓存写入失败；阅读时将尝试从原文件重新解析。"
        )
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
            bookRepository.updateShelfGroupByIds(ids, name)
        }
    }

    companion object {
        /** 封面颜色数量，与 [com.example.markdownreader.ui.theme.BookCoverColors] 保持同步 */
        const val BOOK_COVER_COLOR_COUNT = 16
    }
}
