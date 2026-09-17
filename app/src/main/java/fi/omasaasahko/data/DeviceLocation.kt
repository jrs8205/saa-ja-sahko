package fi.omasaasahko.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.resume

interface LocationProvider {
    suspend fun locate(): Place
    suspend fun describe(place: Place): Place = place
}

class DeviceLocation(private val context: Context) : LocationProvider {
    fun permitted(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun locate(): Place = coroutineScope {
        check(permitted()) { "Salli sijainti, jotta näet lähialueesi sään." }
        val manager = context.getSystemService(LocationManager::class.java)
        check(manager.isLocationEnabled) { "Ota puhelimen sijainti käyttöön." }
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        check(providers.isNotEmpty()) { "Paikannus ei ole käytettävissä." }
        val replies = Channel<Location?>(providers.size)
        val jobs = providers.map { provider -> launch {
            val location = try { current(manager, provider) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            replies.send(location)
        } }
        var location: Location? = null
        try {
            withTimeoutOrNull(12_000) {
                repeat(providers.size) {
                    val fix = replies.receive()?.takeIf { usableFix(it, SystemClock.elapsedRealtimeNanos()) }
                    if (fix != null && (location == null || fix.accuracy < location!!.accuracy)) location = fix
                    if (location != null && location.accuracy <= 50f) return@withTimeoutOrNull
                }
            }
        } finally { jobs.forEach { it.cancel() }; replies.close() }
        val fix = checkNotNull(location) { "Tuoretta sijaintia ei saatu. Yritä uudelleen." }
        val ageMillis = ((SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000).coerceAtLeast(0)
        Place(fix.latitude, fix.longitude, "Nykyinen sijainti", java.time.Instant.now().minusMillis(ageMillis),
            accuracyMeters = fix.accuracy)
    }

    override suspend fun describe(place: Place): Place = coroutineScope {
        val android = async { withTimeoutOrNull(4_000) {
            placeName(Location("name").apply { latitude = place.latitude; longitude = place.longitude })
        } }
        val names = try {
            withTimeoutOrNull(6_000) { MmlPlaceNames().reverseCandidates(place.latitude, place.longitude, PlaceNames.FALLBACK_RADIUS_METERS) }.orEmpty()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        if (names.isNotEmpty()) {
            android.cancel()
            val city = names.first().municipality
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val detail = names.firstOrNull { it.municipality == city && !it.name.equals(city, true) }
                ?.takeIf { fine && place.accuracyMeters != null && place.accuracyMeters <= 200f }
            place.copy(name = city, nearbyName = detail?.name, nearbyDistanceMeters = detail?.distanceMeters)
        } else {
            place.copy(name = android.await()?.label ?: "Nykyinen sijainti")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun current(manager: LocationManager, provider: String): Location? = suspendCancellableCoroutine { c ->
        val signal = CancellationSignal()
        c.invokeOnCancellation { signal.cancel() }
        manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
            if (c.isActive) c.resume(location)
        }
    }

    private suspend fun placeName(location: Location): AddressName? = suspendCancellableCoroutine { c ->
        if (!Geocoder.isPresent()) { c.resume(null); return@suspendCancellableCoroutine }
        try {
            Geocoder(context, FINNISH).getFromLocation(location.latitude, location.longitude, 5, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<android.location.Address>) {
                    val name = addressName(addresses)
                    if (c.isActive) c.resume(name)
                }
                override fun onError(errorMessage: String?) { if (c.isActive) c.resume(null) }
            })
        } catch (_: Exception) { if (c.isActive) c.resume(null) }
    }
}

internal fun usableFix(location: Location, nowNanos: Long): Boolean = location.hasAccuracy() &&
    location.accuracy.isFinite() && location.accuracy >= 0 &&
    location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
    location.longitude.isFinite() && location.longitude in -180.0..180.0 &&
    nowNanos - location.elapsedRealtimeNanos in 0L..30_000_000_000L
