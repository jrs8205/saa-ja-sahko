package fi.omasaasahko

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class PriceAlertsTest {
    private fun data(date: LocalDate): PriceData {
        val from=date.atStartOfDay(HELSINKI).toInstant(); val to=date.plusDays(1).atStartOfDay(HELSINKI).toInstant()
        return PriceData(from.minusSeconds(3600),generateSequence(from) { it.plusSeconds(900) }.takeWhile { it<to }
            .map { QuarterPrice(it,BigDecimal("40.123")) }.toList())
    }
    @Test fun `only complete Finnish delivery days notify including both daylight saving changes`() {
        listOf("2026-09-17","2026-03-29","2026-10-25").forEach { date ->
            val day=LocalDate.parse(date);val prices=data(day);val now=day.minusDays(1).atTime(15,0).atZone(HELSINKI).toInstant()
            val text=tomorrowPriceMessage(prices,now,true)!!
            assertTrue(text.contains("5,035 snt/kWh"));assertTrue(text.contains("ALV 25,5 %"))
            assertTrue(tomorrowPriceMessage(prices,now,false)!!.contains("4,012 snt/kWh"))
            assertNull(tomorrowPriceMessage(prices.copy(quarters=prices.quarters.drop(1)),now,true))
            // Four midnight spillover quarters and one evening quarter are still not publication.
            assertNull(tomorrowPriceMessage(prices.copy(quarters=prices.quarters.take(4)+prices.quarters.last()),now,true))
            val conflict=prices.quarters+prices.quarters.first().copy(euroPerMwh=BigDecimal.TEN)
            assertNull(tomorrowPriceMessage(prices.copy(quarters=conflict),now,true))
        }
    }
    @Test fun `permission denial does not consume date and notification is once per day across instances`() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val prefs=app.getSharedPreferences("price-alerts",Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("enabled",true).commit()
        val day=LocalDate.of(2026,9,17);val prices=data(day);val now=day.minusDays(1).atTime(15,0).atZone(HELSINKI).toInstant()
        val manager=app.getSystemService(NotificationManager::class.java)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        PriceAlerts(app).consider(prices,now)
        assertNull(prefs.getString("lastDay",null))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        PriceAlerts(app).consider(prices,now)
        assertEquals(1,manager.activeNotifications.size)
        val intent=shadowOf(manager.activeNotifications.single().notification.contentIntent).savedIntent
        assertEquals("2026-09-17",intent.getStringExtra("priceDate"))
        assertTrue(intent.getBooleanExtra("showPrices",false))
        manager.cancelAll()
        PriceAlerts(app).consider(prices,now.plusSeconds(1800))
        assertEquals(0,manager.activeNotifications.size)
        assertEquals("2026-09-17",prefs.getString("lastDay",null))
    }
}
