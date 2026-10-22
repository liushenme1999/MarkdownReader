package space.liushenme.markdownreader.data.repository

import java.util.Date
import kotlinx.coroutines.flow.Flow
import space.liushenme.markdownreader.data.local.entity.ReadingProgressEntity
interface IReadingProgressRepository {
    fun getProgressByBookId(bookId: Long): Flow<List<ReadingProgressEntity>>
    fun getProgressByDateRange(startDate: Date, endDate: Date): Flow<List<ReadingProgressEntity>>
    suspend fun getTotalReadCharsLast7Days(): Int
    suspend fun getTotalReadTimeLast7Days(): Int
    fun observeTotalReadTimeMinutes(): Flow<Int>
    fun observeTotalReadChars(): Flow<Int>
    fun observeTodayReadMinutes(): Flow<Int>
    fun observeTotalReadingDays(): Flow<Int>
    fun observeContinuousReadingDays(): Flow<Int>
    fun observeLast7DaysTrend(): Flow<List<DailyReadingTrendDay>>
    suspend fun recordReading(bookId: Long, charsRead: Int, minutesRead: Int)
}
