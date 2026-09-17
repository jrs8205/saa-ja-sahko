package fi.omasaasahko.data

import fi.omasaasahko.domain.*
import kotlinx.coroutines.*

/** Only retain a known device label for a small, accurately measured movement. */
internal fun retainDeviceName(fix: Place, previous: Place?): Place {
    if (fix.nameResolved || previous == null || !previous.nameResolved ||
        previous.origin != PlaceOrigin.DEVICE || fix.origin != PlaceOrigin.DEVICE ||
        fix.accuracyMeters == null || fix.accuracyMeters > 200 ||
        distanceMeters(fix.latitude, fix.longitude, previous.latitude, previous.longitude) > 50) return fix
    return fix.copy(name = previous.name, nearbyName = previous.nearbyName,
        nearbyDistanceMeters = previous.nearbyDistanceMeters, nameResolved = true)
}

/** This is the naming path used by DeviceLocation; dependencies let tests drive timeouts and races. */
internal class DevicePlaceName(
    private val reverse: suspend (Place) -> List<NearbyName>,
    private val address: suspend (Place) -> AddressName?,
    private val finePermission: () -> Boolean,
) {
    suspend fun describe(place: Place): Place = coroutineScope {
        val android = async {
            try { withTimeoutOrNull(4_000) { address(place) } }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
        val names = try { withTimeoutOrNull(6_000) { reverse(place) }.orEmpty() }
            catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        if (names.isNotEmpty()) {
            android.cancel()
            val city = names.first().municipality
            val detail = names.firstOrNull { it.municipality == city && !it.name.equals(city, true) }
                ?.takeIf { finePermission() && place.accuracyMeters != null && place.accuracyMeters <= 200f }
            place.copy(name = city, nearbyName = detail?.name, nearbyDistanceMeters = detail?.distanceMeters, nameResolved = true)
        } else {
            android.await()?.let { place.copy(name = it.label, nearbyName = null, nearbyDistanceMeters = null, nameResolved = true) } ?: place
        }
    }
}

internal class LocationFailure(message: String) : IllegalStateException(message)
