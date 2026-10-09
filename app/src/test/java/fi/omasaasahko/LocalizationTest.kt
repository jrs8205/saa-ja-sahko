package fi.omasaasahko

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.domain.AppLanguage
import fi.omasaasahko.ui.*
import org.junit.Assert.assertEquals
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

    @Test fun `English phone sees English clock punctuation`() {
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("current-time").assertTextContains("12:10", substring = true)
    }
}
