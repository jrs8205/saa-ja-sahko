package fi.omasaasahko.domain

import java.security.MessageDigest
import java.time.Instant
import kotlin.math.abs

enum class WarningLevel(val rank: Int) { YELLOW(1), ORANGE(2), RED(3) }
data class GeoPoint(val latitude: Double, val longitude: Double)
data class WarningArea(val name: String, val polygons: List<List<GeoPoint>>) {
    fun contains(place: Place): Boolean = polygons.any { polygonContains(it, place.latitude, place.longitude) }
}

/** CAP polygons use latitude,longitude; points on the boundary belong to the warning. */
fun polygonContains(points: List<GeoPoint>, latitude: Double, longitude: Double): Boolean {
    if (points.size < 4) return false
    var inside = false
    for (i in 0 until points.lastIndex) {
        val a = points[i]; val b = points[i + 1]
        val cross = (longitude - a.longitude) * (b.latitude - a.latitude) -
            (latitude - a.latitude) * (b.longitude - a.longitude)
        if (abs(cross) < 1e-10 && latitude >= minOf(a.latitude, b.latitude) - 1e-10 &&
            latitude <= maxOf(a.latitude, b.latitude) + 1e-10 &&
            longitude >= minOf(a.longitude, b.longitude) - 1e-10 && longitude <= maxOf(a.longitude, b.longitude) + 1e-10) return true
        if ((a.latitude > latitude) != (b.latitude > latitude) &&
            longitude < (b.longitude - a.longitude) * (latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude) inside = !inside
    }
    return inside
}

data class WeatherWarning(
    val id: String, val eventCode: String, val event: String, val level: WarningLevel,
    val onset: Instant, val expires: Instant, val description: String, val instruction: String,
    val areas: List<WarningArea>, val sent: Instant,
) {
    fun localAreas(place: Place): List<WarningArea> = areas.filter { it.contains(place) }
    fun fingerprint(place: Place): String {
        // Publication IDs change even when the warning content does not.
        val value = listOf(eventCode, event, level.name, onset.toString(), expires.toString(), description, instruction,
            localAreas(place).map { it.name }.sorted().joinToString("\n")).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
data class WarningSnapshot(val fetchedAt: Instant, val publishedAt: Instant, val warnings: List<WeatherWarning>, val partial: Boolean = false) {
    fun local(place: Place?, now: Instant): List<WeatherWarning> = if (place == null) emptyList() else warnings
        .filter { it.expires > now && it.localAreas(place).isNotEmpty() }
        .sortedWith(compareByDescending<WeatherWarning> { it.level.rank }.thenBy { it.onset })
}
