package fi.omasaasahko.data

import android.location.Address
import fi.omasaasahko.BuildConfig
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*

internal data class AddressName(val municipality: String, val district: String?) {
    val label: String get() = listOfNotNull(district, municipality).distinctBy { it.lowercase() }.joinToString(", ")
}

internal fun addressName(addresses: List<Address>): AddressName? {
    // Never borrow a district from a different result, even if its municipality matches.
    val address = addresses.firstOrNull() ?: return null
    fun String?.name() = this?.trim()?.takeIf(String::isNotBlank)
    val city = address.locality.name() ?: address.subAdminArea.name() ?: address.subLocality.name() ?: return null
    return AddressName(city, address.subLocality.name()?.takeUnless { it.equals(city, true) })
}

internal data class NearbyName(val name: String, val municipality: String, val distanceMeters: Double)
internal data class NamedPoint(val place: PlaceResult, val category: Int, val group: Int)

internal fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val a = sin(Math.toRadians(lat2 - lat1) / 2).pow(2) + cos(Math.toRadians(lat1)) *
        cos(Math.toRadians(lat2)) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
    return 6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** MML points describe named features, not district boundaries. */
internal object PlaceNames {
    const val RADIUS_METERS = 750
    const val FALLBACK_RADIUS_METERS = 5_000
    fun points(body: String): List<NamedPoint> {
        val root = JSONObject(body)
        val status = root.optJSONObject("geocoding") ?: return emptyList()
        check(status.optString("status") == "success" && status.optJSONObject("sources")
            ?.optJSONObject("geographic-names")?.optString("status") == "success") { "Paikkahaku epäonnistui" }
        val features = root.optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val feature = features.optJSONObject(i) ?: return@mapNotNull null
            val p = feature.optJSONObject("properties") ?: return@mapNotNull null
            if (p.optString("source") != "geographic-names" || !p.isNull("placeDeletionTime")) return@mapNotNull null
            val municipality = p.optString("label:municipality").trim().takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
                ?: return@mapNotNull null
            val names = p.optJSONArray("name") ?: return@mapNotNull null
            val name = (0 until names.length()).mapNotNull { names.optJSONObject(it) }
                .filter { it.isNull("placeNameDeletionTime") && it.optString("spelling").isNotBlank() }
                .sortedWith(compareBy<JSONObject> { when (it.optString("language")) { "fin" -> 0; "swe" -> 1; else -> 2 } }
                    .thenBy { it.optInt("languageDominance", Int.MAX_VALUE) }.thenBy { it.optString("spelling") })
                .firstOrNull()?.optString("spelling")?.trim() ?: return@mapNotNull null
            val geometry = feature.optJSONObject("geometry") ?: return@mapNotNull null
            if (geometry.optString("type") != "Point") return@mapNotNull null
            val coords = geometry.optJSONArray("coordinates") ?: return@mapNotNull null
            val lon = coords.optDouble(0); val lat = coords.optDouble(1)
            if (!lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0) return@mapNotNull null
            val id = feature.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            NamedPoint(PlaceResult(id, name, municipality, lat, lon, p.optString("label:placeType")),
                p.optInt("placeTypeCategory"), p.optInt("placeTypeGroup"))
        }.distinctBy { it.place.id }
    }

    fun nearby(points: List<NamedPoint>, latitude: Double, longitude: Double, radius: Int = RADIUS_METERS): List<NearbyName> =
        points.asSequence().filter { it.category in 1..3 && it.group != 301 }
            .map { NearbyName(it.place.name, it.place.municipality,
                distanceMeters(latitude, longitude, it.place.latitude, it.place.longitude)) }
            .filter { it.distanceMeters <= radius }
            .sortedWith(compareBy<NearbyName> { it.distanceMeters }.thenBy { it.municipality }.thenBy { it.name }).toList()

    fun parse(body: String, latitude: Double, longitude: Double): NearbyName? =
        runCatching { nearby(points(body), latitude, longitude).firstOrNull() }.getOrNull()

    fun resolve(latitude: Double, longitude: Double, at: Instant, address: AddressName?, nearby: NearbyName?): Place =
        resolve(latitude, longitude, at, address, listOfNotNull(nearby))

    fun resolve(latitude: Double, longitude: Double, at: Instant, address: AddressName?, candidates: List<NearbyName>): Place {
        val label = address?.label ?: candidates.firstOrNull()?.municipality ?: "Nykyinen sijainti"
        val matching = candidates.firstOrNull { (address == null || it.municipality.equals(address.municipality, true)) &&
            label.split(',').none { part -> part.trim().equals(it.name, true) } }
        return Place(latitude, longitude, label, at, matching?.name)
    }

    fun searchResults(body: String, query: String): List<PlaceResult> = points(body)
        .filter { it.category in 1..3 || it.group == 401 }.sortedWith(
            compareBy<NamedPoint> { if (it.place.name.equals(query, true)) 0 else 1 }
                .thenBy { if (it.group == 401) 0 else if (it.category == 3) 1 else 2 }
                .thenBy { it.place.name }.thenBy { it.place.municipality }.thenBy { it.place.id })
        .map { it.place }.distinctBy { Triple(it.label, it.latitude, it.longitude) }.take(30)
}

interface PlaceSearch { suspend fun search(query: String): List<PlaceResult> }

class MmlPlaceNames(private val apiKey: String = BuildConfig.MML_API_KEY, private val suppliedClient: OkHttpClient? = null) : PlaceSearch {
    private fun url(operation: String) = "https://avoin-paikkatieto.maanmittauslaitos.fi/geocoding/v2/pelias/$operation".toHttpUrl().newBuilder()
        .addQueryParameter("sources", "geographic-names").addQueryParameter("lang", "fi").addQueryParameter("size", "100")

    override suspend fun search(query: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        val text = query.trim()
        require(text.length in 2..200) { "Kirjoita vähintään kaksi merkkiä." }
        check(apiKey.isNotBlank()) { "Paikkahaun MML-avain puuttuu sovelluksesta." }
        PlaceNames.searchResults(download(url("search").addQueryParameter("text", text).build()), text)
    }

    internal suspend fun reverse(latitude: Double, longitude: Double): NearbyName? = try {
        reverseCandidates(latitude, longitude, PlaceNames.RADIUS_METERS).firstOrNull()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    internal suspend fun reverseCandidates(latitude: Double, longitude: Double, radius: Int): List<NearbyName> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val body = download(url("reverse").addQueryParameter("point.lat", latitude.toString())
            .addQueryParameter("point.lon", longitude.toString()).addQueryParameter("boundary.circle.radius", radius.toString()).build())
        PlaceNames.nearby(PlaceNames.points(body), latitude, longitude, radius)
    }

    private suspend fun download(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
        val call = (suppliedClient ?: http).newCall(Request.Builder().url(url)
            .header("Authorization", Credentials.basic(apiKey.trim(), "")).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("Paikkapalveluun ei saatu yhteyttä.")) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    check(it.isSuccessful) { "Paikkapalvelu ei vastannut oikein (${it.code})." }
                    val bytes = it.body.byteStream().readNBytes(1_000_001)
                    require(bytes.size <= 1_000_000)
                    bytes.toString(Charsets.UTF_8)
                } }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
    companion object {
        private val http by lazy { OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build() }
    }
}
