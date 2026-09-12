package space.liushenme.markdownreader.data.backup

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupParallelTest {

    @Test
    fun mapLimitedParallel_capsInFlightWork() = runBlocking {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val results = mapLimitedParallel((1..8).toList(), parallelism = 3) { value ->
            val now = inFlight.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            delay(20)
            inFlight.decrementAndGet()
            value * 2
        }
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), results)
        assertTrue(peak.get() in 1..3)
    }

    @Test
    fun mapLimitedParallel_emptyIsEmpty() = runBlocking {
        assertEquals(emptyList<Int>(), mapLimitedParallel(emptyList<Int>(), 4) { it })
    }
}
