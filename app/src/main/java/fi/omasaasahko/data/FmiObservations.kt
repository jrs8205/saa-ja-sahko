package fi.omasaasahko.data

import android.util.Xml
import fi.omasaasahko.domain.*
import org.xmlpull.v1.XmlPullParser
import java.time.Instant
import kotlin.math.*

/** Station metadata and sample tuples come from the same FMI multipointcoverage response. */
internal object FmiObservations {
    private data class StationInfo(val id: String, val name: String, val point: String)
    fun parse(body: String, place: Place, now: Instant): StationObservation? {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(body.reader())
        }
        val metadata = mutableListOf<StationInfo>()
        val points = mutableMapOf<String, Pair<Double, Double>>()
        var inLocation = false
        var id = ""; var name = ""; var reference = ""; var pointId: String? = null
        var positions = ""; var values = ""
        val fields = mutableListOf<String>()
        val gml = "http://www.opengis.net/gml/3.2"
        val xlink = "http://www.w3.org/1999/xlink"
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "ExceptionReport" -> error("FMI:n havaintohaku epäonnistui")
                    "Location" -> { inLocation = true; id = ""; name = ""; reference = "" }
                    "identifier" -> if (inLocation && parser.getAttributeValue(null, "codeSpace")?.endsWith("/fmisid") == true) id = parser.nextText().trim()
                    "name" -> if (inLocation && parser.getAttributeValue(null, "codeSpace")?.endsWith("/name") == true) name = parser.nextText().trim()
                    "representativePoint" -> if (inLocation) reference = parser.getAttributeValue(xlink, "href").orEmpty().removePrefix("#")
                    "Point" -> pointId = parser.getAttributeValue(gml, "id")
                    "pos" -> if (pointId != null) {
                        val xy = parser.nextText().trim().split(Regex("\\s+")).mapNotNull(String::toDoubleOrNull)
                        if (xy.size == 2 && xy[0].isFinite() && xy[0] in -90.0..90.0 && xy[1].isFinite() && xy[1] in -180.0..180.0)
                            points[pointId] = xy[0] to xy[1]
                    }
                    "positions" -> { check(positions.isEmpty()); positions = parser.nextText().trim() }
                    "doubleOrNilReasonTupleList" -> { check(values.isEmpty()); values = parser.nextText().trim() }
                    "field" -> parser.getAttributeValue(null, "name")?.let(fields::add)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "Location" -> { if (id.isNotBlank() && name.isNotBlank() && reference.isNotBlank()) metadata += StationInfo(id, name, reference); inLocation = false }
                    "Point" -> pointId = null
                }
            }
            parser.next()
        }
        if (positions.isBlank() || values.isBlank()) return null
        val coordinates = positions.split(Regex("\\s+"))
        val data = values.split(Regex("[\\s,]+"))
        require(fields.isNotEmpty() && fields.distinct().size == fields.size && coordinates.size % 3 == 0 &&
            data.size == coordinates.size / 3 * fields.size) { "Puutteellinen asemahavainto" }
        val stations = metadata.mapNotNull { meta -> points[meta.point]?.let { xy -> xy to WeatherStation(meta.id, meta.name,
            xy.first, xy.second, distanceMeters(place.latitude, place.longitude, xy.first, xy.second)) } }.toMap()
        val latest = mutableMapOf<String, StationObservation>()
        for (i in 0 until coordinates.size / 3) {
            val lat = coordinates[i * 3].toDoubleOrNull() ?: continue
            val lon = coordinates[i * 3 + 1].toDoubleOrNull() ?: continue
            val station = stations[lat to lon] ?: continue
            val epoch = coordinates[i * 3 + 2].toLongOrNull() ?: continue
            if (epoch > now.epochSecond || epoch < now.epochSecond - 90 * 60) continue
            val time = Instant.ofEpochSecond(epoch)
            fun value(key: String): Double? = fields.indexOf(key).takeIf { it >= 0 }
                ?.let { data[i * fields.size + it].toDoubleOrNull()?.takeIf(Double::isFinite) }
            val temperature = value("t2m") ?: continue
            if (latest[station.id]?.weather?.time?.let { it >= time } == true) continue
            latest[station.id] = StationObservation(station, WeatherHour(time, temperature = temperature,
                wind = value("ws_10min"), windDirection = value("wd_10min")))
        }
        return latest.values.minWithOrNull(compareBy<StationObservation> { it.station.distanceMeters }.thenBy { it.station.id })
    }

    fun bbox(place: Place, radiusKm: Int): String {
        val angular = radiusKm * 1000.0 / 6_371_000
        val latitude = Math.toRadians(place.latitude)
        val deltaLat = Math.toDegrees(angular)
        val deltaLon = if (abs(latitude) + angular >= Math.PI / 2) 180.0
            else Math.toDegrees(asin((sin(angular) / cos(latitude)).coerceIn(-1.0, 1.0)))
        return listOf((place.longitude - deltaLon).coerceAtLeast(-180.0), (place.latitude - deltaLat).coerceAtLeast(-90.0),
            (place.longitude + deltaLon).coerceAtMost(180.0), (place.latitude + deltaLat).coerceAtMost(90.0)).joinToString(",")
    }
}
