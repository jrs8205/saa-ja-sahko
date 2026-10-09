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

    @Test fun `English phone sees English clock punctuation`() {
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("current-time").assertTextContains("12:10", substring = true)
    }
}
