package com.example.markdownreader.data.repository

import com.example.markdownreader.data.local.dao.ReadingProgressDao
import com.example.markdownreader.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepository @Inject constructor(
    private val progressDao: ReadingProgressDao
) {
    fun getProgressByBookId(bookId: Long): Flow<List<ReadingProgressEntity>> = 
        progressDao.getProgressByBookId(bookId)

    fun getProgressByDateRange(startDate: Date, endDate: Date): Flow<List<ReadingProgressEntity>> = 
        progressDao.getProgressByDateRange(startDate, endDate)

    suspend fun getTotalReadCharsLast7Days(): Int {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        return progressDao.getTotalReadCharsSince(calendar.time) ?: 0
    }

    suspend fun getTotalReadTimeLast7Days(): Int {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        return progressDao.getTotalReadTimeSince(calendar.time) ?: 0
    }

    suspend fun recordReading(bookId: Long, charsRead: Int, minutesRead: Int) {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val existing = progressDao.getProgressByBookAndDate(bookId, today)
        if (existing != null) {
            val updated = existing.copy(
                readChars = existing.readChars + charsRead,
                readTimeMinutes = existing.readTimeMinutes + minutesRead
            )
            progressDao.insertProgress(updated)
        } else {
            progressDao.insertProgress(
                ReadingProgressEntity(
                    bookId = bookId,
                    date = today,
                    readChars = charsRead,
                    readTimeMinutes = minutesRead
                )
            )
        }
    }
}
