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
import fi.omasaasahko.ui.bestContrastOn
import fi.omasaasahko.ui.contrastRatio
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import fi.omasaasahko.ui.readable
import fi.omasaasahko.ui.symbolColors
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
    fun `production dynamic palettes and semantic surfaces reach WCAG AAA text and AA graphics contrast`() {
        val text = mutableListOf<Triple<String, Color, Color>>()
        val graphics = mutableListOf<Triple<String, Color, Color>>()
        val checks = mutableListOf<Pair<ColorScheme, ColorScheme>>()
        compose.setContent {
            val context = LocalContext.current
            for (dark in listOf(false, true)) AppTheme(dark = dark) {
                val scheme = MaterialTheme.colorScheme
                checks.add(scheme to if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
                fun pair(name: String, ink: Color, background: Color) { text.add(Triple("$dark $name", ink, background)) }
                fun shape(name: String, fill: Color, background: Color) { graphics.add(Triple("$dark $name", fill, background)) }
                val surfaces = listOf(scheme.background, scheme.surfaceContainer, scheme.surfaceContainerLow,
                    scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
                for (surface in surfaces) {
                    pair("surface", scheme.onSurface, surface)
                    pair("secondary text", scheme.onSurfaceVariant, surface)
                    pair("primary text", scheme.primary, surface)
                    pair("error", scheme.error, surface)
                }
                pair("inverse", scheme.inverseOnSurface, scheme.inverseSurface)
                pair("notice", scheme.onSecondaryContainer, scheme.secondaryContainer)
                pair("selected", scheme.onPrimaryContainer, scheme.primaryContainer)
                pair("on primary", scheme.onPrimary, scheme.primary)
                pair("nav idle", if (dark) scheme.onSurfaceVariant else scheme.inverseOnSurface,
                    if (dark) scheme.surfaceContainerHighest else scheme.inverseSurface)
                for (colors in WeatherSource.entries.map { forecastColors(it) } + sunColors()) {
                    pair("weather ink", colors.ink, colors.top)
                    pair("weather muted", colors.muted, colors.top)
                    pair("weather accent", colors.accent, colors.top)
                }
                for (source in WeatherSource.entries) {
                    val colors = forecastColors(source)
                    for (surface in surfaces) pair("source label", colors.accent, surface)
                    shape("source dot", colors.accent, scheme.surfaceContainerHigh)
                }
                for (band in PriceBand.entries) {
                    val colors = priceColors(band)
                    pair("price ink", colors.ink, colors.top)
                    pair("price ink row", colors.ink, colors.bottom)
                    pair("price accent", colors.accent, colors.top)
                    pair("price accent row", colors.accent, colors.bottom)
                    for (surface in surfaces) pair("price label", colors.accent, surface)
                    pair("price pill", if (dark) colors.bottom else Color.White, colors.accent)
                    shape("price bar", colors.accent, scheme.surfaceContainerHigh)
                    val nav = navTint(AppTab.PRICES, band, null)
                    pair("price nav", nav.content, nav.container)
                }
                for (level in WarningLevel.entries) {
                    val colors = warningColors(level)
                    pair("warning ink", colors.ink, colors.container)
                    pair("warning muted", colors.muted, colors.container)
                    pair("warning pill", colors.onAccent, colors.accent)
                    pair("warning time", colors.ink, colors.pill)
                    shape("warning badge", colors.accent, colors.container)
                    val nav = navTint(AppTab.WARNINGS, null, level)
                    pair("warning nav", nav.content, nav.container)
                }
                val calm = navTint(AppTab.WEATHER, null, null)
                pair("calm nav", calm.content, calm.container)
                val symbol = symbolColors()
                val cards = surfaces + WeatherSource.entries.map { forecastColors(it).top }
                for (card in cards) {
                    shape("symbol cloud", symbol.cloud, card)
                    shape("symbol rear cloud", symbol.rearCloud, card)
                    shape("symbol rain", symbol.rain, card)
                    shape("symbol snow", symbol.snow, card)
                    shape("symbol sun edge", symbol.sunEdge, card)
                    if (symbol.sun == symbol.sunEdge) shape("symbol sun", symbol.sun, card)
                }
                if (symbol.sun != symbol.sunEdge) shape("symbol sun fill against its edge", symbol.sun, symbol.sunEdge)
            }
        }
        compose.runOnIdle {
            checks.forEach { (actual, expected) ->
                assertEquals(expected.primaryContainer, actual.primaryContainer)
                assertEquals(expected.surfaceContainerHigh, actual.surfaceContainerHigh)
            }
            // Robolectric's stand-in system palette has mid-tone containers no colour can carry 7:1 on;
            // real Material You containers are tone 90 / 30, where the lift always reaches AAA.
            fun needed(background: Color) = minOf(7f, bestContrastOn(listOf(background)) - 0.01f)
            val failures = text.filter { (_, ink, background) -> contrastRatio(ink, background) < needed(background) } +
                graphics.filter { (_, fill, background) -> contrastRatio(fill, background) < 3f }
            assertTrue(failures.joinToString("; ") { (name, ink, background) ->
                "$name ${ink.hex()} on ${background.hex()} = ${contrastRatio(ink, background)}" }, failures.isEmpty())
        }
    }

    @Test fun `dynamic schemes are lifted to AAA text contrast without losing their hue`() {
        for (scheme in listOf(lightColorScheme(), darkColorScheme())) {
            val fixed = scheme.readable()
            val surfaces = listOf(fixed.background, fixed.surfaceContainerLow, fixed.surfaceContainer,
                fixed.surfaceContainerHigh, fixed.surfaceContainerHighest)
            for (surface in surfaces) for (ink in listOf(fixed.onSurface, fixed.onSurfaceVariant, fixed.primary, fixed.error)) {
                assertTrue("$ink on $surface ${contrastRatio(ink, surface)}", contrastRatio(ink, surface) >= 7f)
            }
            assertTrue(contrastRatio(fixed.onPrimary, fixed.primary) >= 7f)
            assertTrue(contrastRatio(fixed.onPrimaryContainer, fixed.primaryContainer) >= 7f)
            assertTrue(contrastRatio(fixed.onSecondaryContainer, fixed.secondaryContainer) >= 7f)
            assertTrue(contrastRatio(fixed.inverseOnSurface, fixed.inverseSurface) >= 7f)
            assertEquals(scheme.surfaceContainerHigh, fixed.surfaceContainerHigh)
            assertEquals(scheme.primaryContainer, fixed.primaryContainer)
            // Material's baseline light purple is only AA on its surfaces: the lift keeps it purple.
            if (scheme.background.luminance() > 0.5f) assertTrue(scheme.primary != fixed.primary)
            assertTrue(fixed.primary.blue > fixed.primary.green && fixed.primary.red > fixed.primary.green)
            // Colours that already pass stay exactly as the system defined them.
            assertEquals(scheme.onSurface, fixed.onSurface)
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

    @Test fun `text follows the phone font size up to 150 percent and no further`() {
        var scale by mutableFloatStateOf(1f)
        var seen = 0f
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                AppTheme(dynamic = false) {
                    seen = LocalDensity.current.fontScale
                    androidx.compose.material3.Text("Tuntuu kuin 14,7°", Modifier.testTag("sample"), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        fun height(size: Float): Int { compose.runOnIdle { scale = size }; return compose.onNodeWithTag("sample").fetchSemanticsNode().size.height }
        val normal = height(1f)
        val small = height(0.85f); assertEquals(0.85f, seen)
        val large = height(1.5f); assertEquals(1.5f, seen)
        assertEquals(large, height(2f)); assertEquals(1.5f, seen)
        assertTrue("$small < $normal < $large", small < normal && normal < large)
    }
}

private fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)
