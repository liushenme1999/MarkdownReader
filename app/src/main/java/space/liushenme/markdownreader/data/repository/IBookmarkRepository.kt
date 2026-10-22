package space.liushenme.markdownreader.data.repository

import kotlinx.coroutines.flow.Flow
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity

interface IBookmarkRepository {
    fun getBookmarksByBookId(bookId: Long): Flow<List<BookmarkEntity>>
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>
    suspend fun addBookmark(bookmark: BookmarkEntity): Long
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
    suspend fun deleteBookmarksByBookId(bookId: Long)
}
