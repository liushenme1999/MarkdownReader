package space.liushenme.markdownreader.data.repository

import space.liushenme.markdownreader.data.local.entity.ReadingProgressEntity
import java.util.Calendar
import java.util.Date

data class DailyReadingTrendDay(
    val weekdayLabel: String,
    val minutes: Int,
)

/**
 * 将 [reading_progress] 记录聚合为「本周一至周日」的阅读时长（周一为第一列）。
 */
object ReadingTrendAggregator {

    const val CHARS_PER_HOUR = 20_000
    const val WEEKLY_GOAL_MINUTES = 15

    private val WEEKDAY_LABELS_MON_FIRST = listOf("一", "二", "三", "四", "五", "六", "日")

    fun buildLast7DaysTrend(
        records: List<ReadingProgressEntity>,
        now: Calendar = Calendar.getInstance(),
    ): List<DailyReadingTrendDay> {
        val grouped = records.groupBy { startOfDayMillis(it.date) }
        val weekStart = startOfWeekMonday(now)

        return WEEKDAY_LABELS_MON_FIRST.mapIndexed { index, label ->
            val day = (weekStart.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, index)
            }
            val dayRecords = grouped[startOfDayMillis(day.time)] ?: emptyList()
            val minutesRecorded = dayRecords.sumOf { it.readTimeMinutes }
            val chars = dayRecords.sumOf { it.readChars }
            val minutes = if (minutesRecorded > 0) {
                minutesRecorded
            } else {
                estimateMinutesFromChars(chars)
            }
            DailyReadingTrendDay(
                weekdayLabel = label,
                minutes = minutes,
            )
        }
    }

    fun estimateMinutesFromChars(chars: Int): Int {
        if (chars <= 0) return 0
        return (chars.toLong() * 60 / CHARS_PER_HOUR).toInt().coerceAtLeast(1)
    }

    fun startOfDayMillis(date: Date): Long = startOfDay(Calendar.getInstance().apply { time = date }).timeInMillis

    fun startOfDay(calendar: Calendar): Calendar =
        (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    /** 本周一 00:00 至本周日 23:59:59.999 */
    fun last7DaysRange(): Pair<Date, Date> {
        val start = startOfWeekMonday(Calendar.getInstance())
        val end = (start.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 6)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return start.time to end.time
    }

    fun startOfWeekMonday(calendar: Calendar): Calendar {
        val day = startOfDay(calendar)
        val daysFromMonday = when (day.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
        return day.apply { add(Calendar.DAY_OF_YEAR, -daysFromMonday) }
    }

    fun dayIndexInCurrentWeek(now: Calendar = Calendar.getInstance()): Int {
        return when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
    }
}
