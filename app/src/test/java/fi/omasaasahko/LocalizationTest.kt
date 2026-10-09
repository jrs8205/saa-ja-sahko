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

    @Test fun `English phone sees English clock punctuation`() {
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("current-time").assertTextContains("12:10", substring = true)
    }
}
