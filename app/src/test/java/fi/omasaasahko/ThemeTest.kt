package fi.omasaasahko

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.omasaasahko.ui.AppTheme
import fi.omasaasahko.ui.BodyFont
import fi.omasaasahko.ui.ChoiceRow
import fi.omasaasahko.ui.DisplayFont
import fi.omasaasahko.ui.LocationPill
import fi.omasaasahko.ui.SettingSwitchRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fi-rFI-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `expressive theme uses bundled fonts and defines navigation tokens`() {
        lateinit var typography: Typography
        lateinit var light: ColorScheme
        lateinit var dark: ColorScheme
        compose.setContent {
            AppTheme(dark = false, dynamic = false) { typography = MaterialTheme.typography; light = MaterialTheme.colorScheme }
            AppTheme(dark = true, dynamic = false) { dark = MaterialTheme.colorScheme }
        }
        compose.runOnIdle {
            assertEquals(DisplayFont, typography.displayLarge.fontFamily)
            assertEquals(DisplayFont, typography.titleLarge.fontFamily)
            assertEquals(BodyFont, typography.bodyMedium.fontFamily)
            assertEquals(BodyFont, typography.labelSmall.fontFamily)
            assertEquals(92.sp, typography.displayLarge.fontSize)
            assertEquals(80.sp, typography.displayMedium.fontSize)
            assertEquals(FontWeight.ExtraBold, typography.headlineLarge.fontWeight)
            assertEquals(Color(0xFF18221D), light.inverseSurface)
            assertEquals(Color(0xFFE0EBE1), dark.inverseSurface)
            assertEquals(Color(0xFF313D34), dark.surfaceContainerHighest)
        }
    }

    @Test fun `pill choice row selects and keeps 44dp targets`() {
        var selected by mutableIntStateOf(0)
        compose.setContent { AppTheme(dynamic = false) { ChoiceRow(listOf("Vartti", "Tunti"), selected, { selected = it }) } }
        compose.onNodeWithText("Vartti").assertIsSelected().assertHeightIsAtLeast(44.dp)
        compose.onNodeWithText("Tunti").assertIsNotSelected().performClick()
        compose.onNodeWithText("Tunti").assertIsSelected()
        assertEquals(1, selected)
    }

    @Test fun `location pill is a button only when it has an action and switch row toggles`() {
        var opened = 0
        var checked by mutableStateOf(false)
        compose.setContent {
            AppTheme(dynamic = false) {
                Column {
                    LocationPill("Tikkurila, Vantaa", { opened++ })
                    LocationPill("Uusimaa", null)
                    SettingSwitchRow("ALV 25,5 %", "Mukana hinnoissa", checked, { checked = it }, Modifier.testTag("row"))
                }
            }
        }
        compose.onNodeWithTag("open-place-search").assertHasClickAction().assertHeightIsAtLeast(52.dp).performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("Uusimaa").assertHasNoClickAction()
        compose.onNodeWithTag("row").assertIsOff().performClick().assertIsOn()
    }
}
