package fi.omasaasahko.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val HELSINKI: ZoneId = ZoneId.of("Europe/Helsinki")
val FINNISH: Locale = Locale.forLanguageTag("fi-FI")
private val clockFormat = DateTimeFormatter.ofPattern("HH.mm", FINNISH)
fun clockLabel(time: Instant, zone: ZoneId = HELSINKI): String {
    val z = time.atZone(zone)
    val ambiguous = zone.rules.getValidOffsets(z.toLocalDateTime()).size > 1
    return clockFormat.format(z) + if (ambiguous) " (${z.offset})" else ""
}
fun updatedLabel(time: Instant?): String = time?.atZone(HELSINKI)
    ?.format(DateTimeFormatter.ofPattern("d.M. HH.mm", FINNISH)) ?: "–"
fun decimal(value: Double?, places: Int = 1): String = value?.takeIf { it.isFinite() }
    ?.let { String.format(FINNISH, "%.${places}f", if (kotlin.math.abs(it) < 0.5 * Math.pow(10.0, -places.toDouble())) 0.0 else it) } ?: "–"
fun temperature(value: Double?): String = value?.let { decimal(it, 0) + "°" } ?: "–"

enum class WeatherSource(val title: String) { FMI("Ilmatieteen laitos"), OPEN_METEO("Open-Meteo") }
enum class Condition(val label: String) {
    CLEAR("Selkeää"), MOSTLY_CLEAR("Melkein selkeää"), PARTLY_CLOUDY("Puolipilvistä"),
    MOSTLY_CLOUDY("Melkein pilvistä"), CLOUDY("Pilvistä"),
    DRIZZLE("Tihkusadetta"), FREEZING_DRIZZLE("Jäätävää tihkua"), FREEZING_RAIN("Jäätävää sadetta"),
    RAIN("Vesisadetta"), SLEET("Räntää"), SNOW("Lumisadetta"),
    HAIL("Rakeita"), THUNDER("Ukkosta"), THUNDER_HAIL("Ukkosta ja rakeita"), FOG("Sumua"), UNKNOWN("Säätieto puuttuu")
}
const val CURRENT_LOCATION_NAME = "Nykyinen sijainti"
enum class PlaceOrigin { DEVICE, SELECTED, UNKNOWN }
data class Place(val latitude: Double, val longitude: Double, val name: String, val locatedAt: Instant,
                 val nearbyName: String? = null, val accuracyMeters: Float? = null,
                 val nearbyDistanceMeters: Double? = null, val origin: PlaceOrigin = PlaceOrigin.DEVICE,
                 val nameResolved: Boolean = true) {
    init { require(latitude.isFinite() && latitude in -90.0..90.0); require(longitude.isFinite() && longitude in -180.0..180.0) }
}
data class PlaceResult(val id: String, val name: String, val municipality: String, val latitude: Double,
                       val longitude: Double, val kind: String = "") {
    val label: String get() = listOf(name, municipality).filter(String::isNotBlank).distinct().joinToString(", ")
    fun place(at: Instant) = Place(latitude, longitude, label, at, origin = PlaceOrigin.SELECTED)
}
data class WeatherStation(val id: String, val name: String, val latitude: Double, val longitude: Double,
                          val distanceMeters: Double)
data class StationObservation(val station: WeatherStation, val weather: WeatherHour)

data class WeatherHour(
    val time: Instant, val temperature: Double? = null, val feelsLike: Double? = null,
    val wind: Double? = null, val windDirection: Double? = null, val rain: Double? = null,
    val rainProbability: Double? = null, val condition: Condition = Condition.UNKNOWN,
    val night: Boolean = false,
    val description: String = condition.label,
)
data class WeatherDay(
    val date: LocalDate, val low: Double?, val high: Double?, val rain: Double?,
    val probability: Double?, val wind: Double?, val condition: Condition,
    val sunrise: Instant? = null, val sunset: Instant? = null, val uvMax: Double? = null,
    val complete: Boolean = true,
    val description: String = condition.label,
)
data class Forecast(
    val source: WeatherSource, val place: Place, val fetchedAt: Instant,
    val hours: List<WeatherHour>, val days: List<WeatherDay>,
    val observation: WeatherHour? = null,
    val observationStation: WeatherStation? = null,
    val zone: ZoneId = HELSINKI,
) {
    fun current(now: Instant): WeatherHour? = hours.minByOrNull { kotlin.math.abs(Duration.between(it.time, now).seconds) }
        ?.takeIf { kotlin.math.abs(Duration.between(it.time, now).seconds) <= 3600 }
    fun recentObservation(now: Instant): WeatherHour? = observation?.takeIf {
        !it.time.isAfter(now) && Duration.between(it.time, now).toMinutes() <= 90
    }
}

