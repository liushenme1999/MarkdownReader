package space.liushenme.markdownreader.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferenceNumbersTest {

    @Test
    fun asInt_acceptsLongFromGson() {
        assertEquals(80, PreferenceNumbers.asInt(80L))
        assertEquals(0, PreferenceNumbers.asInt(0L))
        assertEquals(-657183, PreferenceNumbers.asInt(-657183L))
    }

    @Test
    fun asInt_acceptsUnsignedArgbLong() {
        val paperBg = 0xFFF5F0E1L
        assertEquals(0xFFF5F0E1.toInt(), PreferenceNumbers.asInt(paperBg))
    }

    @Test
    fun asInt_rejectsTimestamp() {
        assertNull(PreferenceNumbers.asInt(1_726_000_000_000L))
    }

    @Test
    fun asFloat_acceptsDouble() {
        assertEquals(1.5f, PreferenceNumbers.asFloat(1.5)!!, 0.0001f)
    }
}
