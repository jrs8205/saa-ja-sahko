package fi.omasaasahko

import fi.omasaasahko.data.FmiObservations
import fi.omasaasahko.domain.Place
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FmiObservationsTest {
    private val now = Instant.parse("2026-09-17T12:00:00Z")
    private val place = Place(60.39, 25.66, "Porvoo", now)
    private fun body(nearValues: String = "14 NaN 200", extra: String = "", extraValues: String = "") = """
        <root xmlns:gml="http://www.opengis.net/gml/3.2" xmlns:xlink="http://www.w3.org/1999/xlink">
          <Location><gml:identifier codeSpace="id/fmisid">far</gml:identifier><gml:name codeSpace="id/name">Kauempi</gml:name><representativePoint xlink:href="#p-far"/></Location>
          <Location><gml:identifier codeSpace="id/fmisid">near</gml:identifier><gml:name codeSpace="id/name">Lähempi</gml:name><representativePoint xlink:href="#p-near"/></Location>
          <gml:Point gml:id="p-far"><gml:pos>60.2 25.6</gml:pos></gml:Point>
          <gml:Point gml:id="p-near"><gml:pos>60.391 25.66</gml:pos></gml:Point>
          <positions>60.2 25.6 ${now.epochSecond} 60.391 25.66 ${now.epochSecond - 600} $extra</positions>
          <doubleOrNilReasonTupleList>16 8 100 $nearValues $extraValues</doubleOrNilReasonTupleList>
          <DataRecord><field name="t2m"/><field name="ws_10min"/><field name="wd_10min"/></DataRecord>
        </root>
    """.trimIndent()

    @Test fun `nearest reporting station wins without mixing missing values from another station`() {
        val result = FmiObservations.parse(body(), place, now)!!
        assertEquals("near", result.station.id)
        assertEquals("Lähempi", result.station.name)
        assertEquals(14.0, result.weather.temperature!!, 0.0)
        assertNull(result.weather.wind)
        assertTrue(result.station.distanceMeters in 100.0..120.0)
    }
    @Test fun `nearest station without a temperature falls back to the next reporting station`() {
        assertEquals("far", FmiObservations.parse(body("NaN 2 200"), place, now)!!.station.id)
    }
    @Test fun `latest valid sample selected and future samples ignored`() {
        val xml = body(extra = "60.391 25.66 ${now.epochSecond} 60.391 25.66 ${now.epochSecond + 600}",
            extraValues = "15 3 210 99 99 99")
        assertEquals(15.0, FmiObservations.parse(xml, place, now)!!.weather.temperature!!, 0.0)
        assertNull(FmiObservations.parse(xml, place, now.plusSeconds(7_000)))
    }
    @Test fun `incomplete tuple matrix cannot shift observations between stations`() {
        assertThrows(IllegalArgumentException::class.java) { FmiObservations.parse(body("14 2"), place, now) }
    }
}
