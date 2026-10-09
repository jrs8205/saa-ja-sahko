package fi.omasaasahko

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import fi.omasaasahko.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rGB-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalizationTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `Finnish and Swedish phones get their language and everyone else gets English`() {
        mapOf("fi" to AppLanguage.FI, "fi-rFI" to AppLanguage.FI, "sv" to AppLanguage.SV, "sv-rSE" to AppLanguage.SV,
            "sv-rFI" to AppLanguage.SV, "en-rGB" to AppLanguage.EN, "de" to AppLanguage.EN, "et" to AppLanguage.EN).forEach { (qualifier, expected) ->
            RuntimeEnvironment.setQualifiers(qualifier)
            assertEquals(qualifier, expected, AppLanguage.of(app))
        }
    }

    @Test fun `navigation and place search speak Swedish`() {
        RuntimeEnvironment.setQualifiers("sv-rFI-w411dp-h891dp-xhdpi")
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Väder").assertIsDisplayed()
        compose.onNodeWithText("Elpris").assertIsDisplayed()
        compose.onNodeWithText("Varningar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Uppdatera").assertIsDisplayed()
        compose.onNodeWithTag("open-place-search").performClick()
        compose.onNodeWithText("Sök ort").assertIsDisplayed()
        compose.onNodeWithText("Skriv minst två tecken. Med stjärnan sparar du orten som favorit.").assertIsDisplayed()
        compose.onNodeWithText("Stäng").performClick()
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithText("Elpriset").assertIsDisplayed()
    }

    @Test fun `navigation and place search speak English`() {
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Warnings").assertIsDisplayed()
        compose.onNodeWithText("Electricity").assertIsDisplayed()
        compose.onNodeWithTag("open-place-search").performClick()
        compose.onNodeWithText("Find a place").assertIsDisplayed()
        compose.onNodeWithText("Municipality or place name").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("tab-warnings").performClick()
        compose.onNodeWithText("Tikkurila, Vantaa").assertIsDisplayed()
    }

    @Test fun `every weather description resolves to its own text in every language`() {
        listOf("fi", "sv", "en-rGB").forEach { qualifier ->
            RuntimeEnvironment.setQualifiers(qualifier)
            val texts = WeatherText.entries.associateWith { app.getString(it.res()) }
            texts.forEach { (key, text) -> assertTrue("$qualifier $key", text.isNotBlank()) }
            assertEquals("$qualifier duplicates: " + texts.entries.groupBy { it.value }.filter { it.value.size > 1 }.keys,
                texts.size, texts.values.toSet().size)
        }
        RuntimeEnvironment.setQualifiers("sv")
        assertEquals("Enstaka lätta snöbyar", app.getString(WeatherText.ISOLATED_LIGHT_SNOW_SHOWERS.res()))
        assertEquals("Meteorologiska institutet", app.getString(WeatherSource.FMI.titleRes()))
    }

    @Test fun `weather screen speaks English with English numbers`() {
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("current-time").assertTextEquals("Now 12:10")
        compose.onNodeWithText("Wednesday 16 September").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Today"))
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Wind · m/s"))
        compose.onNodeWithContentDescription("Finnish Meteorological Institute: Wind 4.0 m/s").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Next 24 hours"))
        compose.onNodeWithText("Next 24 hours").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("day-2026-09-16"))
        compose.onNodeWithTag("day-2026-09-16").assertTextContains("Chance of rain 80%", substring = true)
        compose.onNodeWithTag("day-2026-09-16").assertTextContains("Finnish Meteorological Institute", substring = true)
    }

    @Test fun `weather screen speaks Swedish`() {
        RuntimeEnvironment.setQualifiers("sv-rFI-w411dp-h891dp-xhdpi")
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("DIN POSITION").assertIsDisplayed()
        compose.onNodeWithText("Onsdag 16 september").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Kommande 24 timmar"))
        compose.onNodeWithText("Kommande 24 timmar").assertIsDisplayed()
        compose.onNodeWithText("Temperatur · vind · nederbörd").assertIsDisplayed()
    }

    @Test fun `electricity screen speaks Swedish`() {
        RuntimeEnvironment.setQualifiers("sv-rFI-w411dp-h891dp-xhdpi")
        var state by mutableStateOf(PreviewData.state)
        compose.setContent { AppTheme(dynamic = false) { AppScreen(state, true, {}, {}, {}, {}, {}, { state = state.copy(resolution = it) }) } }
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithText("AKTUELL KVART").assertIsDisplayed()
        compose.onNodeWithText("Inkl. moms 25,5 %").assertIsDisplayed()
        compose.onNodeWithText("Timme").performClick()
        compose.onNodeWithText("AKTUELL TIMMES MEDELPRIS").assertIsDisplayed()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText("Billigast"))
        compose.onNodeWithText("Billigast").assertIsDisplayed()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText("Prisfärger · c/kWh"))
        compose.onNodeWithText("Prisfärger · c/kWh").assertIsDisplayed()
    }

    @Test fun `price notifications speak English with English numbers`() {
        val day = java.time.LocalDate.of(2026, 9, 17)
        val from = day.atStartOfDay(HELSINKI).toInstant()
        val prices = PriceData(from, generateSequence(from) { it.plusSeconds(900) }.takeWhile { it < day.plusDays(1).atStartOfDay(HELSINKI).toInstant() }
            .map { QuarterPrice(it, java.math.BigDecimal("40.123")) }.toList())
        val text = tomorrowPriceMessage(prices, day.minusDays(1).atTime(15, 0).atZone(HELSINKI).toInstant(), true, app)!!
        assertTrue(text, text.startsWith("Thu 17 Sep · VAT 25.5%"))
        assertTrue(text, text.contains("Average 5.035 c/kWh"))
        assertTrue(text, text.contains("Cheapest hour 00:00–01:00: 5.035 c/kWh"))
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        PriceAlerts(app).testNotification()
        val notification = app.getSystemService(android.app.NotificationManager::class.java).activeNotifications.single().notification
        assertEquals("Electricity price test", notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
    }

    private fun warningSnapshot(level: WarningLevel): WarningSnapshot {
        val now = PreviewData.now
        val area = WarningArea("Uusimaa", listOf(listOf(GeoPoint(60.0, 24.0), GeoPoint(61.0, 24.0), GeoPoint(61.0, 26.0), GeoPoint(60.0, 26.0), GeoPoint(60.0, 24.0))))
        val warning = WeatherWarning("w1", "rain", "Rain warning", level, now.plusSeconds(7200), now.plusSeconds(86400), "Heavy rain.", "", listOf(area), now)
        return WarningSnapshot(now, now, listOf(warning))
    }

    @Test fun `warnings screen speaks Swedish`() {
        RuntimeEnvironment.setQualifiers("sv-rFI-w411dp-h891dp-xhdpi")
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state.copy(devicePlace = PreviewData.place), true, {}, {}, {}, {}, {}, {},
            warnings = WarningsState(snapshot = warningSnapshot(WarningLevel.ORANGE), enabled = true, allowed = true)) } }
        compose.onNodeWithTag("tab-warnings").performClick()
        compose.onNodeWithText("1 varning").assertIsDisplayed()
        compose.onNodeWithTag("warnings-scroll").performScrollToNode(hasText("Orange · Kommande"))
        compose.onAllNodesWithText("Orange · Kommande").onFirst().assertIsDisplayed()
        compose.onNodeWithTag("warnings-scroll").performScrollToNode(hasText("Varningsaviseringar"))
        compose.onNodeWithText("Varningsaviseringar").assertIsDisplayed()
        compose.onNodeWithTag("warnings-scroll").performScrollToNode(hasText("Varningar på FMI:s webbplats ↗"))
        compose.onNodeWithText("Varningar på FMI:s webbplats ↗").assertIsDisplayed()
    }

    @Test fun `warnings screen and warning notifications speak English`() {
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state.copy(devicePlace = PreviewData.place), true, {}, {}, {}, {}, {}, {},
            warnings = WarningsState(snapshot = warningSnapshot(WarningLevel.RED))) } }
        compose.onNodeWithTag("tab-warnings").performClick()
        compose.onNodeWithText("1 warning").assertIsDisplayed()
        compose.onNodeWithText("Finnish Meteorological Institute · Now 16 Sep 12:10").assertIsDisplayed()
        compose.onNodeWithTag("warnings-scroll").performScrollToNode(hasText("Red · Upcoming"))
        compose.onAllNodesWithText("Red · Upcoming").onFirst().assertIsDisplayed()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        WarningService(app).testNotification()
        val notification = app.getSystemService(android.app.NotificationManager::class.java).activeNotifications.single().notification
        assertEquals("Weather warning test", notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
    }

    @Test fun `error messages are translated by the screen`() {
        val state = PreviewData.state.copy(locationError = AppMessage.LOCATION_DISABLED, pricesError = AppMessage.PRICES_REFRESH_FAILED,
            searchError = AppMessage.PLACE_SEARCH_FAILED, searchQuery = "Po")
        compose.setContent { AppTheme(dynamic = false) { AppScreen(state, true, {}, {}, {}, {}, {}, {},
            warnings = WarningsState(error = AppMessage.WARNINGS_REFRESH_FAILED)) } }
        compose.onNodeWithText("Turn on the phone’s location.").assertIsDisplayed()
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithText("Price update failed. Showing the last fetched prices, if any.").assertIsDisplayed()
        compose.onNodeWithTag("tab-warnings").performClick()
        compose.onNodeWithText("Warning update failed. Earlier data may be out of date.").assertIsDisplayed()
        compose.onNodeWithTag("tab-weather").performClick()
        compose.onNodeWithTag("open-place-search").performClick()
        compose.onNodeWithText("Place search failed. Check your connection and try again.").assertIsDisplayed()
    }

    @Test fun `an unnamed device fix is shown as the translated current location`() {
        val fix = PreviewData.place.copy(name = CURRENT_LOCATION_NAME, origin = PlaceOrigin.DEVICE, nameResolved = false)
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state.copy(place = fix, devicePlace = fix), true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Current location").assertIsDisplayed()
        compose.onNodeWithText(CURRENT_LOCATION_NAME).assertDoesNotExist()
        compose.onNodeWithTag("tab-warnings").performClick()
        compose.onNodeWithTag("warnings-scroll").performScrollToNode(hasText("Tracking: Current location · 16 Sep 12:10"))
        compose.onNodeWithText("Tracking: Current location · 16 Sep 12:10").assertIsDisplayed()
    }

    @Test fun `English phone sees English clock punctuation`() {
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("current-time").assertTextContains("12:10", substring = true)
    }
}
