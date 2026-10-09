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
        if (!permitted()) throw LocationFailure(AppMessage.LOCATION_PERMISSION_MISSING)
        val manager = context.getSystemService(LocationManager::class.java)
        if (!manager.isLocationEnabled) throw LocationFailure(AppMessage.LOCATION_DISABLED)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        if (providers.isEmpty()) throw LocationFailure(AppMessage.LOCATION_UNAVAILABLE)
        val replies = Channel<Location?>(providers.size)
        val jobs = providers.map { provider -> launch {
            val location = try { current(manager, provider) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            replies.send(location)
        } }
        var location: Location? = null
        var received = 0
        suspend fun receiveFix() {
            val fix = replies.receive()?.takeIf { usableFix(it, SystemClock.elapsedRealtimeNanos()) }
            received++
            if (fix != null && (location == null || fix.accuracy < location!!.accuracy)) location = fix
        }
        try {
            withTimeoutOrNull(12_000) {
                while (location == null && received < providers.size) receiveFix()
                // Weather and nearby names already accept 200 m accuracy. Indoor GPS
                // must not hold a usable network fix for the full acquisition timeout.
                withTimeoutOrNull(2_000) {
                    while (received < providers.size && location!!.accuracy > 200f) receiveFix()
                }
            }
        } finally { jobs.forEach { it.cancel() }; replies.close() }
        // Freshness was checked on receipt. Optional refinement must not invalidate
        // an accepted fix; retain its original timestamp below, including the wait.
        val fix = location
            ?: throw LocationFailure(AppMessage.LOCATION_STALE)
        val ageMillis = ((SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000).coerceAtLeast(0)
        Place(fix.latitude, fix.longitude, CURRENT_LOCATION_NAME, java.time.Instant.now().minusMillis(ageMillis),
            accuracyMeters = fix.accuracy, origin = PlaceOrigin.DEVICE, nameResolved = false)
    }

    override suspend fun describe(place: Place): Place = DevicePlaceName(
        reverse = { MmlPlaceNames().reverseCandidates(it.latitude, it.longitude, PlaceNames.FALLBACK_RADIUS_METERS) },
        address = { placeName(Location("name").apply { latitude = it.latitude; longitude = it.longitude }) },
        finePermission = { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED },
    ).describe(place)

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
            Geocoder(context, FINNISH).getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
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
