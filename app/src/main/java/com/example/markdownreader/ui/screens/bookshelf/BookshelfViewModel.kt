package com.example.markdownreader.ui.screens.bookshelf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Date
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    val books = bookRepository.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun importMarkdownFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val content = readFileContent(context, uri)
                val fileName = getFileName(context, uri) ?: "未命名书籍"
                val title = fileName.removeSuffix(".md").removeSuffix(".markdown")

                // 简单提取作者（假设文件中有作者信息）
                val author = extractAuthor(content)

                val book = BookEntity(
                    title = title,
                    author = author,
                    filePath = uri.toString(),
                    coverColor = Random.nextInt(BookCoverColors.size),
                    totalChars = content.length,
                    addTime = Date()
                )

                bookRepository.addBook(book)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

    private fun readFileContent(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        } ?: ""
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
        // 尝试从 YAML front matter 或内容中提取作者
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
