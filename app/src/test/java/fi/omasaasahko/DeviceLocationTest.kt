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
    private fun fix(provider: String, accuracy: Float) = Location(provider).apply {
        latitude = 60.27; longitude = 24.75; this.accuracy = accuracy
        time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
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
