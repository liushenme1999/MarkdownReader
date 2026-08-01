package space.liushenme.markdownreader.data.repository

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReadingSessionStatsTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()


    @Test
    fun elapsedMillisToMinutes_ignoresVeryShortSegments() {
        assertEquals(0, ReadingSessionStats.elapsedMillisToMinutes(0))
        assertEquals(0, ReadingSessionStats.elapsedMillisToMinutes(14_999))
    }

    @Test
    fun elapsedMillisToMinutes_roundsAndCaps() {
        assertEquals(1, ReadingSessionStats.elapsedMillisToMinutes(15_000))
        assertEquals(1, ReadingSessionStats.elapsedMillisToMinutes(89_999))
        assertEquals(2, ReadingSessionStats.elapsedMillisToMinutes(90_000))
        assertEquals(
            ReadingSessionStats.MAX_SEGMENT_MINUTES,
            ReadingSessionStats.elapsedMillisToMinutes(30L * 60 * 60 * 1000),
        )
    }

    @Test
    fun continuousReadingDays_countsBackFromToday() {
        val today = day(2026, Calendar.JULY, 21)
        val yesterday = day(2026, Calendar.JULY, 20)
        val twoDaysAgo = day(2026, Calendar.JULY, 19)
        val fourDaysAgo = day(2026, Calendar.JULY, 17)

        assertEquals(
            3,
            ReadingSessionStats.continuousReadingDays(
                listOf(today, yesterday, twoDaysAgo, fourDaysAgo),
                todayStartMillis = today,
            ),
        )
    }

    @Test
    fun continuousReadingDays_allowsMissingTodayUsingYesterday() {
        val today = day(2026, Calendar.JULY, 21)
        val yesterday = day(2026, Calendar.JULY, 20)
        val twoDaysAgo = day(2026, Calendar.JULY, 19)

        assertEquals(
            2,
            ReadingSessionStats.continuousReadingDays(
                listOf(yesterday, twoDaysAgo),
                todayStartMillis = today,
            ),
        )
    }

    @Test
    fun continuousReadingDays_breaksOnGap() {
        val today = day(2026, Calendar.JULY, 21)
        val twoDaysAgo = day(2026, Calendar.JULY, 19)

        assertEquals(
            0,
            ReadingSessionStats.continuousReadingDays(
                listOf(twoDaysAgo),
                todayStartMillis = today,
            ),
        )
    }

    @Test
    fun formatReadDuration_adaptsUnits() {
        assertEquals(
            context.getString(space.liushenme.markdownreader.R.string.duration_minutes_only, 0),
            ReadingSessionStats.formatReadDuration(0, context),
        )
        assertEquals(
            context.getString(space.liushenme.markdownreader.R.string.duration_minutes_only, 45),
            ReadingSessionStats.formatReadDuration(45, context),
        )
        assertEquals(
            context.getString(space.liushenme.markdownreader.R.string.duration_hours_only, 1),
            ReadingSessionStats.formatReadDuration(60, context),
        )
        assertEquals(
            context.getString(space.liushenme.markdownreader.R.string.duration_hours_and_minutes, 1, 5),
            ReadingSessionStats.formatReadDuration(65, context),
        )
        assertEquals(
            context.getString(space.liushenme.markdownreader.R.string.duration_hours_only, 2),
            ReadingSessionStats.formatReadDuration(120, context),
        )
    }

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
