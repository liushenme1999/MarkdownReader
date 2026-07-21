package space.liushenme.markdownreader.data.repository

import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

/**
 * 阅读会话时长与连续天数等纯计算，便于单测与多处复用。
 */
object ReadingSessionStats {

    /** 单次前台片段写入上限，避免异常计时污染总量。 */
    const val MAX_SEGMENT_MINUTES = 12 * 60

    /** 不足此时长（毫秒）的片段不记分钟，仍可记前进字数。 */
    const val MIN_COUNTABLE_MILLIS = 15_000L

    /**
     * 将前台经过的墙钟毫秒转为应记入的阅读分钟。
     * - 不足 [MIN_COUNTABLE_MILLIS] → 0
     * - 否则按分钟四舍五入，至少 1，并封顶 [MAX_SEGMENT_MINUTES]
     */
    fun elapsedMillisToMinutes(elapsedMs: Long): Int {
        if (elapsedMs < MIN_COUNTABLE_MILLIS) return 0
        val rounded = (elapsedMs / 60_000.0).roundToInt().coerceAtLeast(1)
        return rounded.coerceAtMost(MAX_SEGMENT_MINUTES)
    }

    /** 今日 00:00:00.000 */
    fun startOfToday(now: Calendar = Calendar.getInstance()): Date =
        ReadingTrendAggregator.startOfDay(now).time

    /**
     * 连续阅读天数：从今天（若今天无记录则从昨天）起，向前数连续有阅读记录的自然日。
     * [activeDayStartMillis] 为各活跃日的 00:00 epoch millis（去重即可）。
     */
    fun continuousReadingDays(
        activeDayStartMillis: Collection<Long>,
        todayStartMillis: Long = startOfToday().time,
    ): Int {
        if (activeDayStartMillis.isEmpty()) return 0
        val daySet = activeDayStartMillis.toHashSet()
        var expected = if (todayStartMillis in daySet) {
            todayStartMillis
        } else {
            previousDayStartMillis(todayStartMillis)
        }
        if (expected !in daySet) return 0
        var streak = 0
        while (expected in daySet) {
            streak++
            expected = previousDayStartMillis(expected)
        }
        return streak
    }

    private fun previousDayStartMillis(dayStartMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dayStartMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return ReadingTrendAggregator.startOfDay(cal).timeInMillis
    }

    /** 总览时长文案：不足 1 小时显示分钟，否则「X小时」或「X小时Y分」。 */
    fun formatReadDuration(totalMinutes: Int): String {
        val minutes = totalMinutes.coerceAtLeast(0)
        if (minutes < 60) return "${minutes}分钟"
        val hours = minutes / 60
        val rem = minutes % 60
        return if (rem == 0) "${hours}小时" else "${hours}小时${rem}分"
    }
}
