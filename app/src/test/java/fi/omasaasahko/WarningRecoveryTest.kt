package fi.omasaasahko

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class WarningRecoveryTest {
    private val now = Instant.parse("2026-09-17T12:00:00Z")
    private val place = Place(60.27, 24.75, "Espoo", now)

    @Test fun `pending lease expires after process restart and handles legacy flag and clock rollback`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        var time = now
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: ZoneId) = this
            override fun instant() = time
        }
        val service = WarningService(app, clock)
        service.savePlace(place); service.beginLocationUpdate()
        assertTrue(WarningService(app, clock).locationPending)
        time = time.plusMillis(WarningService.LOCATION_PENDING_TTL_MS)
        val restarted = WarningService(app, clock)
        assertFalse(restarted.locationPending)
        assertEquals(place, restarted.place())
        restarted.beginLocationUpdate(); time = time.minusSeconds(1)
        assertFalse(restarted.locationPending)
        app.getSharedPreferences("warnings", Context.MODE_PRIVATE).edit().putBoolean("locationPending", true).remove("pendingSince").commit()
        assertFalse(WarningService(app, clock).locationPending)
    }

    @Test fun `place JSON stores origin and resolved state independently of the visible text`() {
        val renamed = place.copy(name = "Oma paikka", nameResolved = false)
        assertEquals(renamed, placeFromJson(renamed.toJson()))
        val selected = renamed.copy(origin = PlaceOrigin.SELECTED)
        assertEquals(selected, placeFromJson(selected.toJson()))
        val legacy = JSONObject(place.toJson().toString()).apply { remove("origin"); remove("nameResolved") }
        assertEquals(PlaceOrigin.UNKNOWN, placeFromJson(legacy).origin)
        assertEquals(PlaceOrigin.DEVICE, placeFromJson(legacy, PlaceOrigin.DEVICE).origin)
    }

    @Test fun `place completion reuses CAP without a second fetch and handles notification errors`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            var fetches = 0; var evaluations = 0; var fail = false
            val feed = object : WarningFeed {
                override suspend fun cached(): WarningSnapshot? = null
                override suspend fun refresh(): WarningSnapshot { fetches++; delay(100); return WarningSnapshot(now, now, emptyList()) }
                override suspend fun reevaluate() { evaluations++; if (fail) throw SecurityException("revoked") }
            }
            val model = WarningsViewModel(app, feed)
            model.start(); runCurrent(); model.place(place); runCurrent()
            advanceTimeBy(101); runCurrent()
            model.place(place.copy(name = "Espoo, Nikunmäki")); runCurrent()
            assertEquals(1, fetches)
            assertEquals(2, evaluations)
            fail = true; model.place(place); runCurrent()
            assertNotNull(model.state.value.error)
            advanceTimeBy(15 * 60_000L); runCurrent()
            assertEquals(2, fetches)
            model.stop(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }
}
