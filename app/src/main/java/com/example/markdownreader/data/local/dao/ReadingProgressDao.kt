package com.example.markdownreader.data.local.dao

import androidx.room.*
import com.example.markdownreader.data.local.entity.ReadingProgressEntity
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId AND date = :date")
    suspend fun getProgressByBookAndDate(bookId: Long, date: Date): ReadingProgressEntity?
}
