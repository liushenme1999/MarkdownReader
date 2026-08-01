package space.liushenme.markdownreader.data.local.dao

import androidx.room.*
import space.liushenme.markdownreader.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId ORDER BY date DESC")
    fun getProgressByBookId(bookId: Long): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getProgressByDateRange(startDate: Date, endDate: Date): Flow<List<ReadingProgressEntity>>

    @Query("SELECT SUM(readChars) FROM reading_progress WHERE date >= :startDate")
    suspend fun getTotalReadCharsSince(startDate: Date): Int?

    @Query("SELECT SUM(readTimeMinutes) FROM reading_progress WHERE date >= :startDate")
    suspend fun getTotalReadTimeSince(startDate: Date): Int?

    @Query("SELECT COALESCE(SUM(readTimeMinutes), 0) FROM reading_progress")
    fun observeTotalReadTimeMinutes(): Flow<Int>

    @Query("SELECT COALESCE(SUM(readChars), 0) FROM reading_progress")
    fun observeTotalReadChars(): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(readTimeMinutes), 0) FROM reading_progress
        WHERE date = :today
        """,
    )
    fun observeTodayReadMinutes(today: Date): Flow<Int>

    @Query(
        """
        SELECT DISTINCT date FROM reading_progress
        WHERE readTimeMinutes > 0 OR readChars > 0
        ORDER BY date DESC
        """,
    )
    fun observeActiveReadingDates(): Flow<List<Date>>

    @Query("SELECT * FROM reading_progress ORDER BY date DESC")
    suspend fun getAllList(): List<ReadingProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ReadingProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(progressList: List<ReadingProgressEntity>)

    @Query("DELETE FROM reading_progress")
    suspend fun deleteAll()

    /**
     * 按 (bookId, date) 累加；依赖表上 UNIQUE(bookId, date)。
     * 新行时 id 由自增生成；冲突时只累加字数与分钟。
     */
    @Query(
        """
        INSERT INTO reading_progress (bookId, date, readChars, readTimeMinutes)
        VALUES (:bookId, :date, :chars, :minutes)
        ON CONFLICT(bookId, date) DO UPDATE SET
          readChars = readChars + :chars,
          readTimeMinutes = readTimeMinutes + :minutes
        """,
    )
    suspend fun upsertAddProgress(bookId: Long, date: Date, chars: Int, minutes: Int)

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId AND date = :date")
    suspend fun getProgressByBookAndDate(bookId: Long, date: Date): ReadingProgressEntity?

    /**
     * 将 oldBookId 的日统计合并到 newBookId，再删除旧行。
     * 同日冲突时累加字数与分钟。
     */
    @Transaction
    suspend fun mergeReassignBookId(oldBookId: Long, newBookId: Long) {
        if (oldBookId == newBookId) return
        val rows = getProgressListByBookId(oldBookId)
        for (row in rows) {
            upsertAddProgress(newBookId, row.date, row.readChars, row.readTimeMinutes)
        }
        deleteByBookId(oldBookId)
    }

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getProgressListByBookId(bookId: Long): List<ReadingProgressEntity>

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}
