package fi.omasaasahko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeatherProviderLiveTest {
    @Test fun `live fields match original FMI XML and Open Meteo JSON including units and dates`() {
        assumeTrue(System.getenv("SAA_LIVE_API_TESTS") == "1")
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repo = Repository(context); val now = Instant.now()
            val place = Place(60.29,24.84,"Tikkurila, Vantaa",now, origin = PlaceOrigin.DEVICE)
            val a = async { repo.weather(WeatherSource.FMI,place,now) }
            val b = async { repo.weather(WeatherSource.OPEN_METEO,place,now) }
            val fmi = a.await(); val om = b.await()
            fun body(source: WeatherSource) = JSONObject(File(context.filesDir,"forecast-cache/${source.name}-DEVICE.json").readText()).getString("body")
            val raw = JSONObject(body(WeatherSource.OPEN_METEO)); val hourly = raw.getJSONObject("hourly"); val daily = raw.getJSONObject("daily")
            val units = raw.getJSONObject("hourly_units")
            assertEquals("°C",units.getString("temperature_2m")); assertEquals("m/s",units.getString("wind_speed_10m")); assertEquals("mm",units.getString("precipitation"))
            assertEquals("unixtime",units.getString("time")); assertEquals("Europe/Helsinki",raw.getString("timezone"))
            fun value(obj: JSONObject,key: String,i: Int): Double? = obj.getJSONArray(key).let { if (it.isNull(i)) null else it.getDouble(i) }
            assertEquals(hourly.getJSONArray("time").length(),om.hours.size)
            om.hours.forEachIndexed { i,h ->
                assertEquals(hourly.getJSONArray("time").getLong(i),h.time.epochSecond)
                mapOf("temperature_2m" to h.temperature,"apparent_temperature" to h.feelsLike,"wind_speed_10m" to h.wind,
                    "wind_direction_10m" to h.windDirection,"precipitation" to h.rain,"precipitation_probability" to h.rainProbability).forEach { (key,v) -> assertEquals(key,value(hourly,key,i),v) }
                assertEquals(hourly.getJSONArray("is_day").getInt(i)==0,h.night)
                assertNotEquals(Condition.UNKNOWN,h.condition)
            }
            om.days.forEachIndexed { i,d ->
                assertEquals(Instant.ofEpochSecond(daily.getJSONArray("time").getLong(i)).atZone(HELSINKI).toLocalDate(),d.date)
                mapOf("temperature_2m_min" to d.low,"temperature_2m_max" to d.high,"precipitation_sum" to d.rain,
                    "precipitation_probability_max" to d.probability,"wind_speed_10m_max" to d.wind,"uv_index_max" to d.uvMax).forEach { (key,v) -> assertEquals(key,value(daily,key,i),v) }
                assertEquals(daily.getJSONArray("sunrise").getLong(i),d.sunrise!!.epochSecond)
                assertEquals(daily.getJSONArray("sunset").getLong(i),d.sunset!!.epochSecond)
            }
            val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware=true }.newDocumentBuilder().parse(body(WeatherSource.FMI).byteInputStream())
            val rows = document.getElementsByTagNameNS("*","BsWfsElement")
            val expected = mutableMapOf<Instant,MutableMap<String,Double>>()
            for (i in 0 until rows.length) {
                val row = rows.item(i) as Element
                fun text(name: String) = row.getElementsByTagNameNS("*",name).item(0).textContent.trim()
                val number = text("ParameterValue").toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
                expected.getOrPut(Instant.parse(text("Time"))) { mutableMapOf() }[text("ParameterName")]=number
            }
            assertEquals(expected.size,fmi.hours.size)
            fmi.hours.forEach { h ->
                val values=expected.getValue(h.time)
                mapOf("Temperature" to h.temperature,"FeelsLike" to h.feelsLike,"WindSpeedMS" to h.wind,
                    "WindDirection" to h.windDirection,"Precipitation1h" to h.rain).forEach { (key,v) -> assertEquals(key,values[key],v) }
                assertEquals(values["SmartSymbol"]!!.toInt() in 101..177,h.night)
                assertNotEquals(Condition.UNKNOWN,h.condition)
                assertNull(h.rainProbability)
            }
            fmi.days.forEach { d ->
                val from=d.date.atStartOfDay(HELSINKI).toInstant(); val to=d.date.plusDays(1).atStartOfDay(HELSINKI).toInstant()
                val rain=expected.filterKeys { it>from && it<=to }.values.mapNotNull { it["Precipitation1h"] }
                if (rain.size==java.time.Duration.between(from,to).toHours().toInt()) assertEquals(rain.sum(),d.rain!!,1e-8)
                else assertNull(d.rain)
            }
            val warnings=WarningService(context).refresh()
            println("Verified live scalar fields: FMI ${fmi.hours.size} hours/${fmi.days.size} days; Open-Meteo ${om.hours.size} hours/${om.days.size} days and units; CAP ${warnings.warnings.size} current/future warnings, local ${warnings.local(place,now).size}, partial=${warnings.partial}; at $now")
            assertFalse(warnings.partial)
        }
    }
}
