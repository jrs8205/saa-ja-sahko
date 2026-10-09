package fi.omasaasahko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.data.Repository
import fi.omasaasahko.domain.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Opt-in comparison with Nord Pool's public data portal; never a runtime app dependency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfficialPricesLiveTest {
    @Test fun `official Nord Pool FI prices match app quarters hours and VAT`() {
        assumeTrue(System.getenv("SAA_PRICE_CHECK") == "1")
        runBlocking {
            val now = Instant.now()
            val prices = Repository(ApplicationProvider.getApplicationContext<Context>()).prices(now)
            val today = now.atZone(HELSINKI).toLocalDate()
            val tomorrow = today.plusDays(1)
            val start = today.atStartOfDay(HELSINKI).toInstant()
            val end = tomorrow.plusDays(1).atStartOfDay(HELSINKI).toInstant()
            // Nord Pool's delivery date follows central Europe, not the Finnish calendar.
            val marketZone = ZoneId.of("Europe/Oslo")
            val first = start.atZone(marketZone).toLocalDate()
            val last = end.minusSeconds(1).atZone(marketZone).toLocalDate()
            val official = sortedMapOf<Instant, BigDecimal>()
            val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
            for (day in generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }) {
                val url = "https://dataportal-api.nordpoolgroup.com/api/DayAheadPrices?date=$day&market=DayAhead&deliveryArea=FI&currency=EUR"
                val json = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "Nord Pool HTTP ${response.code} for $day" }
                    JSONObject(response.body.string())
                }
                assertEquals("EUR", json.getString("currency"))
                assertEquals("DayAhead", json.getString("market"))
                val entries = json.getJSONArray("multiAreaEntries")
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    val time = Instant.parse(entry.getString("deliveryStart"))
                    assertEquals("Quarter duration", 900L, Duration.between(time,
                        Instant.parse(entry.getString("deliveryEnd"))).seconds)
                    if (time >= start && time < end) {
                        val raw = entry.getJSONObject("entryPerArea").opt("FI")
                        if (raw != null && raw != JSONObject.NULL) {
                            val value = BigDecimal(raw.toString())
                            val previous = official.put(time, value)
                            assertTrue("Conflicting official interval $time", previous == null || previous.compareTo(value) == 0)
                        }
                    }
                }
            }
            assertTrue("Today's official prices unavailable", official.keys.any { it.atZone(HELSINKI).toLocalDate() == today })
            val app = prices.quarters.filter { it.start >= start && it.start < end }.associate { it.start to it.euroPerMwh }
            assertEquals("Missing or unexpected intervals between providers", official.keys, app.keys)
            official.forEach { (time, value) ->
                assertEquals("Raw EUR/MWh mismatch at $time", 0, value.compareTo(app.getValue(time)))
            }
            var checkedSlots = 0
            for (date in listOf(today, tomorrow)) {
                val expectedQuarters = Duration.between(date.atStartOfDay(HELSINKI), date.plusDays(1).atStartOfDay(HELSINKI)).toMinutes() / 15
                val count = official.keys.count { it.atZone(HELSINKI).toLocalDate() == date }
                if (date == tomorrow && count == 0) continue // Tomorrow need not be published yet.
                assertEquals("Incomplete official day $date", expectedQuarters.toInt(), count)
                for (resolution in Resolution.entries) for (vat in listOf(false, true)) {
                    val slots = Prices.slots(prices.quarters, date, resolution, vat)
                    for (slot in slots) {
                        val parts = generateSequence(slot.start) { it.plusSeconds(900) }.takeWhile { it < slot.end }
                            .map { official.getValue(it) }.toList()
                        val expected = parts.reduce(BigDecimal::add).divide(BigDecimal(parts.size))
                            .divide(BigDecimal.TEN).multiply(if (vat) BigDecimal("1.255") else BigDecimal.ONE)
                        val display = expected.setScale(3, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
                        assertEquals("Displayed price $date $resolution VAT=$vat ${slot.start}", display, Prices.format(slot.centsPerKwh, AppLanguage.FI))
                        checkedSlots++
                    }
                }
            }
            println("Official price check at $now: ${official.size} FI quarters match Nord Pool exactly; $checkedSlots quarter/hour displays checked including VAT on/off. Finnish day boundaries verified.")
        }
    }
}
