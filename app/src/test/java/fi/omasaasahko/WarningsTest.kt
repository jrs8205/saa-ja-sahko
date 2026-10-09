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
import java.time.Instant
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fi")
class WarningsTest {
    private val now = Instant.parse("2026-09-16T09:00:00Z")
    private val vantaa = Place(60.2925,25.0408,"Tikkurila, Vantaa",now, origin = PlaceOrigin.DEVICE)
    private val tampere = Place(61.5,23.76,"Tampere",now, origin = PlaceOrigin.DEVICE)
    private fun feed(vararg alerts: String) = """<feed xmlns="http://www.w3.org/2005/Atom"><updated>$now</updated>${alerts.joinToString("") { "<entry><content>$it</content></entry>" }}</feed>"""
    private fun alert(id: String = "a", type: String = "Alert", references: String = "", severity: String = "Moderate",
        onset: String = "2026-09-18T12:00:00+03:00", expires: String = "2026-09-19T00:00:00+03:00", status: String = "Actual",
        polygon: String = "60,24 61,24 61,26 60,26 60,24", area: String = "Testialue") = """
        <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2"><identifier>$id</identifier><sender>urn:oid:2.49.0.0.246.0</sender>
        <sent>$now</sent><status>$status</status><scope>Public</scope><msgType>$type</msgType><references>$references</references>
        <info><language>en-GB</language><event>Rain</event></info>
        <info><language>fi-FI</language><severity>$severity</severity><event>Sadevaroitus</event>
        <eventCode><valueName>profile:cap:https://alerts.fmi.fi/cap/profile/v1.1.0</valueName><value>rain</value></eventCode>
        <onset>$onset</onset><expires>$expires</expires><description>Rankkaa sadetta.</description>
        <area><areaDesc>$area</areaDesc><polygon>$polygon</polygon></area></info></alert>""".trimIndent()
    @Test fun `info block follows the app language and falls back to Finnish`() {
        val swedish = alert().replace("<info><language>fi-FI</language>", "<info><language>sv-FI</language><severity>Moderate</severity><event>Regnvarning</event><onset>2026-09-18T12:00:00+03:00</onset><expires>2026-09-19T00:00:00+03:00</expires><description>Kraftigt regn.</description><area><areaDesc>Testområde</areaDesc><polygon>60,24 61,24 61,26 60,26 60,24</polygon></area></info><info><language>fi-FI</language>")
        assertEquals("Regnvarning", WarningParser.parse(feed(swedish), now, AppLanguage.SV).warnings.single().event)
        assertEquals("Sadevaroitus", WarningParser.parse(feed(swedish), now, AppLanguage.FI).warnings.single().event)
        assertEquals("Sadevaroitus", WarningParser.parse(feed(alert()), now, AppLanguage.SV).warnings.single().event)
        assertEquals("Testområde", WarningParser.parse(feed(swedish), now, AppLanguage.SV).local(vantaa, now).single().areas.single().name)
    }
    @Test fun `polygon selection follows location and includes every future day`() {
        val snapshot = WarningParser.parse(feed(alert()), now, AppLanguage.FI)
        val warning = snapshot.local(vantaa,now).single()
        assertEquals("Sadevaroitus", warning.event)
        assertEquals(Instant.parse("2026-09-18T09:00:00Z"), warning.onset)
        assertEquals(WarningLevel.YELLOW, warning.level)
        assertTrue(snapshot.local(tampere,now).isEmpty())
        assertTrue(snapshot.local(vantaa,warning.expires).isEmpty())
        assertTrue(warning.areas.single().contains(vantaa.copy(latitude=60.0,longitude=24.0)))
        assertFalse(warning.areas.single().contains(vantaa.copy(latitude=25.0408,longitude=60.2925)))
    }
    @Test fun `updates cancellations and test messages are excluded independent of entry order`() {
        val ref = "urn:oid:2.49.0.0.246.0,a,$now"
        val update = alert("b", "Update", ref, "Extreme")
        val snapshot = WarningParser.parse(feed(update, alert(), alert("test",status="Test")), now, AppLanguage.FI)
        assertEquals("b", snapshot.warnings.single().id)
        assertEquals(WarningLevel.RED,snapshot.warnings.single().level)
        val cancel = alert("cancel","Cancel","urn:oid:2.49.0.0.246.0,b,$now")
        assertTrue(WarningParser.parse(feed(update, cancel, alert()), now, AppLanguage.FI).warnings.isEmpty())
        assertTrue(WarningParser.parse(feed(), now, AppLanguage.FI).warnings.isEmpty())
    }
    @Test fun `unknown severity is marked partial and malformed data is never empty success`() {
        assertTrue(WarningParser.parse(feed(alert(severity="NewLevel")), now, AppLanguage.FI).partial)
        assertEquals(WarningLevel.ORANGE,WarningParser.parse(feed(alert(severity="Severe")), now, AppLanguage.FI).warnings.single().level)
        listOf("<html>error</html>",feed(alert(polygon="60,24 61,24")),feed(alert(expires="bad")),
            "<!DOCTYPE feed [<!ENTITY x 'bad'>]>" + feed()).forEach { body ->
            assertTrue(runCatching { WarningParser.parse(body, now, AppLanguage.FI) }.isFailure)
        }
    }
    @Test fun `concave polygons do not match their entire bounding rectangle`() {
        val shape = "60,24 62,24 62,25 61,25 61,26 60,26 60,24"
        val warning = WarningParser.parse(feed(alert(polygon=shape)), now, AppLanguage.FI).warnings.single()
        assertFalse(warning.areas.single().contains(vantaa.copy(latitude=61.5,longitude=25.5)))
        assertTrue(warning.areas.single().contains(vantaa.copy(latitude=60.5,longitude=25.5)))
    }
    @Test fun `official FMI Uusimaa boundary contains Tikkurila but not Tampere`() {
        // FMI CAP archive 2026-07-01 12:01Z, Uusimaa area; data CC BY 4.0 FMI.
        val polygon = javaClass.getResource("/fmi-uusimaa-polygon.txt")!!.readText()
        val data = WarningParser.parse(feed(alert(polygon=polygon,area="Uusimaa")), now, AppLanguage.FI)
        assertEquals("Uusimaa",data.local(vantaa,now).single().localAreas(vantaa).single().name)
        assertTrue(data.local(tampere,now).isEmpty())
    }
    @Test fun `location refresh blocks old-city notifications and existing text follows the new city quietly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_COARSE_LOCATION)
        app.getSharedPreferences("warnings", Context.MODE_PRIVATE).edit().clear().putBoolean("enabled", true).commit()
        val service = WarningService(app)
        val manager = app.getSystemService(NotificationManager::class.java)
        val espoo = vantaa.copy(name = "Espoo")
        val porvoo = vantaa.copy(latitude = 60.39, longitude = 25.66, name = "Porvoo")
        val snapshot = WarningParser.parse(feed(alert()), now, AppLanguage.FI)
        service.savePlace(espoo); service.beginLocationUpdate()
        service.notifyNew(snapshot, now)
        assertTrue(manager.activeNotifications.isEmpty())
        service.savePlace(porvoo, ready = false)
        service.notifyNew(snapshot, now)
        assertTrue(manager.activeNotifications.isEmpty())
        service.savePlace(porvoo)
        service.notifyNew(snapshot, now)
        assertTrue(manager.activeNotifications.single().notification.extras.getString(android.app.Notification.EXTRA_TEXT)!!.startsWith("Porvoo"))
        service.savePlace(espoo)
        service.notifyNew(snapshot, now)
        val updated = manager.activeNotifications.single().notification
        assertTrue(updated.extras.getString(android.app.Notification.EXTRA_TEXT)!!.startsWith("Espoo"))
        assertTrue(updated.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE != 0)
        manager.cancelAll()
        service.savePlace(porvoo); service.notifyNew(snapshot, now)
        assertTrue("A dismissed unchanged regional warning must stay dismissed", manager.activeNotifications.isEmpty())
    }

    @Test fun `republication does not notify again and cancelled warnings leave notification tray`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.ACCESS_COARSE_LOCATION)
        val prefs = app.getSharedPreferences("warnings",Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("enabled",true).commit()
        val service = WarningService(app); service.savePlace(vantaa)
        val manager = app.getSystemService(NotificationManager::class.java)
        service.notifyNew(WarningParser.parse(feed(alert()), now, AppLanguage.FI),now)
        assertEquals(1,manager.activeNotifications.size)
        val original = manager.activeNotifications.single().tag
        manager.cancelAll() // User dismisses the notification.
        service.notifyNew(WarningParser.parse(feed(alert(id="republished")), now, AppLanguage.FI),now)
        assertEquals(0,manager.activeNotifications.size)
        service.notifyNew(WarningParser.parse(feed(alert(id="stronger",severity="Severe")), now, AppLanguage.FI),now)
        assertEquals(1,manager.activeNotifications.size)
        assertNotEquals(original,manager.activeNotifications.single().tag)
        service.notifyNew(WarningParser.parse(feed(), now, AppLanguage.FI),now)
        assertEquals(0,manager.activeNotifications.size)
        service.savePlace(tampere)
        service.notifyNew(WarningParser.parse(feed(alert(id="elsewhere")), now, AppLanguage.FI),now)
        assertEquals(0,manager.activeNotifications.size)
    }

    @Test fun `ending location update evaluates newly cached warnings and cancellations immediately`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_COARSE_LOCATION)
        app.getSharedPreferences("warnings", Context.MODE_PRIVATE).edit().clear().putBoolean("enabled", true).commit()
        val evaluationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val service = WarningService(app, java.time.Clock.fixed(now, java.time.ZoneOffset.UTC), evaluationScope)
            service.savePlace(vantaa)
            val manager = app.getSystemService(NotificationManager::class.java)
            for (body in listOf(feed(alert()), feed())) {
                service.beginLocationUpdate()
                File(app.filesDir, "warnings.json").writeText(JSONObject().put("fetched", now.toString()).put("body", body).toString())
                val before = manager.activeNotifications.size
                service.notifyNew(WarningParser.parse(body, now, AppLanguage.FI), now)
                assertEquals(before, manager.activeNotifications.size)
                service.endLocationUpdate()
                evaluationScope.coroutineContext.job.children.toList().joinAll()
                assertEquals(1 - before, manager.activeNotifications.size)
            }
        } finally { evaluationScope.cancel() }
    }
}
