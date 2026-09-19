package fi.omasaasahko

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class DeviceLocationTest {
    @Test fun `accepted 29 second old coarse fix survives refinement with original timestamp`() = withProviders { scope, manager, locator ->
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(40))
        val result = scope.async { locator.locate() }
        shadowOf(Looper.getMainLooper()).idle()
        val cached = fix("network", 800f).apply { elapsedRealtimeNanos -= 29_000_000_000L }
        shadowOf(manager).simulateLocation(cached); shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_001))
        assertTrue(result.isCompleted)
        val place = runBlocking { result.await() }
        assertEquals(800f, place.accuracyMeters)
        assertTrue(Duration.between(place.locatedAt, java.time.Instant.now()).seconds >= 31)
    }

    private fun fix(provider: String, accuracy: Float) = Location(provider).apply {
        latitude = 60.27; longitude = 24.75; this.accuracy = accuracy
        time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }
    private fun withProviders(gps: Boolean = true, block: (CoroutineScope, LocationManager, DeviceLocation) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        val manager = app.getSystemService(LocationManager::class.java)
        val shadow = shadowOf(manager)
        shadow.setLocationEnabled(true); shadow.setProviderEnabled("network", true); shadow.setProviderEnabled("gps", gps)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try { block(scope, manager, DeviceLocation(app)) } finally { scope.cancel() }
    }

    @Test fun `usable wifi fix returns immediately without waiting for silent GPS`() = withProviders { scope, manager, locator ->
        val result = scope.async { locator.locate() }
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(manager).simulateLocation(fix("network", 100f)); shadowOf(Looper.getMainLooper()).idle()
        assertTrue("A 100 m network fix used to wait the full 12 seconds", result.isCompleted)
        assertEquals(100f, runBlocking { result.await() }.accuracyMeters)
        shadowOf(manager).simulateLocation(fix("gps", 8f)); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(100f, runBlocking { result.await() }.accuracyMeters)
    }

    @Test fun `200 metre boundary is accepted without extra delay`() = withProviders { scope, manager, locator ->
        val result = scope.async { locator.locate() }
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(manager).simulateLocation(fix("network", 200f)); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCompleted)
        assertEquals(200f, runBlocking { result.await() }.accuracyMeters)
    }

    @Test fun `coarse mobile fix waits at most two extra seconds for indoor GPS`() = withProviders { scope, manager, locator ->
        val result = scope.async { locator.locate() }
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(manager).simulateLocation(fix("network", 800f)); shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_999))
        assertFalse(result.isCompleted)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2))
        assertTrue(result.isCompleted)
        assertEquals(800f, runBlocking { result.await() }.accuracyMeters)
    }

    @Test fun `GPS arriving during the short refinement window still wins`() = withProviders { scope, manager, locator ->
        val result = scope.async { locator.locate() }
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(manager).simulateLocation(fix("network", 800f)); shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))
        assertFalse(result.isCompleted)
        shadowOf(manager).simulateLocation(fix("gps", 8f)); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCompleted)
        assertEquals(8f, runBlocking { result.await() }.accuracyMeters)
    }

    @Test fun `single provider returns coarse coordinates without a refinement delay`() = withProviders(gps = false) { scope, manager, locator ->
        val result = scope.async { locator.locate() }
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(manager).simulateLocation(fix("network", 800f)); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCompleted)
        assertEquals(800f, runBlocking { result.await() }.accuracyMeters)
    }

    @Test fun `first fix keeps full acquisition budget but refinement cannot extend it`() = withProviders { scope, manager, locator ->
        val result = scope.async { locator.locate() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertFalse(result.isCompleted)
        shadowOf(manager).simulateLocation(fix("network", 800f)); shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_001))
        assertTrue(result.isCompleted)
        assertEquals(800f, runBlocking { result.await() }.accuracyMeters)
    }

    @Test fun `no fix fails after acquisition timeout`() = withProviders { scope, _, locator ->
        val result = scope.async { runCatching { locator.locate() } }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        assertFalse(result.isCompleted)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_001))
        assertTrue(result.isCompleted)
        assertTrue(runBlocking { result.await() }.exceptionOrNull() is LocationFailure)
    }

    @Test fun `old precise reply cannot beat fresh network coordinates`() = withProviders { scope, manager, locator ->
        val result = scope.async { locator.locate() }
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(manager).simulateLocation(fix("gps", 5f).apply { elapsedRealtimeNanos -= 31_000_000_000L })
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(result.isCompleted)
        shadowOf(manager).simulateLocation(fix("network", 100f)); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCompleted)
        assertEquals(100f, runBlocking { result.await() }.accuracyMeters)
    }

    @Test fun `cancelling acquisition ignores later provider replies`() = withProviders { scope, manager, locator ->
        val result = scope.async { locator.locate() }
        shadowOf(Looper.getMainLooper()).idle()
        result.cancel(); shadowOf(Looper.getMainLooper()).idle()
        shadowOf(manager).simulateLocation(fix("network", 100f))
        shadowOf(manager).simulateLocation(fix("gps", 8f)); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCancelled)
    }

    @Test fun `coarse network reply waits for the more accurate GPS fix`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        val manager = app.getSystemService(LocationManager::class.java)
        val shadow = shadowOf(manager)
        shadow.setLocationEnabled(true); shadow.setProviderEnabled("network", true); shadow.setProviderEnabled("gps", true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val result = scope.async { DeviceLocation(app).locate() }
            shadowOf(Looper.getMainLooper()).idle()
            shadow.simulateLocation(fix("network", 800f)); shadowOf(Looper.getMainLooper()).idle()
            assertFalse(result.isCompleted)
            shadow.simulateLocation(fix("gps", 8f)); shadowOf(Looper.getMainLooper()).idle()
            assertTrue(result.isCompleted)
            assertEquals(8f, runBlocking { result.await() }.accuracyMeters)
        } finally { scope.cancel() }
    }
    @Test fun `fresh coarse fix survives GPS timeout but old cached coordinates are rejected`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        val manager = app.getSystemService(LocationManager::class.java); val shadow = shadowOf(manager)
        shadow.setLocationEnabled(true); shadow.setProviderEnabled("network", true); shadow.setProviderEnabled("gps", true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val result = scope.async { DeviceLocation(app).locate() }; shadowOf(Looper.getMainLooper()).idle()
            shadow.simulateLocation(fix("network", 800f)); shadowOf(Looper.getMainLooper()).idle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(13))
            assertTrue(result.isCompleted)
            assertEquals(800f, runBlocking { result.await() }.accuracyMeters)
            val old = fix("gps", 5f)
            assertFalse(usableFix(old, old.elapsedRealtimeNanos + 31_000_000_000L))
        } finally { scope.cancel() }
    }
}
