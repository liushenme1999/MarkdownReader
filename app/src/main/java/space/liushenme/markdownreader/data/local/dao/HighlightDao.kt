package space.liushenme.markdownreader.data.local.dao

import androidx.room.*
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createTime DESC")
    fun getHighlightsByBookId(bookId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights ORDER BY createTime DESC")
    fun getAllHighlights(): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights ORDER BY createTime DESC")
    suspend fun getAllHighlightsList(): List<HighlightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlights(highlights: List<HighlightEntity>)

    @Update
    suspend fun updateHighlight(highlight: HighlightEntity)

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE bookId = :bookId")
    suspend fun deleteHighlightsByBookId(bookId: Long)

    @Query("UPDATE highlights SET bookId = :newBookId WHERE bookId = :oldBookId")
    suspend fun reassignBookId(oldBookId: Long, newBookId: Long)

    @Query("DELETE FROM highlights")
    suspend fun deleteAll()
}
