package space.liushenme.markdownreader.data.repository

import android.content.Context
import space.liushenme.markdownreader.data.local.dao.ReadingProgressDao
import space.liushenme.markdownreader.data.local.entity.ReadingProgressEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepository @Inject constructor(
    private val progressDao: ReadingProgressDao,
    @ApplicationContext private val context: Context,
) : IReadingProgressRepository {
    override fun getProgressByBookId(bookId: Long): Flow<List<ReadingProgressEntity>> =
        progressDao.getProgressByBookId(bookId)

    override fun getProgressByDateRange(startDate: Date, endDate: Date): Flow<List<ReadingProgressEntity>> =
        progressDao.getProgressByDateRange(startDate, endDate)

    override suspend fun getTotalReadCharsLast7Days(): Int {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        return progressDao.getTotalReadCharsSince(calendar.time) ?: 0
    }

    override suspend fun getTotalReadTimeLast7Days(): Int {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        return progressDao.getTotalReadTimeSince(calendar.time) ?: 0
    }

    override fun observeTotalReadTimeMinutes(): Flow<Int> = progressDao.observeTotalReadTimeMinutes()

    override fun observeTotalReadChars(): Flow<Int> = progressDao.observeTotalReadChars()

    override fun observeTodayReadMinutes(): Flow<Int> =
        progressDao.observeTodayReadMinutes(ReadingSessionStats.startOfToday())

    override fun observeTotalReadingDays(): Flow<Int> =
        progressDao.observeActiveReadingDates().map { it.size }

    override fun observeContinuousReadingDays(): Flow<Int> =
        progressDao.observeActiveReadingDates().map { dates ->
            ReadingSessionStats.continuousReadingDays(
                activeDayStartMillis = dates.map { ReadingTrendAggregator.startOfDayMillis(it) },
                todayStartMillis = ReadingSessionStats.startOfToday().time,
            )
        }

    /** 本自然周（周一至周日）每日阅读趋势。 */
    override fun observeLast7DaysTrend(): Flow<List<DailyReadingTrendDay>> {
        val (startDate, endDate) = ReadingTrendAggregator.last7DaysRange()
        return progressDao.getProgressByDateRange(startDate, endDate).map { records ->
            ReadingTrendAggregator.buildLast7DaysTrend(records, context)
        }
    }

    override suspend fun recordReading(bookId: Long, charsRead: Int, minutesRead: Int) {
        val chars = charsRead.coerceAtLeast(0)
        val minutes = minutesRead.coerceAtLeast(0)
        if (chars == 0 && minutes == 0) return

        val today = ReadingSessionStats.startOfToday()
        progressDao.upsertAddProgress(bookId, today, chars, minutes)
    }
}
