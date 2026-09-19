package fi.omasaasahko

import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DevicePlaceNameTest {
    private val fix = Place(60.27, 24.75, CURRENT_LOCATION_NAME, Instant.EPOCH, accuracyMeters = 10f, nameResolved = false, origin = PlaceOrigin.DEVICE)

    @Test fun `nearby anchored name avoids both network lookups without replacing fresh coordinates`() = runTest {
        val original = fix.copy(name = "Espoo", nearbyName = "Nikunmäki", nameResolved = true)
        val moved = fix.copy(latitude = fix.latitude + 0.0001, locatedAt = Instant.EPOCH.plusSeconds(60))
        val retained = retainDeviceName(moved, original)
        var requests = 0
        val result = DevicePlaceName({ requests++; emptyList() }, { requests++; null }, { true }).describe(retained)
        assertEquals(retained, result)
        assertEquals(moved.latitude, result.latitude, 0.0)
        assertEquals(moved.locatedAt, result.locatedAt)
        assertEquals(original.latitude, result.nameAnchor!!.latitude, 0.0)
        assertEquals(0, requests)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun `incomplete name is retried and revoked fine permission drops retained detail`() = runTest {
        val original = fix.copy(name = "Espoo", nearbyName = "Nikunmäki", nameResolved = true)
        var calls = 0
        val lookup: suspend (Place) -> List<NearbyName> = { calls++; listOf(NearbyName("Nikunmäki", "Espoo", 10.0)) }
        val incomplete = retainDeviceName(fix, original.copy(nearbyName = null))
        val recovered = DevicePlaceName(lookup, { null }, { true }).describe(incomplete)
        assertEquals("Nikunmäki", recovered.nearbyName)
        assertEquals(1, calls)
        val coarse = DevicePlaceName(lookup, { null }, { false }).describe(retainDeviceName(fix, original))
        assertEquals("Espoo", coarse.name)
        assertNull(coarse.nearbyName)
        assertEquals(2, calls)
    }

    @Test fun `retained names stay anchored to their original fix through successive small movements`() {
        val original = fix.copy(name = "Espoo", nearbyName = "Nikunmäki", nameResolved = true)
        val first = retainDeviceName(fix.copy(latitude = fix.latitude + 0.0003), original)
        assertTrue(first.nameResolved)
        val stored = first
        assertEquals(original.latitude, stored.nameAnchor!!.latitude, 0.0)
        val second = retainDeviceName(fix.copy(latitude = fix.latitude + 0.0006), stored)
        assertFalse("Two short steps must not carry the name beyond 50 metres", second.nameResolved)
        assertFalse(retainDeviceName(fix, original.copy(accuracyMeters = 500f)).nameResolved)
        assertFalse(retainDeviceName(fix, original.copy(accuracyMeters = null)).nameResolved)
    }

    @Test fun `production naming uses MML independently of Android district and skips duplicate candidates`() = runTest {
        val names = listOf(NearbyName("Espoo", "Espoo", 10.0), NearbyName("Across border", "Vantaa", 15.0), NearbyName("Nikunmäki", "Espoo", 20.0))
        for (android in listOf(AddressName("Espoo", "Juvanmalmi"), AddressName("Esbo", null))) {
            val result = DevicePlaceName({ names }, { android }, { true }).describe(fix)
            assertEquals("Espoo", result.name)
            assertEquals("Nikunmäki", result.nearbyName)
            assertEquals(fix.latitude, result.latitude, 0.0)
            assertTrue(result.nameResolved)
        }
    }

    @Test fun `coarse permission and coarse accuracy allow municipality but suppress precise point`() = runTest {
        val names = listOf(NearbyName("Brunnsparken", "Vantaa", 10.0))
        for ((fine, accuracy) in listOf(false to 10f, true to 201f)) {
            val result = DevicePlaceName({ names }, { null }, { fine }).describe(fix.copy(accuracyMeters = accuracy))
            assertEquals("Vantaa", result.name)
            assertNull(result.nearbyName)
        }
    }

    @Test fun `timeouts preserve Android fallback or explicit unresolved state and cancellation propagates`() = runTest {
        val slow: suspend (Place) -> List<NearbyName> = { delay(7_000); emptyList() }
        val named = DevicePlaceName(slow, { AddressName("Inari", null) }, { true }).describe(fix)
        assertEquals("Inari", named.name)
        val unresolved = DevicePlaceName(slow, { delay(5_000); null }, { true }).describe(fix)
        assertEquals(fix, unresolved)
        val job = async { DevicePlaceName(slow, { delay(5_000); null }, { true }).describe(fix) }
        runCurrent(); job.cancel(); runCurrent()
        assertTrue(job.isCancelled)
    }

    @Test fun `MML failure uses Android while MML success cancels slow Android lookup`() = runTest {
        val fallback = DevicePlaceName({ error("offline") }, { AddressName("Inari", null) }, { true }).describe(fix)
        assertEquals("Inari", fallback.name)
        var cancelled = false
        val result = DevicePlaceName({ delay(1); listOf(NearbyName("Nikunmäki", "Espoo", 10.0)) },
            { try { delay(5_000); null } finally { cancelled = true } }, { true }).describe(fix)
        assertEquals("Espoo", result.name)
        assertTrue(cancelled)
    }
}
