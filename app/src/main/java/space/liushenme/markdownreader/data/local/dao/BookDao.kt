package space.liushenme.markdownreader.data.local.dao

import androidx.room.*
import space.liushenme.markdownreader.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    /**
     * 置顶优先；其余按「最近活动」倒序：
     * 未读用导入时间，读过后用最近阅读时间，因此新导入与刚读过的书都会靠前。
     */
    @Query(
        "SELECT * FROM books ORDER BY isPinned DESC, pinOrder DESC, " +
            "COALESCE(lastReadTime, addTime) DESC, id DESC"
    )
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query(
        "SELECT * FROM books ORDER BY isPinned DESC, pinOrder DESC, " +
            "COALESCE(lastReadTime, addTime) DESC, id DESC"
    )
    suspend fun getAllBooksList(): List<BookEntity>

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY lastReadTime DESC")
    fun getFavoriteBooks(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Query("DELETE FROM books")
    suspend fun deleteAll()

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE filePath = :filePath LIMIT 1")
    suspend fun getBookByFilePath(filePath: String): BookEntity?

    @Query("SELECT * FROM books WHERE contentHash = :contentHash LIMIT 1")
    suspend fun getBookByContentHash(contentHash: String): BookEntity?

    @Query(
        "SELECT * FROM books WHERE gitProjectId = :projectId " +
            "AND gitRelativePath = :relativePath LIMIT 1",
    )
    suspend fun getBookByGitPath(projectId: Long, relativePath: String): BookEntity?

    @Query("SELECT * FROM books WHERE gitProjectId = :projectId")
    suspend fun getBooksByGitProjectId(projectId: Long): List<BookEntity>

    @Query(
        "SELECT * FROM books WHERE gitProjectId IS NULL ORDER BY isPinned DESC, pinOrder DESC, " +
            "COALESCE(lastReadTime, addTime) DESC, id DESC",
    )
    fun getStandaloneBooks(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query(
        "UPDATE books SET readingProgress = :progress, currentPosition = :position, " +
            "progressPreviewText = :previewText, lastReadTime = :time WHERE id = :bookId",
    )
    suspend fun updateReadingProgress(
        bookId: Long,
        progress: Float,
        position: Int,
        previewText: String,
        time: Long,
    )

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :bookId")
    suspend fun updateFavoriteStatus(bookId: Long, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int

    @Query("DELETE FROM books WHERE id IN (:ids)")
    suspend fun deleteBooksByIds(ids: List<Long>)

    @Query("UPDATE books SET isPinned = 1, pinOrder = :pinOrder WHERE id IN (:ids)")
    suspend fun pinBooksByIds(ids: List<Long>, pinOrder: Long)

    @Query("UPDATE books SET isPinned = 0, pinOrder = 0 WHERE id IN (:ids)")
    suspend fun unpinBooksByIds(ids: List<Long>)

    @Query("UPDATE books SET shelfGroup = :groupName WHERE id IN (:ids)")
    suspend fun updateShelfGroupByIds(ids: List<Long>, groupName: String)

    @Query(
        "SELECT DISTINCT shelfGroup FROM books " +
            "WHERE TRIM(shelfGroup) != '' ORDER BY shelfGroup ASC",
    )
    suspend fun getDistinctShelfGroups(): List<String>

    @Query("UPDATE books SET shelfGroup = '' WHERE shelfGroup = :groupName")
    suspend fun clearShelfGroupByName(groupName: String)

    @Query("UPDATE books SET shelfGroup = :newName WHERE shelfGroup = :oldName")
    suspend fun renameShelfGroup(oldName: String, newName: String)
}
