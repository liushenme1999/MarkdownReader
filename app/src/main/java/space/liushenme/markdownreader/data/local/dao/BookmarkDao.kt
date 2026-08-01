package space.liushenme.markdownreader.data.local.dao

import androidx.room.*
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createTime DESC")
    fun getBookmarksByBookId(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createTime DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createTime DESC")
    suspend fun getAllBookmarksList(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteBookmarksByBookId(bookId: Long)

    @Query("UPDATE bookmarks SET bookId = :newBookId WHERE bookId = :oldBookId")
    suspend fun reassignBookId(oldBookId: Long, newBookId: Long)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}
