package fi.omasaasahko

import fi.omasaasahko.data.WeatherCodes
import fi.omasaasahko.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.Duration

class WeatherCodesTest {
    @Test fun `all 45 official FMI day codes and night variants are recognised`() {
        val families = mapOf(
            Condition.CLEAR to listOf(1), Condition.MOSTLY_CLEAR to listOf(2), Condition.PARTLY_CLOUDY to listOf(4),
            Condition.MOSTLY_CLOUDY to listOf(6), Condition.CLOUDY to listOf(7), Condition.FOG to listOf(9),
            Condition.DRIZZLE to listOf(11), Condition.FREEZING_DRIZZLE to listOf(14), Condition.FREEZING_RAIN to listOf(17),
            Condition.RAIN to listOf(21,24,27,31,32,33,34,35,36,37,38,39),
            Condition.SLEET to listOf(41,42,43,44,45,46,47,48,49), Condition.SNOW to listOf(51,52,53,54,55,56,57,58,59),
            Condition.HAIL to listOf(61,64,67), Condition.THUNDER to listOf(71,74,77))
        assertEquals(45, families.values.sumOf { it.size })
        families.forEach { (condition, codes) -> codes.forEach { number ->
            assertEquals("FMI $number", condition, WeatherCodes.fmi(number).condition)
            assertFalse(WeatherCodes.fmi(number).night)
            assertEquals(condition, WeatherCodes.fmi(number + 100).condition)
            assertTrue(WeatherCodes.fmi(number + 100).night)
        } }
        listOf(null,0,3,5,10,60,62,100,201,-1).forEach { assertEquals(Condition.UNKNOWN, WeatherCodes.fmi(it).condition) }
    }
    @Test fun `all 28 WMO codes preserve freezing precipitation hail and cloud distinctions`() {
        val families = mapOf(Condition.CLEAR to listOf(0), Condition.MOSTLY_CLEAR to listOf(1),
            Condition.PARTLY_CLOUDY to listOf(2), Condition.CLOUDY to listOf(3), Condition.FOG to listOf(45,48),
            Condition.DRIZZLE to listOf(51,53,55), Condition.FREEZING_DRIZZLE to listOf(56,57),
            Condition.FREEZING_RAIN to listOf(66,67), Condition.RAIN to listOf(61,63,65,80,81,82),
            Condition.SNOW to listOf(71,73,75,77,85,86), Condition.THUNDER to listOf(95), Condition.THUNDER_HAIL to listOf(96,99))
        assertEquals(28, families.values.sumOf { it.size })
        families.forEach { (condition, codes) -> codes.forEach { assertEquals("WMO $it", condition, WeatherCodes.wmo(it).condition) } }
        assertNotEquals(WeatherCodes.wmo(61).description, WeatherCodes.wmo(65).description)
        assertNotEquals(WeatherCodes.fmi(31).description, WeatherCodes.fmi(39).description)
        assertEquals(Condition.UNKNOWN, WeatherCodes.wmo(100).condition)
    }
    @Test fun `daily rain belongs to preceding hour including 23 and 25 hour days`() {
        listOf("2026-09-16", "2026-03-29", "2026-10-25").forEach { value ->
            val day = LocalDate.parse(value); val start = day.atStartOfDay(HELSINKI).toInstant()
            val count = Duration.between(start, day.plusDays(1).atStartOfDay(HELSINKI).toInstant()).toHours().toInt()
            val hours = (0..count).map { i -> WeatherHour(start.plusSeconds(i * 3600L), temperature = if (i == count) -90.0 else i.toDouble(),
                rain = if (i == 0) 100.0 else if (i == count) 2.0 else 1.0) }
            val result = dailyForecast(hours, HELSINKI).first()
            assertEquals((count + 1).toDouble(), result.rain!!, 0.0)
            assertEquals(0.0, result.low!!, 0.0)
            assertTrue(result.complete)
            val missing = dailyForecast(hours.dropLast(1), HELSINKI).first()
            assertNull(missing.rain); assertFalse(missing.complete)
        }
    }
}
