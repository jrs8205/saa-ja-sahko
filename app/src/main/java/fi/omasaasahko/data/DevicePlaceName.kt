package fi.omasaasahko.data

import fi.omasaasahko.domain.*
import kotlinx.coroutines.*

/** Only retain a known device label for a small, accurately measured movement. */
internal fun retainDeviceName(fix: Place, previous: Place?): Place {
    val anchor = previous?.nameAnchor ?: previous?.accuracyMeters?.takeIf { it.isFinite() && it >= 0 }?.let { NameAnchor(previous.latitude, previous.longitude, it) }
    if (fix.nameResolved || previous == null || !previous.nameResolved ||
        previous.origin != PlaceOrigin.DEVICE || fix.origin != PlaceOrigin.DEVICE ||
        anchor == null || anchor.accuracyMeters > 200 ||
        fix.accuracyMeters == null || !fix.accuracyMeters.isFinite() || fix.accuracyMeters !in 0f..200f ||
        distanceMeters(fix.latitude, fix.longitude, anchor.latitude, anchor.longitude) > 50) return fix
    return fix.copy(name = previous.name, nearbyName = previous.nearbyName,
        nearbyDistanceMeters = previous.nearbyDistanceMeters, nameResolved = true, nameAnchor = anchor)
}

/** This is the naming path used by DeviceLocation; dependencies let tests drive timeouts and races. */
internal class DevicePlaceName(
    private val reverse: suspend (Place) -> List<NearbyName>,
    private val address: suspend (Place) -> AddressName?,
    private val finePermission: () -> Boolean,
) {
    suspend fun describe(place: Place): Place = coroutineScope {
        // AppViewModel carries the name only after checking the fresh fix against its
        // original 50 m anchor. Retry incomplete names so a past outage can recover.
        if (place.nameResolved && place.nearbyName != null && place.nameAnchor != null && finePermission()) {
            return@coroutineScope place
        }
        val anchor = place.accuracyMeters?.let { NameAnchor(place.latitude, place.longitude, it) }
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
            place.copy(name = city, nearbyName = detail?.name, nearbyDistanceMeters = detail?.distanceMeters, nameResolved = true, nameAnchor = anchor)
        } else {
            android.await()?.let { place.copy(name = it.label, nearbyName = null, nearbyDistanceMeters = null, nameResolved = true, nameAnchor = anchor) } ?: place
        }
    }
}

internal class LocationFailure(val reason: AppMessage) : IllegalStateException(reason.name)
