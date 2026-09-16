package fi.omasaasahko.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.resume

interface LocationProvider { suspend fun locate(): Place }

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
        val location = try {
            withTimeoutOrNull(20_000) {
                var result: Location? = null
                repeat(providers.size) { if (result == null) result = replies.receive() }
                result
            }
        } finally { jobs.forEach { it.cancel() }; replies.close() }
        checkNotNull(location) { "Sijaintia ei saatu. Yritä uudelleen." }
        val label = withTimeoutOrNull(4_000) { placeName(location) } ?: "Nykyinen sijainti"
        Place(location.latitude, location.longitude, label, java.time.Instant.now())
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun current(manager: LocationManager, provider: String): Location? = suspendCancellableCoroutine { c ->
        val signal = CancellationSignal()
        c.invokeOnCancellation { signal.cancel() }
        manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
            if (c.isActive) c.resume(location)
        }
    }

    private suspend fun placeName(location: Location): String? = suspendCancellableCoroutine { c ->
        if (!Geocoder.isPresent()) { c.resume(null); return@suspendCancellableCoroutine }
        try {
            Geocoder(context, FINNISH).getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<android.location.Address>) {
                    val a = addresses.firstOrNull()
                    val name = listOfNotNull(a?.subLocality, a?.locality ?: a?.subAdminArea).distinct().joinToString(", ").ifBlank { null }
                    if (c.isActive) c.resume(name)
                }
                override fun onError(errorMessage: String?) { if (c.isActive) c.resume(null) }
            })
        } catch (_: Exception) { if (c.isActive) c.resume(null) }
    }
}
