package fi.omasaasahko.data

import android.util.Xml
import fi.omasaasahko.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object Parsers {
    fun prices(body: String, fetched: Instant): PriceData {
        val root = JSONObject(body)
        require(root.optBoolean("success", false)) { "Hintapalvelu ei palauttanut hintoja" }
        val array = root.getJSONObject("data").getJSONArray("fi")
        val rows = (0 until array.length()).mapNotNull { i ->
            val row = array.optJSONObject(i) ?: return@mapNotNull null
            val time = row.optLong("timestamp", -1)
            val price = row.opt("price")?.toString()?.toBigDecimalOrNull()
            if (time <= 0 || time % 900 != 0L || price == null) null else QuarterPrice(Instant.ofEpochSecond(time), price)
        }
        require(rows.isNotEmpty()) { "Hintatietoja ei ole saatavilla" }
        return PriceData(fetched, rows.sortedBy { it.start })
    }

    fun openMeteo(body: String, place: Place, fetched: Instant): Forecast {
        val root = JSONObject(body)
        require(!root.optBoolean("error", false)) { "Sääpalvelun haku epäonnistui" }
        val zone = ZoneId.of(root.getString("timezone"))
        val h = root.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val hours = (0 until times.length()).map { i ->
            val code = WeatherCodes.wmo(h.number("weather_code", i)?.toInt())
            WeatherHour(Instant.ofEpochSecond(times.getLong(i)), h.number("temperature_2m", i),
                h.number("apparent_temperature", i), h.number("wind_speed_10m", i),
                h.number("wind_direction_10m", i), h.number("precipitation", i),
                h.number("precipitation_probability", i), code.condition, h.number("is_day", i) == 0.0, code.description)
        }.distinctBy { it.time }.sortedBy { it.time }
        require(hours.any { it.temperature != null }) { "Ennuste puuttuu" }
        val d = root.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val days = (0 until dates.length()).map { i ->
            val code = WeatherCodes.wmo(d.number("weather_code", i)?.toInt())
            WeatherDay(Instant.ofEpochSecond(dates.getLong(i)).atZone(zone).toLocalDate(),
                d.number("temperature_2m_min", i), d.number("temperature_2m_max", i),
                d.number("precipitation_sum", i), d.number("precipitation_probability_max", i),
                d.number("wind_speed_10m_max", i), code.condition,
                d.epoch("sunrise", i), d.epoch("sunset", i), d.number("uv_index_max", i), description = code.description)
        }
        return Forecast(WeatherSource.OPEN_METEO, place, fetched, hours, days, zone = zone)
    }

    fun fmi(body: String, place: Place, fetched: Instant): Forecast {
        val sun = mutableMapOf<LocalDate, MutableMap<String, Instant>>()
        val hours = wfsRows(body, onSun = { parameter, event ->
            // WFS sun values use the UTC calendar day of the sample. Group by the
            // event's actual local date, not the weather sample's local date.
            sun.getOrPut(event.atZone(HELSINKI).toLocalDate()) { mutableMapOf() }[parameter] = event
        }).map { (time, p) ->
            val code = WeatherCodes.fmi(p["SmartSymbol"]?.toInt())
            WeatherHour(time, p["Temperature"], p["FeelsLike"], p["WindSpeedMS"], p["WindDirection"],
                p["Precipitation1h"], condition = code.condition, night = code.night, description = code.description)
        }.sortedBy { it.time }
        require(hours.any { it.temperature != null }) { "FMI:n ennustetta ei ole saatavilla tälle paikalle" }
        val days = dailyForecast(hours, HELSINKI).take(7).map { day ->
            day.copy(sunrise = sun[day.date]?.get("Sunrise"), sunset = sun[day.date]?.get("Sunset"))
        }
        return Forecast(WeatherSource.FMI, place, fetched, hours, days)
    }

    fun observation(body: String, place: Place): WeatherHour? = wfsRows(body, place).entries
        .filter { it.value["t2m"] != null }.maxByOrNull { it.key }?.let { (time, p) ->
            WeatherHour(time, temperature = p["t2m"], wind = p["ws_10min"], windDirection = p["wd_10min"])
        }

    private fun wfsRows(body: String, nearestTo: Place? = null,
                        onSun: (String, Instant) -> Unit = { _, _ -> }): Map<Instant, Map<String, Double>> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(body.reader())
        val stations = mutableMapOf<String, MutableMap<Instant, MutableMap<String, Double>>>()
        var time: Instant? = null
        var parameter: String? = null
        var value: Double? = null
        var rawValue: String? = null
        var station = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "BsWfsElement" -> { time = null; parameter = null; value = null; rawValue = null; station = "" }
                    "pos" -> station = parser.nextText().trim()
                    "Time" -> time = runCatching { Instant.parse(parser.nextText().trim()) }.getOrNull()
                    "ParameterName" -> parameter = parser.nextText().trim()
                    "ParameterValue" -> {
                        rawValue = parser.nextText().trim()
                        value = rawValue.toDoubleOrNull()?.takeIf(Double::isFinite)
                    }
                    "ExceptionReport" -> error("FMI:n haku epäonnistui")
                }
                XmlPullParser.END_TAG -> if (parser.name == "BsWfsElement") {
                    val t = time; val p = parameter; val v = value
                    if (t != null && p != null && v != null) stations.getOrPut(station) { mutableMapOf() }.getOrPut(t) { mutableMapOf() }[p] = v
                    if (t != null && (p == "Sunrise" || p == "Sunset")) {
                        rawValue?.let(::fmiSunTime)?.let { onSun(p, it) }
                    }
                }
            }
            parser.next()
        }
        if (nearestTo == null) return stations.values.firstOrNull().orEmpty()
        // A bbox can contain multiple stations. Never combine their simultaneous observations.
        return stations.entries.filter { (_, rows) -> rows.values.any { it["t2m"] != null } }.minByOrNull { (position, _) ->
            val coordinates = position.split(Regex("\\s+")).mapNotNull(String::toDoubleOrNull)
            if (coordinates.size < 2) Double.POSITIVE_INFINITY else {
                val dx = (coordinates[1] - nearestTo.longitude) * kotlin.math.cos(Math.toRadians(nearestTo.latitude))
                val dy = coordinates[0] - nearestTo.latitude
                dx * dx + dy * dy
            }
        }?.value.orEmpty()
    }

    private val fmiSunFormat = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(ResolverStyle.STRICT)
    private fun fmiSunTime(value: String): Instant? = runCatching {
        LocalDateTime.parse(value, fmiSunFormat).toInstant(ZoneOffset.UTC)
    }.getOrNull()

    fun smart(symbol: Int?): Condition = WeatherCodes.fmi(symbol).condition
    fun wmo(code: Int?): Condition = WeatherCodes.wmo(code).condition
    private fun JSONObject.number(key: String, index: Int): Double? = optJSONArray(key)?.let { a ->
        if (a.isNull(index)) null else a.optDouble(index, Double.NaN).takeIf(Double::isFinite)
    }
    private fun JSONObject.epoch(key: String, index: Int): Instant? = number(key, index)
        ?.takeIf { it > 0 }?.let { Instant.ofEpochSecond(it.toLong()) }
}
