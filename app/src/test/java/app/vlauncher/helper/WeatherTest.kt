package app.vlauncher.helper

import app.vlauncher.data.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {

    @Test
    fun `wmo codes map to short labels`() {
        assertEquals("Clear", Weather.labelForCode(0))
        assertEquals("Mostly clear", Weather.labelForCode(1))
        assertEquals("Cloudy", Weather.labelForCode(3))
        assertEquals("Fog", Weather.labelForCode(45))
        assertEquals("Drizzle", Weather.labelForCode(55))
        assertEquals("Rain", Weather.labelForCode(63))
        assertEquals("Snow", Weather.labelForCode(75))
        assertEquals("Thunderstorm", Weather.labelForCode(95))
    }

    @Test
    fun `temperature formats in celsius and fahrenheit`() {
        val reading = WeatherReading(10, "Clear", 1L)
        assertEquals("10° Clear", Weather.format(reading, Constants.Weather.UNIT_CELSIUS))
        assertEquals("50° Clear", Weather.format(reading, Constants.Weather.UNIT_FAHRENHEIT))
    }

    @Test
    fun `cache staleness honours custom interval`() {
        val now = 10_000_000L
        assertTrue(Weather.isStale(now - 31 * 60 * 1000L, now, maxAgeMinutes = 30))
        assertFalse(Weather.isStale(now - 29 * 60 * 1000L, now, maxAgeMinutes = 30))
        assertFalse(Weather.isStale(now - 11 * 60 * 1000L, now, maxAgeMinutes = 720))
    }

    @Test
    fun `cache is stale only after one hour`() {
        val now = 10_000_000L
        val hour = Weather.MAX_AGE_MS

        assertFalse(Weather.isStale(now, now))
        assertFalse(Weather.isStale(now - hour, now))
        assertTrue(Weather.isStale(now - hour - 1, now))
        assertTrue(Weather.isStale(0L, now))
    }
}
