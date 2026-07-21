package space.liushenme.markdownreader.data.local.dao

import androidx.room.*
import space.liushenme.markdownreader.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query(
        "SELECT * FROM books ORDER BY isPinned DESC, pinOrder DESC, " +
            "lastReadTime IS NULL ASC, lastReadTime DESC, addTime DESC"
    )
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY lastReadTime DESC")
    fun getFavoriteBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

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
}
