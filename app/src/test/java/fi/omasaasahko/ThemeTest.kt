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
import fi.omasaasahko.domain.WarningLevel
import fi.omasaasahko.ui.AppTab
import fi.omasaasahko.ui.AppTheme
import fi.omasaasahko.ui.NavTint
import fi.omasaasahko.ui.PriceBand
import fi.omasaasahko.ui.navTint
import fi.omasaasahko.ui.BodyFont
import fi.omasaasahko.ui.ChoiceRow
import fi.omasaasahko.ui.DisplayFont
import fi.omasaasahko.ui.LocationPill
import fi.omasaasahko.ui.SettingSwitchRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import fi.omasaasahko.domain.WeatherSource
import fi.omasaasahko.ui.forecastColors
import fi.omasaasahko.ui.priceColors
import fi.omasaasahko.ui.warningColors
import fi.omasaasahko.ui.sunColors
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

    @Test
    @Config(sdk = [33, 35])
    fun `production dynamic palettes and semantic surfaces retain text contrast`() {
        val pairs = mutableListOf<Triple<String, Color, Color>>()
        val checks = mutableListOf<Pair<ColorScheme, ColorScheme>>()
        compose.setContent {
            val context = LocalContext.current
            for (dark in listOf(false, true)) AppTheme(dark = dark) {
                val scheme = MaterialTheme.colorScheme
                checks.add(scheme to if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
                fun pair(name: String, ink: Color, background: Color) { pairs.add(Triple("$dark $name", ink, background)) }
                for (surface in listOf(scheme.background, scheme.surfaceContainer, scheme.surfaceContainerLow,
                    scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)) {
                    pair("surface", scheme.onSurface, surface)
                    pair("secondary text", scheme.onSurfaceVariant, surface)
                    pair("primary text", scheme.primary, surface)
                }
                pair("inverse", scheme.inverseOnSurface, scheme.inverseSurface)
                pair("notice", scheme.onSecondaryContainer, scheme.secondaryContainer)
                pair("error", scheme.error, scheme.background)
                pair("selected", scheme.onPrimaryContainer, scheme.primaryContainer)
                for (colors in WeatherSource.entries.map { forecastColors(it) } + sunColors()) {
                    pair("weather ink", colors.ink, colors.top)
                    pair("weather muted", colors.muted, colors.top)
                    pair("weather accent", colors.accent, colors.top)
                }
                for (band in PriceBand.entries) {
                    val colors = priceColors(band)
                    pair("price ink", colors.ink, colors.top)
                    pair("price accent", colors.accent, colors.top)
                }
                for (level in WarningLevel.entries) {
                    val colors = warningColors(level)
                    pair("warning ink", colors.ink, colors.container)
                    pair("warning muted", colors.muted, colors.container)
                    pair("warning pill", colors.onAccent, colors.accent)
                    pair("warning time", colors.ink, colors.accent.copy(alpha = 0.16f).compositeOver(colors.container))
                }
            }
        }
        compose.runOnIdle {
            checks.forEach { (actual, expected) ->
                assertEquals(expected.primary, actual.primary)
                assertEquals(expected.surfaceContainerHigh, actual.surfaceContainerHigh)
            }
            pairs.forEach { (name, ink, background) ->
                val a = ink.luminance(); val b = background.luminance()
                val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                assertTrue("$name contrast $contrast", contrast >= 4.5f)
            }
        }
    }

    @Test fun `disabled switch exposes disabled state and dims both text labels`() {
        var calls = 0
        compose.setContent { AppTheme(dynamic = false) {
            SettingSwitchRow("Varoitusilmoitukset", "Sijainti puuttuu", false, { calls++ }, Modifier.testTag("disabled-switch"), enabled = false)
        } }
        compose.onNodeWithTag("disabled-switch").assertIsNotEnabled().performClick()
        assertEquals(0, calls)
        for (text in listOf("Varoitusilmoitukset", "Sijainti puuttuu")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(0.38f, layouts.single().layoutInput.style.color.alpha, 0.01f)
        }
    }

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

    @Test fun `navigation tint follows price band and warning level`() {
        val tints = mutableMapOf<String, NavTint>()
        compose.setContent {
            AppTheme(dark = false, dynamic = false) {
                tints["weather"] = navTint(AppTab.WEATHER, null, null)
                tints["extreme"] = navTint(AppTab.PRICES, PriceBand.EXTREME, null)
                tints["orange"] = navTint(AppTab.WARNINGS, null, WarningLevel.ORANGE)
                tints["calm"] = navTint(AppTab.WARNINGS, null, null)
                tints["ignored"] = navTint(AppTab.WEATHER, PriceBand.EXTREME, WarningLevel.RED)
            }
        }
        compose.runOnIdle {
            assertEquals(Color(0xFFC8F0DF), tints.getValue("weather").container)
            assertEquals(Color(0xFFEADBFF), tints.getValue("extreme").container)
            assertEquals(Color(0xFFFFDEC2), tints.getValue("orange").container)
            assertEquals(tints.getValue("weather"), tints.getValue("calm"))
            assertEquals(tints.getValue("weather"), tints.getValue("ignored"))
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
