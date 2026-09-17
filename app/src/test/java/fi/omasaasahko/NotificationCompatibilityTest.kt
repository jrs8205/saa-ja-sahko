package fi.omasaasahko

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class NotificationCompatibilityTest {
    @Test fun `both channel tests expose title and full text to Pebble without consuming real price alert`() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager=app.getSystemService(NotificationManager::class.java)
        val prefs=app.getSharedPreferences("price-alerts",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        WarningService(app).testNotification()
        PriceAlerts(app).testNotification()
        assertEquals(setOf(WarningService.CHANNEL,PriceAlerts.CHANNEL),manager.activeNotifications.map { it.notification.channelId }.toSet())
        manager.activeNotifications.forEach {
            val n=it.notification
            assertEquals(0,n.flags and (Notification.FLAG_LOCAL_ONLY or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY))
            assertTrue(n.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("testi"))
            val body=n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
            assertTrue(body.contains("Jos näet tämän kellossa"))
            assertEquals(body,n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            val intent=shadowOf(n.contentIntent).savedIntent
            assertTrue(intent.getBooleanExtra(if(n.channelId==WarningService.CHANNEL) "showWarnings" else "showPrices",false))
            assertFalse(intent.hasExtra("priceDate"))
        }
        assertNull(prefs.getString("lastDay",null))
    }

    @Test fun `disabled channels remain disabled after registration and block tests`() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager=app.getSystemService(NotificationManager::class.java)
        listOf(WarningService.CHANNEL,PriceAlerts.CHANNEL).forEach {
            manager.createNotificationChannel(NotificationChannel(it,"Disabled",NotificationManager.IMPORTANCE_NONE))
        }
        val weather=WarningService(app);val prices=PriceAlerts(app)
        weather.createChannel();prices.channel()
        assertFalse(weather.allowed());assertFalse(prices.allowed())
        weather.testNotification();prices.testNotification()
        assertTrue(manager.activeNotifications.isEmpty())
        listOf(WarningService.CHANNEL,PriceAlerts.CHANNEL).forEach {
            assertEquals(NotificationManager.IMPORTANCE_NONE,manager.getNotificationChannel(it).importance)
        }
    }
}
