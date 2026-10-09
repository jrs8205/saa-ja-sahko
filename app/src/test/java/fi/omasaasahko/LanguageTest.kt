package fi.omasaasahko

import fi.omasaasahko.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class LanguageTest {
    private val noon = Instant.parse("2026-09-17T09:10:00Z") // 12.10 Finnish time

    @Test fun `clock punctuation follows the language`() {
        assertEquals("12.10", clockLabel(noon, AppLanguage.FI))
        assertEquals("12.10", clockLabel(noon, AppLanguage.SV))
        assertEquals("12:10", clockLabel(noon, AppLanguage.EN))
    }

    @Test fun `decimals use a comma in Finnish and Swedish and a point in English`() {
        assertEquals("12,3°", temperature(12.34, AppLanguage.FI))
        assertEquals("12,3°", temperature(12.34, AppLanguage.SV))
        assertEquals("12.3°", temperature(12.34, AppLanguage.EN))
        assertEquals("0.0°", temperature(-0.04, AppLanguage.EN))
        assertEquals("80", decimal(80.4, AppLanguage.EN, 0))
        assertEquals("4,012", Prices.format(BigDecimal("4.0115"), AppLanguage.SV))
        assertEquals("4.012", Prices.format(BigDecimal("4.0115"), AppLanguage.EN))
        assertEquals("–", Prices.format(null, AppLanguage.EN))
    }

    @Test fun `dates follow the language`() {
        val day = LocalDate.of(2026, 9, 17)
        assertEquals("Torstaina 17.9.", AppLanguage.FI.day(day))
        assertEquals("Torsdag 17.9.", AppLanguage.SV.day(day))
        assertEquals("Thursday 17 Sep", AppLanguage.EN.day(day))
        assertEquals("Torstaina 17. syyskuuta", AppLanguage.FI.longDay(day))
        assertEquals("Torsdag 17 september", AppLanguage.SV.longDay(day))
        assertEquals("Thursday 17 September", AppLanguage.EN.longDay(day))
        assertEquals("17.9.", AppLanguage.SV.shortDate(day))
        assertEquals("17 Sep", AppLanguage.EN.shortDate(day))
        assertEquals("to", AppLanguage.FI.weekday(day))
        assertEquals("Torstaina", AppLanguage.FI.weekdayLong(day))
        assertEquals("Torsdag", AppLanguage.SV.weekdayLong(day))
        assertEquals("Thursday", AppLanguage.EN.weekdayLong(day))
        assertEquals("Thu", AppLanguage.EN.weekday(day))
        assertEquals("17 Sep 12:10", updatedLabel(noon, AppLanguage.EN))
        assertEquals("17.9. 12.10", updatedLabel(noon, AppLanguage.SV))
        assertEquals("–", updatedLabel(null, AppLanguage.FI))
    }

    @Test fun `unknown language tag falls back to English`() {
        assertEquals(AppLanguage.EN, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.SV, AppLanguage.fromTag("sv"))
        assertEquals(AppLanguage.FI, AppLanguage.fromTag("fi"))
    }
}
