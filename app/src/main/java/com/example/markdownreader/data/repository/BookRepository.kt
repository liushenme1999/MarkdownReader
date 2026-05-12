package com.example.markdownreader.data.repository

import com.example.markdownreader.data.local.dao.BookDao
import com.example.markdownreader.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow
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

    suspend fun addBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun deleteBook(book: BookEntity) = bookDao.deleteBook(book)

    suspend fun updateReadingProgress(bookId: Long, progress: Float, position: Int) {
        bookDao.updateReadingProgress(bookId, progress, position, Date().time)
    }

    suspend fun toggleFavorite(bookId: Long, isFavorite: Boolean) {
        bookDao.updateFavoriteStatus(bookId, isFavorite)
    }

    suspend fun getBookCount(): Int = bookDao.getBookCount()

    suspend fun deleteBooksByIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        bookDao.deleteBooksByIds(ids.toList())
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
