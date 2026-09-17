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
    private val place = Place(60.27, 24.75, "Espoo", now, origin = PlaceOrigin.DEVICE)

    @Test fun `pending state has an owner uses no preference writes and ignores legacy disk flags`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("warnings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("locationPending", true).putLong("pendingSince", now.toEpochMilli()).commit()
        val before = prefs.all
        val first = WarningService(app, evaluationScope = backgroundScope)
        val second = WarningService(app, evaluationScope = backgroundScope)
        assertFalse(first.locationPending)
        first.beginLocationUpdate(); assertTrue(second.locationPending)
        second.beginLocationUpdate()
        first.endLocationUpdate(); assertTrue(second.locationPending)
        first.savePlace(place); assertNull(second.place())
        second.endLocationUpdate(); assertFalse(first.locationPending)
        first.endLocationUpdate(); second.endLocationUpdate()
        assertEquals(before, prefs.all)
    }

    @Test fun `place JSON stores origin and resolved state independently of the visible text`() {
        val renamed = place.copy(name = "Oma paikka", nameResolved = false, nameAnchor = NameAnchor(60.27, 24.75, 10f))
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
            val feed = object : FakeWarningFeed() {
                override suspend fun cached(): WarningSnapshot? = null
                override suspend fun refresh(): WarningSnapshot { fetches++; delay(100); return WarningSnapshot(now, now, emptyList()) }
                override suspend fun reevaluate(): Boolean { evaluations++; if (fail) throw SecurityException("revoked"); return true }
            }
            val model = WarningsViewModel(app, feed)
            model.start(); runCurrent(); model.place(place); runCurrent()
            advanceTimeBy(101); runCurrent()
            model.place(place.copy(name = "Espoo, Nikunmäki")); runCurrent()
            assertEquals(1, fetches)
            assertEquals(2, evaluations)
            fail = true; model.place(place); runCurrent()
            assertNotNull(model.state.value.error)
            fail = false; model.place(place); runCurrent()
            assertNull(model.state.value.error)
            advanceTimeBy(15 * 60_000L); runCurrent()
            assertEquals(2, fetches)
            model.stop(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `stale feed retries after location completes and all controls use only the injected backend`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            var fetches = 0; var fresh = false; var interval = 0; var enabledCalls = 0; var tests = 0
            val feed = object : FakeWarningFeed() {
                override val enabled = true
                override suspend fun refresh(): WarningSnapshot {
                    fetches++; if (fetches == 1) error("offline")
                    delay(100); fresh = true; return WarningSnapshot(now, now, emptyList())
                }
                override suspend fun reevaluate() = fresh
                override fun setEnabled(value: Boolean) { enabledCalls++ }
                override fun interval(minutes: Int) { interval = minutes }
                override fun testNotification() { tests++ }
            }
            val model = WarningsViewModel(app, feed)
            model.start(); runCurrent(); assertNotNull(model.state.value.error)
            model.place(place); model.place(place); runCurrent()
            assertEquals(2, fetches)
            advanceTimeBy(101); runCurrent(); assertNull(model.state.value.error)
            model.place(place); runCurrent(); assertEquals(2, fetches)
            model.interval(60); model.enable(false); model.test()
            advanceTimeBy(10_001); runCurrent()
            assertEquals(60, interval); assertEquals(2, enabledCalls); assertEquals(1, tests)
            assertTrue(app.getSharedPreferences("warnings", Context.MODE_PRIVATE).all.isEmpty())
            model.stop()
        } finally { Dispatchers.resetMain() }
    }
}

internal open class FakeWarningFeed : WarningFeed {
    override val enabled = false
    override val intervalMinutes = 30
    override fun allowed() = true
    override fun setEnabled(value: Boolean) {}
    override fun interval(minutes: Int) {}
    override fun testNotification() {}
    override suspend fun cached(): WarningSnapshot? = null
    override suspend fun refresh() = WarningSnapshot(Instant.EPOCH, Instant.EPOCH, emptyList())
    override suspend fun reevaluate() = true
}
