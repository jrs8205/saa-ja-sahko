package fi.omasaasahko

import fi.omasaasahko.data.Parsers
import fi.omasaasahko.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParsersTest {
    private val now = Instant.parse("2026-09-16T09:00:00Z")
    private val place = Place(60.2925, 25.0408, "Testipaikka", now, origin = PlaceOrigin.DEVICE)
    @Test fun `Elering retains raw decimal price and discards invalid intervals`() {
        val data = Parsers.prices("""{"success":true,"data":{"fi":[{"timestamp":1789552800,"price":12.123456},{"timestamp":1789553700,"price":null},{"timestamp":1789552801,"price":100}]}}""", now)
        assertEquals(1, data.quarters.size)
        assertEquals("12.123456", data.quarters[0].euroPerMwh.toPlainString())
    }
    @Test(expected = IllegalArgumentException::class) fun `Elering error is not a successful empty update`() {
        Parsers.prices("""{"success":false,"data":{"fi":[]}}""", now)
    }
    @Test fun `FMI keeps missing data missing and reads feels like from source`() {
        val body = """<wfs:FeatureCollection xmlns:wfs="x" xmlns:BsWfs="y"><BsWfs:BsWfsElement><BsWfs:Time>2026-09-16T09:00:00Z</BsWfs:Time><BsWfs:ParameterName>Temperature</BsWfs:ParameterName><BsWfs:ParameterValue>15</BsWfs:ParameterValue></BsWfs:BsWfsElement><BsWfs:BsWfsElement><BsWfs:Time>2026-09-16T09:00:00Z</BsWfs:Time><BsWfs:ParameterName>FeelsLike</BsWfs:ParameterName><BsWfs:ParameterValue>12.75</BsWfs:ParameterValue></BsWfs:BsWfsElement><BsWfs:BsWfsElement><BsWfs:Time>2026-09-16T09:00:00Z</BsWfs:Time><BsWfs:ParameterName>Precipitation1h</BsWfs:ParameterName><BsWfs:ParameterValue>NaN</BsWfs:ParameterValue></BsWfs:BsWfsElement></wfs:FeatureCollection>"""
        val forecast = Parsers.fmi(body, place, now)
        assertEquals(12.75, forecast.hours.single().feelsLike!!, 0.0)
        assertNull(forecast.hours.single().rain)
        assertNull(forecast.hours.single().rainProbability)
        assertFalse(forecast.days.single().complete)
        assertNull(forecast.days.single().rain)
        assertNotNull(forecast.current(now))
        assertNull(forecast.current(now.plusSeconds(7200)))
    }
    @Test fun `observation expires separately from forecast`() {
        val f = Forecast(WeatherSource.FMI, place, now, emptyList(), emptyList(), observation = WeatherHour(now, 15.0))
        assertNotNull(f.recentObservation(now.plusSeconds(3600)))
        assertNull(f.recentObservation(now.plusSeconds(6000)))
        assertNull(f.recentObservation(now.minusSeconds(60)))
    }
    @Test fun `FMI solar events use their own local day and UTC conversion`() {
        fun row(sample: String, parameter: String, value: String) = """<b:BsWfsElement><b:Time>$sample</b:Time><b:ParameterName>$parameter</b:ParameterName><b:ParameterValue>$value</b:ParameterValue></b:BsWfsElement>"""
        val body = """<w:FeatureCollection xmlns:w="w" xmlns:b="b">""" +
            row("2026-09-15T21:00:00Z", "Temperature", "15") +
            row("2026-09-15T21:00:00Z", "Sunrise", "20260915T034751") +
            row("2026-09-16T00:00:00Z", "Temperature", "14") +
            row("2026-09-16T00:00:00Z", "Sunrise", "20260916T035013") +
            row("2026-09-16T00:00:00Z", "Sunset", "20260916T163923") +
            row("2026-09-16T01:00:00Z", "Sunrise", "NaN") +
            row("2026-09-16T01:00:00Z", "Sunset", "20260230T123456") + "</w:FeatureCollection>"
        val day = Parsers.fmi(body, place, now).days.single()
        assertEquals(LocalDate.of(2026, 9, 16), day.date)
        assertEquals("06.50", clockLabel(day.sunrise!!, AppLanguage.FI))
        assertEquals("19.39", clockLabel(day.sunset!!, AppLanguage.FI))
        assertEquals(Instant.parse("2026-09-16T03:50:13Z"), day.sunrise)
    }
    @Test fun `nearby stations remain separate and closest valid station is used`() {
        fun element(position: String, time: String, value: String) = """<b:BsWfsElement><b:Location><g:Point><g:pos>$position</g:pos></g:Point></b:Location><b:Time>$time</b:Time><b:ParameterName>t2m</b:ParameterName><b:ParameterValue>$value</b:ParameterValue></b:BsWfsElement>"""
        val body = """<w:FeatureCollection xmlns:w="w" xmlns:b="b" xmlns:g="g">""" +
            element("60.5 25.1", "2026-09-16T09:00:00Z", "18") +
            element("60.3 24.85", "2026-09-16T08:50:00Z", "15") + "</w:FeatureCollection>"
        val observed = Parsers.observation(body, place)!!
        assertEquals(15.0, observed.temperature!!, 0.0)
        assertEquals(Instant.parse("2026-09-16T08:50:00Z"), observed.time)
    }
    @Test fun `weather codes preserve snow rain and night family`() {
        assertEquals(Condition.RAIN, Parsers.smart(138))
        assertEquals(Condition.SNOW, Parsers.smart(157))
        assertEquals(Condition.THUNDER, Parsers.smart(174))
        assertEquals(Condition.UNKNOWN, Parsers.smart(null))
        assertEquals(Condition.SNOW, Parsers.wmo(85))
    }
    @Test fun `Open Meteo null fields are not zero and time zone is retained`() {
        val body = """{"timezone":"Europe/Helsinki","hourly":{"time":[1789552800],"temperature_2m":[15],"precipitation":[null],"precipitation_probability":[null],"weather_code":[3],"is_day":[1]},"daily":{"time":[1789506000],"temperature_2m_min":[10],"temperature_2m_max":[16],"sunrise":[null],"sunset":[null],"uv_index_max":[2]}}"""
        val data = Parsers.openMeteo(body, place, now)
        assertNull(data.hours.single().rain)
        assertNull(data.hours.single().rainProbability)
        assertEquals(Condition.CLOUDY, data.hours.single().condition)
        assertEquals(HELSINKI, data.zone)
        assertNull(data.days.single().sunrise)
        assertEquals(2.0, data.days.single().uvMax!!, 0.0)
    }
}
