package space.liushenme.markdownreader.data.repository

import android.content.Context
import space.liushenme.markdownreader.data.local.entity.ReadingProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReadingTrendAggregatorTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun buildLast7DaysTrend_aggregatesMinutesPerDay_mondayFirst() {
        // 2026-05-16 为周六
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 16, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = ReadingTrendAggregator.startOfDay(now).time
        val friday = (ReadingTrendAggregator.startOfDay(now).clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.time

        val records = listOf(
            ReadingProgressEntity(bookId = 1L, date = today, readTimeMinutes = 30, readChars = 0),
            ReadingProgressEntity(bookId = 2L, date = today, readTimeMinutes = 10, readChars = 0),
            ReadingProgressEntity(bookId = 1L, date = friday, readTimeMinutes = 5, readChars = 0),
        )

        val trend = ReadingTrendAggregator.buildLast7DaysTrend(records, context, now)

        assertEquals(7, trend.size)
        assertEquals(expectedWeekdayLabels(), trend.map { it.weekdayLabel })
        assertEquals(40, trend[5].minutes)
        assertEquals(5, trend[4].minutes)
        assertEquals(0, trend[0].minutes)
        assertEquals(0, trend[6].minutes)
    }

    @Test
    fun buildLast7DaysTrend_estimatesMinutesFromCharsWhenNoTimeRecorded() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 16, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = ReadingTrendAggregator.startOfDay(now).time
        val records = listOf(
            ReadingProgressEntity(
                bookId = 1L,
                date = today,
                readTimeMinutes = 0,
                readChars = ReadingTrendAggregator.CHARS_PER_HOUR,
            ),
        )

        val trend = ReadingTrendAggregator.buildLast7DaysTrend(records, context, now)

        assertEquals(60, trend[ReadingTrendAggregator.dayIndexInCurrentWeek(now)].minutes)
    }

    @Test
    fun shortWeekdayLabels_areMondayThroughSunday() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 16, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val trend = ReadingTrendAggregator.buildLast7DaysTrend(emptyList(), context, now)

        assertEquals(expectedWeekdayLabels(), trend.map { it.weekdayLabel })
    }

    private fun expectedWeekdayLabels(): List<String> = listOf(
        context.getString(space.liushenme.markdownreader.R.string.weekday_mon),
        context.getString(space.liushenme.markdownreader.R.string.weekday_tue),
        context.getString(space.liushenme.markdownreader.R.string.weekday_wed),
        context.getString(space.liushenme.markdownreader.R.string.weekday_thu),
        context.getString(space.liushenme.markdownreader.R.string.weekday_fri),
        context.getString(space.liushenme.markdownreader.R.string.weekday_sat),
        context.getString(space.liushenme.markdownreader.R.string.weekday_sun),
    )

    @Test
    fun startOfWeekMonday_returnsMondayForSaturday() {
        val saturday = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 16, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monday = ReadingTrendAggregator.startOfWeekMonday(saturday)
        assertEquals(Calendar.MONDAY, monday.get(Calendar.DAY_OF_WEEK))
        assertEquals(11, monday.get(Calendar.DAY_OF_MONTH))
    }
}
