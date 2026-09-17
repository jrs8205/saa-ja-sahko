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
    private val fix = Place(60.27, 24.75, CURRENT_LOCATION_NAME, Instant.EPOCH, accuracyMeters = 10f, nameResolved = false)

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
