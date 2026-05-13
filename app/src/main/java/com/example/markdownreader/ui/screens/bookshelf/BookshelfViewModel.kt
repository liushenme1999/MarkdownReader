package com.example.markdownreader.ui.screens.bookshelf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.data.repository.BookRepository
import com.example.markdownreader.importing.BookContentLoader
import com.example.markdownreader.importing.ImportedBookFormat
import com.example.markdownreader.importing.UrlBookDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookRepository: BookRepository
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
                val fileName = getFileName(context, uri) ?: "未命名书籍"
                val mime = context.contentResolver.getType(uri)
                val format = detectFormat(fileName, mime)
                val content = withContext(Dispatchers.IO) {
                    BookContentLoader.loadFromUri(context, uri, format)
                }
                if (content.isBlank() && format.hasBuiltInTextExtract) {
                    toastChannel.trySend("未能解析出正文，请确认文件未损坏。")
                }
                persistImportedBook(
                    title = stripKnownExtension(fileName),
                    content = content,
                    filePath = uri.toString(),
                    format = format
                )
            } catch (e: Exception) {
                e.printStackTrace()
                toastChannel.trySend("导入失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun importFromUrl(urlRaw: String) {
        val trimmed = urlRaw.trim()
        if (trimmed.isEmpty()) {
            toastChannel.trySend("请输入有效的网址。")
            return
        }
        val url = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { UrlBookDownloader.download(url) }
                val name = result.suggestedFileName ?: url.substringAfterLast('/').substringBefore('?')
                val format = detectFormat(name, result.contentType)
                val content = withContext(Dispatchers.IO) {
                    BookContentLoader.loadFromUrlBytes(
                        result.bytes,
                        format,
                        result.charsetFromHeader
                    )
                }
                if (content.isBlank() && format.hasBuiltInTextExtract) {
                    toastChannel.trySend("下载成功但未解析出正文。")
                }
                persistImportedBook(
                    title = stripKnownExtension(name.ifBlank { "网络书籍" }),
                    content = content,
                    filePath = url,
                    format = format
                )
            } catch (e: Exception) {
                e.printStackTrace()
                toastChannel.trySend("从网址导入失败：${e.message ?: "网络错误"}")
            }
        }
    }

    private suspend fun persistImportedBook(
        title: String,
        content: String,
        filePath: String,
        format: ImportedBookFormat
    ) {
        val author = extractAuthor(content)
        val book = BookEntity(
            title = title.ifBlank { "未命名书籍" },
            author = author,
            filePath = filePath,
            importFormat = format.storedKey,
            coverColor = Random.nextInt(BookCoverColors.size),
            totalChars = content.length,
            addTime = Date()
        )
        bookRepository.addBook(book)
        toastChannel.trySend("已导入「${book.title}」")
    }

    private fun detectFormat(fileName: String?, mime: String?): ImportedBookFormat {
        val ext = fileName?.substringAfterLast('.', "")?.trim()?.lowercase().orEmpty()
        val knownExt = ext in setOf(
            "md", "markdown", "mdown", "mkd",
            "txt", "text", "log",
            "epub", "docx", "pdf", "doc", "mobi", "prc", "azw3", "azw"
        )
        return if (knownExt) ImportedBookFormat.fromFileName(fileName)
        else ImportedBookFormat.fromMimeType(mime)
    }

    private fun stripKnownExtension(fileName: String): String {
        var n = fileName.trim()
        val suffixes = listOf(
            ".markdown", ".mdown", ".mkd", ".md",
            ".txt", ".text", ".log",
            ".epub", ".docx", ".pdf", ".doc", ".mobi", ".prc", ".azw3", ".azw"
        )
        for (s in suffixes) {
            if (n.endsWith(s, ignoreCase = true)) {
                n = n.dropLast(s.length)
                break
            }
        }
        return n.ifBlank { fileName }
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

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    private fun extractAuthor(content: String): String? {
        val authorRegex = Regex("^[Aa]uthor:\\s*(.+)$", RegexOption.MULTILINE)
        return authorRegex.find(content)?.groupValues?.get(1)?.trim()
    }

    companion object {
        val BookCoverColors = listOf(
            android.graphics.Color.parseColor("#E57373"),
            android.graphics.Color.parseColor("#F06292"),
            android.graphics.Color.parseColor("#BA68C8"),
            android.graphics.Color.parseColor("#9575CD"),
            android.graphics.Color.parseColor("#7986CB"),
            android.graphics.Color.parseColor("#64B5F6"),
            android.graphics.Color.parseColor("#4FC3F7"),
            android.graphics.Color.parseColor("#4DD0E1"),
            android.graphics.Color.parseColor("#4DB6AC"),
            android.graphics.Color.parseColor("#81C784"),
            android.graphics.Color.parseColor("#AED581"),
            android.graphics.Color.parseColor("#FF8A65"),
            android.graphics.Color.parseColor("#FFB74D"),
            android.graphics.Color.parseColor("#90A4AE"),
            android.graphics.Color.parseColor("#B0BEC5")
        )
    }
}
