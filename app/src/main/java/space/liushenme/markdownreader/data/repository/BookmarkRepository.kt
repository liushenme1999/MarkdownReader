package space.liushenme.markdownreader.data.repository

import space.liushenme.markdownreader.data.local.dao.BookmarkDao
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : IBookmarkRepository {
    override fun getBookmarksByBookId(bookId: Long): Flow<List<BookmarkEntity>> = 
        bookmarkDao.getBookmarksByBookId(bookId)

    override fun getAllBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()

    override suspend fun addBookmark(bookmark: BookmarkEntity): Long = bookmarkDao.insertBookmark(bookmark)

    override suspend fun deleteBookmark(bookmark: BookmarkEntity) = bookmarkDao.deleteBookmark(bookmark)

    override suspend fun deleteBookmarksByBookId(bookId: Long) = bookmarkDao.deleteBookmarksByBookId(bookId)
}