fun dailyForecast(hours: List<WeatherHour>, zone: ZoneId): List<WeatherDay> {
    val byTime = hours.associateBy { it.time }
    return hours.groupBy { it.time.atZone(zone).toLocalDate() }.toSortedMap().map { (date, rows) ->
        val expected = Duration.between(date.atStartOfDay(zone), date.plusDays(1).atStartOfDay(zone)).toHours().toInt()
        val start = date.atStartOfDay(zone).toInstant()
        val dayHours = (0 until expected).map { byTime[start.plusSeconds(it * 3600L)] }
        val complete = dayHours.all { it?.temperature != null }
        // Rain at 00:00 belongs to 23:00–00:00 of the preceding day.
        val rainHours = (1..expected).map { byTime[start.plusSeconds(it * 3600L)]?.rain }
        val temperatures = rows.mapNotNull { it.temperature }
        val symbol = rows.minByOrNull { kotlin.math.abs(it.time.atZone(zone).hour - 12) }
        WeatherDay(date, temperatures.minOrNull(), temperatures.maxOrNull(),
            if (rainHours.all { it != null }) rainHours.sumOf { it!! } else null,
            if (complete) rows.mapNotNull { it.rainProbability }.maxOrNull() else null,
            rows.mapNotNull { it.wind }.maxOrNull(),
            symbol?.condition ?: Condition.UNKNOWN,
            complete = complete && rainHours.all { it != null },
            description = symbol?.description ?: Condition.UNKNOWN.label)
    }
}

data class QuarterPrice(val start: Instant, val euroPerMwh: BigDecimal)
data class PriceSlot(val start: Instant, val end: Instant, val centsPerKwh: BigDecimal?) {
    fun contains(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(end)
}
data class PriceData(val fetchedAt: Instant, val quarters: List<QuarterPrice>)
enum class Resolution(val label: String) { QUARTER("Vartti"), HOUR("Tunti") }

object Prices {
    private val vat = BigDecimal("1.255")
    fun withVat(euroPerMwh: BigDecimal): BigDecimal = euroPerMwh.movePointLeft(1).multiply(vat)
    fun format(value: BigDecimal?): String = value?.setScale(3, RoundingMode.HALF_UP)?.toPlainString()?.replace('.', ',') ?: "–"
    fun slots(data: List<QuarterPrice>, date: LocalDate, resolution: Resolution, includeVat: Boolean = true): List<PriceSlot> {
        val start = date.atStartOfDay(HELSINKI).toInstant()
        val end = date.plusDays(1).atStartOfDay(HELSINKI).toInstant()
        // Conflicting duplicates cannot silently replace a market interval.
        val byTime = data.groupBy { it.start }.mapValues { (_, values) ->
            values.map { it.euroPerMwh.stripTrailingZeros() }.distinct().singleOrNull()
        }
        val step = if (resolution == Resolution.QUARTER) 900L else 3600L
        return generateSequence(start) { it.plusSeconds(step) }.takeWhile { it < end }.map { t ->
            val parts = (0 until (step / 900).toInt()).map { byTime[t.plusSeconds(it * 900L)] }
            val average = if (parts.all { it != null })
                parts.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(parts.size)) else null
            PriceSlot(t, t.plusSeconds(step), average?.let { if (includeVat) withVat(it) else it.movePointLeft(1) })
        }.toList()
    }
    fun average(slots: List<PriceSlot>): BigDecimal? {
        if (slots.isEmpty() || slots.any { it.centsPerKwh == null }) return null
        return slots.fold(BigDecimal.ZERO) { acc, s -> acc + s.centsPerKwh!! }
            .divide(BigDecimal(slots.size), 12, RoundingMode.HALF_UP)
    }
}
