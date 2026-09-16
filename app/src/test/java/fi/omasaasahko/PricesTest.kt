package fi.omasaasahko

import fi.omasaasahko.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.*

class PricesTest {
    private val date = LocalDate.of(2026, 9, 16)
    private fun quarters(day: LocalDate = date, price: String = "100") : List<QuarterPrice> {
        val start = day.atStartOfDay(HELSINKI).toInstant()
        val end = day.plusDays(1).atStartOfDay(HELSINKI).toInstant()
        return generateSequence(start) { it.plusSeconds(900) }.takeWhile { it < end }.map { QuarterPrice(it, BigDecimal(price)) }.toList()
    }
    @Test fun `conversion adds Finnish VAT exactly once`() {
        assertEquals("12,550", Prices.format(Prices.withVat(BigDecimal("100"))))
        assertEquals("-1,255", Prices.format(Prices.withVat(BigDecimal("-10"))))
        assertEquals("0,000", Prices.format(BigDecimal("-0.0001")))
        assertEquals("6,125", Prices.format(BigDecimal("6.125")))
        assertEquals("1,235", Prices.format(BigDecimal("1.2345")))
        assertEquals("–", Prices.format(null))
    }
    @Test fun `VAT off applies to quarter and hourly prices including negatives`() {
        for (resolution in Resolution.entries) {
            val data = quarters(price = "-100")
            val net = Prices.slots(data, date, resolution, includeVat = false)
            val gross = Prices.slots(data, date, resolution, includeVat = true)
            assertEquals("-10,000", Prices.format(net.first().centsPerKwh))
            assertEquals("-12,550", Prices.format(gross.first().centsPerKwh))
            assertEquals("-10,000", Prices.format(Prices.average(net)))
            assertEquals("-12,550", Prices.format(Prices.average(gross)))
        }
    }
    @Test fun `hourly average uses unrounded quarters`() {
        val data = quarters().take(4).mapIndexed { i, q -> q.copy(euroPerMwh = BigDecimal(listOf("0", "0", "0", "0.017")[i])) }
        val hourly = Prices.slots(data, date, Resolution.HOUR).first()
        assertEquals("0,001", Prices.format(hourly.centsPerKwh))
        assertEquals(0, BigDecimal("0.000533375").compareTo(hourly.centsPerKwh))
    }
    @Test fun `missing quarter does not create a full hourly price or daily mean`() {
        val data = quarters().filterIndexed { i, _ -> i != 2 }
        val hours = Prices.slots(data, date, Resolution.HOUR)
        assertNull(hours.first().centsPerKwh)
        assertEquals(23, hours.count { it.centsPerKwh != null })
        assertNull(Prices.average(hours))
    }
    @Test fun `day mean includes every interval and negative prices`() {
        val data = quarters().mapIndexed { i, q -> if (i < 48) q.copy(euroPerMwh = BigDecimal("-100")) else q }
        assertEquals("0,000", Prices.format(Prices.average(Prices.slots(data, date, Resolution.QUARTER))))
        assertEquals("0,000", Prices.format(Prices.average(Prices.slots(data, date, Resolution.HOUR))))
    }
    @Test fun `conflicting duplicates become missing and identical duplicates count once`() {
        val data = quarters()
        assertNotNull(Prices.slots(data + data.first(), date, Resolution.HOUR).first().centsPerKwh)
        assertNull(Prices.slots(data + data.first().copy(euroPerMwh = BigDecimal("200")), date, Resolution.HOUR).first().centsPerKwh)
    }
    @Test fun `spring and autumn use actual delivery duration`() {
        listOf(LocalDate.of(2026, 3, 29) to 23, LocalDate.of(2026, 10, 25) to 25).forEach { (day, count) ->
            val data = quarters(day)
            assertEquals(count * 4, Prices.slots(data, day, Resolution.QUARTER).size)
            val slots = Prices.slots(data, day, Resolution.HOUR)
            assertEquals(count, slots.size)
            assertTrue(slots.all { it.centsPerKwh != null })
            assertEquals("12,550", Prices.format(Prices.average(slots)))
        }
        val repeated = Prices.slots(quarters(LocalDate.of(2026, 10, 25)), LocalDate.of(2026, 10, 25), Resolution.HOUR)
            .filter { it.start.atZone(HELSINKI).hour == 3 }
        assertEquals(2, repeated.size)
        assertNotEquals(clockLabel(repeated[0].start), clockLabel(repeated[1].start))
    }
    @Test fun `interval and date boundaries are exclusive at end`() {
        val slots = Prices.slots(quarters(), date, Resolution.QUARTER)
        assertTrue(slots.first().contains(slots.first().start))
        assertFalse(slots.first().contains(slots.first().end))
        assertTrue(slots[1].contains(slots.first().end))
        assertEquals(96, slots.size)
        assertTrue(Prices.slots(quarters(), date.plusDays(1), Resolution.HOUR).all { it.centsPerKwh == null })
    }
}
