package space.liushenme.markdownreader.data.repository

import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.importing.ParsedBookStorage
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao
) {
    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    fun getFavoriteBooks(): Flow<List<BookEntity>> = bookDao.getFavoriteBooks()

    suspend fun getBookById(id: Long): BookEntity? = bookDao.getBookById(id)

    suspend fun getBookByFilePath(filePath: String): BookEntity? =
        bookDao.getBookByFilePath(filePath)

    suspend fun addBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun deleteBook(book: BookEntity) {
        deleteStoredAssets(book)
        bookDao.deleteBook(book)
    }

    suspend fun updateReadingProgress(
        bookId: Long,
        progress: Float,
        position: Int,
        previewText: String = "",
    ) {
        bookDao.updateReadingProgress(bookId, progress, position, previewText, Date().time)
    }

    suspend fun toggleFavorite(bookId: Long, isFavorite: Boolean) {
        bookDao.updateFavoriteStatus(bookId, isFavorite)
    }

    suspend fun getBookCount(): Int = bookDao.getBookCount()

    suspend fun deleteBooksByIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        for (id in ids) {
            bookDao.getBookById(id)?.let { deleteStoredAssets(it) }
        }
        bookDao.deleteBooksByIds(ids.toList())
    }

    private fun deleteStoredAssets(book: BookEntity) {
        ParsedBookStorage.deleteBundleDir(book.parsedBundlePath)
        book.coverImagePath?.let { path ->
            runCatching { File(path).delete() }
        }
    }

    suspend fun pinBooksByIds(ids: Collection<Long>, pinOrder: Long) {
        if (ids.isEmpty()) return
        bookDao.pinBooksByIds(ids.toList(), pinOrder)
    }

    suspend fun unpinBooksByIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        bookDao.unpinBooksByIds(ids.toList())
    }

    suspend fun updateShelfGroupByIds(ids: Collection<Long>, groupName: String) {
        if (ids.isEmpty()) return
        bookDao.updateShelfGroupByIds(ids.toList(), groupName)
    }
}
