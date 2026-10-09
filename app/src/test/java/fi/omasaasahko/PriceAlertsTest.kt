package fi.omasaasahko

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
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
    @Test fun `a background check waiting for another request rechecks the time window before downloading`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("price-alerts", Context.MODE_PRIVATE).edit().clear().putBoolean("enabled", true).commit()
        val day = LocalDate.of(2026, 9, 17)
        var time = day.atTime(15, 59).atZone(HELSINKI).toInstant()
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: ZoneId) = this
            override fun instant() = time
        }
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val manual = async { PriceAlerts(app).check(clock) { entered.complete(Unit); release.await(); PriceData(time, emptyList()) } }
        entered.await()
        var backgroundRequests = 0
        val worker = async { PriceAlerts(app).checkBackground(clock) { backgroundRequests++; data(day.plusDays(1)) } }
        yield()
        time = day.atTime(16, 0).atZone(HELSINKI).toInstant()
        release.complete(Unit); manual.await(); worker.await()
        assertEquals(0, backgroundRequests)
    }
    @Test fun `concurrent notification checks share the completion guard before downloading again`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("price-alerts", Context.MODE_PRIVATE).edit().clear().putBoolean("enabled", true).commit()
        val day = LocalDate.of(2026, 9, 17)
        val clock = Clock.fixed(day.atTime(14, 30).atZone(HELSINKI).toInstant(), ZoneOffset.UTC)
        var requests = 0
        listOf(PriceAlerts(app), PriceAlerts(app)).map { service -> async {
            service.check(clock) { requests++; delay(20); data(day.plusDays(1)) }
        } }.awaitAll()
        assertEquals(1, requests)
        assertEquals(1, app.getSystemService(NotificationManager::class.java).activeNotifications.size)
    }
    @Test fun `next background run sleeps until Finnish afternoon across daylight saving transitions`() {
        for (date in listOf("2026-03-28", "2026-10-24", "2026-09-17")) {
            val day = LocalDate.parse(date)
            fun at(hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(HELSINKI).toInstant()
            val nextAfternoon = day.plusDays(1).atTime(14, 0).atZone(HELSINKI).toInstant()
            assertEquals(at(14), nextPriceCheck(at(2), needed = true))
            assertEquals(at(14), nextPriceCheck(at(9), needed = false))
            assertEquals(at(14), nextPriceCheck(at(13, 59), needed = true, afterAttempt = true))
            assertEquals(at(14), nextPriceCheck(at(13, 45), needed = false, afterAttempt = true))
            assertEquals(at(14, 15), nextPriceCheck(at(14), needed = true, afterAttempt = true))
            assertEquals(nextAfternoon, nextPriceCheck(at(15, 50), needed = true, afterAttempt = true))
            assertEquals(nextAfternoon, nextPriceCheck(at(18), needed = true))
            assertEquals(nextAfternoon, nextPriceCheck(at(14, 30), needed = false))
        }
    }

    @Test fun `enabling price notifications can fetch and notify at eighteen`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("price-alerts", Context.MODE_PRIVATE).edit().clear().putBoolean("enabled", true).commit()
        val day = LocalDate.of(2026, 9, 17)
        val now = day.atTime(18, 0).atZone(HELSINKI).toInstant()
        var requests = 0
        PriceAlerts(app).check(Clock.fixed(now, ZoneOffset.UTC)) { requests++; data(day.plusDays(1)) }
        assertEquals(1, requests)
        assertEquals(1, app.getSystemService(NotificationManager::class.java).activeNotifications.size)
    }

    @Test fun `background network requests stay inside Finnish afternoon in winter summer and DST transitions`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("price-alerts", Context.MODE_PRIVATE).edit().clear().putBoolean("enabled", true).commit()
        val service = PriceAlerts(app)
        val requests = mutableListOf<Instant>()
        listOf("2026-01-15", "2026-03-29", "2026-07-15", "2026-10-25").forEach { date ->
            listOf("00:00", "08:00", "13:59:59", "14:00", "15:59:59", "16:00", "23:59:59").forEach { time ->
                val local = LocalDate.parse(date).atTime(LocalTime.parse(time))
                val now = local.atZone(HELSINKI).toInstant()
                val before = requests.size
                // The injected clock's zone is deliberately foreign; Finland always governs the window.
                service.checkBackground(Clock.fixed(now, ZoneId.of("America/New_York"))) { at ->
                    requests += at
                    PriceData(at, emptyList())
                }
                assertEquals("$date $time", if (local.hour in 14..15) 1 else 0, requests.size - before)
                if (local.hour in 14..15) assertEquals(now, requests.last())
            }
        }
    }

    @Test fun `complete publication stops background fetching until the next Finnish afternoon`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("price-alerts", Context.MODE_PRIVATE).edit().clear().putBoolean("enabled", true).commit()
        val service = PriceAlerts(app)
        val today = LocalDate.of(2026, 9, 17)
        var requests = 0
        suspend fun check(day: LocalDate, hour: Int, minute: Int = 0) {
            val now = day.atTime(hour, minute).atZone(HELSINKI).toInstant()
            service.checkBackground(Clock.fixed(now, ZoneOffset.UTC)) { requests++; data(day.plusDays(1)) }
        }
        check(today, 14); assertEquals(1, requests)
        check(today, 14, 15); check(today, 15, 45); check(today, 16)
        check(today.plusDays(1), 0); check(today.plusDays(1), 13, 59)
        assertEquals(1, requests)
        check(today.plusDays(1), 14); assertEquals(2, requests)
    }

    @Test fun `foreground prices can still notify after the background window`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("price-alerts", Context.MODE_PRIVATE).edit().clear().putBoolean("enabled", true).commit()
        val today = LocalDate.of(2026, 9, 17)
        PriceAlerts(app).consider(data(today.plusDays(1)), today.atTime(18, 0).atZone(HELSINKI).toInstant())
        assertEquals(1, app.getSystemService(NotificationManager::class.java).activeNotifications.size)
    }

    private fun data(date: LocalDate): PriceData {
        val from=date.atStartOfDay(HELSINKI).toInstant(); val to=date.plusDays(1).atStartOfDay(HELSINKI).toInstant()
        return PriceData(from.minusSeconds(3600),generateSequence(from) { it.plusSeconds(900) }.takeWhile { it<to }
            .map { QuarterPrice(it,BigDecimal("40.123")) }.toList())
    }
    @Test fun `only complete Finnish delivery days notify including both daylight saving changes`() {
        listOf("2026-09-17","2026-03-29","2026-10-25").forEach { date ->
            val day=LocalDate.parse(date);val prices=data(day);val now=day.minusDays(1).atTime(15,0).atZone(HELSINKI).toInstant()
            val text=tomorrowPriceMessage(prices,now,true,AppLanguage.FI)!!
            assertTrue(text.contains("5,035 snt/kWh"));assertTrue(text.contains("ALV 25,5 %"))
            assertTrue(tomorrowPriceMessage(prices,now,false,AppLanguage.FI)!!.contains("4,012 snt/kWh"))
            assertNull(tomorrowPriceMessage(prices.copy(quarters=prices.quarters.drop(1)),now,true,AppLanguage.FI))
            // Four midnight spillover quarters and one evening quarter are still not publication.
            assertNull(tomorrowPriceMessage(prices.copy(quarters=prices.quarters.take(4)+prices.quarters.last()),now,true,AppLanguage.FI))
            val conflict=prices.quarters+prices.quarters.first().copy(euroPerMwh=BigDecimal.TEN)
            assertNull(tomorrowPriceMessage(prices.copy(quarters=conflict),now,true,AppLanguage.FI))
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
        PriceAlerts(app).testNotification()
        val actual = manager.activeNotifications.single { it.tag == "tomorrow-prices" }.notification
        assertEquals("2026-09-17",shadowOf(actual.contentIntent).savedIntent.getStringExtra("priceDate"))
        val text=actual.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(text.contains("Keskihinta"));assertTrue(text.contains("Halvin tunti"));assertTrue(text.contains("Kallein tunti"))
        assertEquals(text,actual.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
        assertEquals(0,actual.flags and android.app.Notification.FLAG_LOCAL_ONLY)
        manager.cancelAll()
        PriceAlerts(app).consider(prices,now.plusSeconds(1800))
        assertEquals(0,manager.activeNotifications.size)
        assertEquals("2026-09-17",prefs.getString("lastDay",null))
    }
}
