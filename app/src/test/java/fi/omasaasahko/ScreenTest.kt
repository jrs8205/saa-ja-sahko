package fi.omasaasahko

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import fi.omasaasahko.domain.*
import fi.omasaasahko.data.PriceAlertState
import fi.omasaasahko.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.math.BigDecimal
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fi-rFI-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `price timeline reserves offset label height throughout autumn clock change`() {
        val start = java.time.Instant.parse("2026-10-25T00:00:00Z")
        val rows = (0..11).map { i -> PriceSlot(start.plusSeconds(i * 3600L), start.plusSeconds((i + 1) * 3600L), BigDecimal.ONE) }
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                AppTheme(dynamic = false) { PriceChart(rows, -1, 1, {}) }
            }
        }
        for (size in listOf(1f, 2f)) {
            compose.runOnIdle { scale = size }
            compose.onNodeWithTag("price-timeline").performScrollToIndex(0)
            compose.onNodeWithText(clockLabel(start).replace(" (", "\n("), useUnmergedTree = true).assertTextFits(singleLine = false)
            val height = compose.onNodeWithTag("price-timeline").fetchSemanticsNode().size.height
            compose.onNodeWithTag("price-timeline").performScrollToIndex(8)
            assertEquals(height, compose.onNodeWithTag("price-timeline").fetchSemanticsNode().size.height)
        }
    }

    @Test
    @Config(sdk = [33, 35])
    fun `production dynamic theme renders all screens in light and dark`() {
        var dark by mutableStateOf(false)
        compose.setContent { AppTheme(dark = dark) {
            AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}, warnings = WarningsState(
                snapshot = WarningSnapshot(PreviewData.now, PreviewData.now, emptyList())))
        } }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark = night }
            for (tab in AppTab.entries) {
                compose.onNodeWithTag(tab.tag).performClick().assertIsSelected()
                screenshot("review-dynamic-${android.os.Build.VERSION.SDK_INT}-${tab.name}-${if (night) "dark" else "light"}")
            }
        }
    }

    @Test
    @Config(qualifiers = "fi-rFI-w320dp-h891dp-xhdpi")
    fun `hourly columns align with headings across font scales and autumn clock change`() {
        val now = java.time.Instant.parse("2026-10-24T23:10:00Z")
        val state = PreviewData.state.copy(now = now)
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                AppTheme(dynamic = false) { AppScreen(state, true, {}, {}, {}, {}, {}, {}) }
            }
        }
        val times = (0..2).map { now.truncatedTo(ChronoUnit.HOURS).plusSeconds((it + 1) * 3600L) }
        for (size in listOf(1f, 2f)) {
        compose.runOnIdle { scale = size }
        for (source in WeatherSource.entries) {
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("hour-heading-${source.name}"))
            val heading = compose.onNodeWithTag("hour-heading-${source.name}").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("hour-heading-${source.name}").assertTextFits()
            for (time in times) {
                val tag = "hour-${source.name}-$time"
                compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
                assertEquals(heading.left, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.left, 1f)
                compose.onNodeWithText(clockLabel(time), useUnmergedTree = true).assertTextFits()
            }
        }
        }
        screenshot("review-hour-columns-dst-320-2.0")
    }

    @Test fun `tiny signed prices stay on their side of zero and zero has no area`() {
        val start = PreviewData.now
        val rows = listOf("0.3", "-0.3", "0", "20", "-5").mapIndexed { i, value ->
            PriceSlot(start.plusSeconds(i * 900L), start.plusSeconds((i + 1) * 900L), BigDecimal(value))
        }
        compose.setContent { AppTheme(dark = false, dynamic = false) { PriceChart(rows, -1, -1, {}) } }
        val accent = Color.rgb(0x14, 0x62, 0x3C)
        for (i in 0..2) {
            val bitmap = compose.onNodeWithTag("price-plot-$i", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            // Axis is -10..20, with 8 dp inset in the 220 dp canvas.
            val zero = bitmap.height * (8f + 204f * 20f / 30f) / 220f
            val painted = (0 until bitmap.height).filter { bitmap.getPixel(bitmap.width / 2, it) == accent }
            if (i == 0) assertTrue("Positive bar extends below zero: $painted / $zero", painted.isNotEmpty() && painted.all { it < zero })
            if (i == 1) assertTrue("Negative bar extends above zero", painted.isNotEmpty() && painted.all { it >= zero - 1 })
            if (i == 2) assertTrue("Zero should only mark the baseline", painted.size <= 4)
        }
        screenshot("review-signed-price-bars")
    }

    @Test
    @Config(qualifiers = "fi-rFI-w800dp-h891dp-xhdpi")
    fun `trace rainfall keeps a small height on tablet with visible scale guides`() {
        val times = (0..23).map { PreviewData.now.plusSeconds(it * 3600L) }
        val rain = listOf(0.1, 20.0) + List(22) { 0.0 }
        compose.setContent { AppTheme(dark = false, dynamic = false) {
            RainBlock(rain, times, HELSINKI, WeatherSource.FMI) {}
        } }
        val bitmap = compose.onNodeWithTag("rain-plot").captureToImage().asAndroidBitmap()
        val accent = Color.rgb(0x17, 0x66, 0x9E)
        val painted = (0 until bitmap.height).count { bitmap.getPixel(bitmap.width / 48, it) == accent }
        assertTrue("0.1 mm was exaggerated to $painted pixels", painted in 1..4)
        screenshot("review-tablet-rain")
    }

    @Test
    @Config(qualifiers = "fi-rFI-w320dp-h891dp-xhdpi")
    fun `negative current temperatures fit at narrow width through double font scale`() {
        var scale by mutableFloatStateOf(1f)
        var temperature by mutableStateOf(-27.0)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                AppTheme(dynamic = false) {
                    val preview = PreviewData.state
                    val state = preview.copy(weather = preview.weather.mapValues { (_, source) ->
                        source.copy(forecast = source.forecast!!.copy(hours = source.forecast.hours.map { it.copy(temperature = temperature) }))
                    })
                    AppScreen(state, true, {}, {}, {}, {}, {}, {})
                }
            }
        }
        for (size in listOf(1f, 1.15f, 1.6f, 2f)) for (value in listOf(-27.0, -9.0, -105.0)) {
            compose.runOnIdle { scale = size; temperature = value }
            for (source in WeatherSource.entries) {
                compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("current-${source.name}"))
                compose.onNode(hasText(temperature(value)) and hasAnyAncestor(hasTestTag("current-${source.name}"))).assertTextFits()
            }
            if (value == -27.0) screenshot("review-temperature-320-$size")
        }
    }

    @Test fun `warning count is never presented as known when the source or location is unavailable`() {
        val app = PreviewData.state
        val snapshot = WarningSnapshot(app.now, app.now, emptyList())
        var state by mutableStateOf(WarningsState())
        var permitted by mutableStateOf(true)
        var place by mutableStateOf(app.place)
        compose.setContent { AppTheme(dynamic = false) {
            WarningsScreen(app.copy(place = place), state, permitted, rememberLazyListState(), {}, {}, {}, {}, {})
        } }
        val unknown = listOf(WarningsState(), WarningsState(loading = true), WarningsState(error = "Ei verkkoa"),
            WarningsState(snapshot = snapshot, error = "Ei verkkoa"), WarningsState(snapshot = snapshot.copy(partial = true)),
            WarningsState(snapshot = snapshot.copy(fetchedAt = app.now.minusSeconds(7200))))
        for (value in unknown) {
            compose.runOnIdle { state = value }
            compose.onNodeWithText("0 varoitusta").assertDoesNotExist()
        }
        compose.runOnIdle { state = WarningsState(snapshot = snapshot); permitted = false }
        compose.onNodeWithText("0 varoitusta").assertDoesNotExist()
        compose.runOnIdle { permitted = true; place = null }
        compose.onNodeWithText("0 varoitusta").assertDoesNotExist()
        compose.runOnIdle { place = app.place }
        compose.onNodeWithText("0 varoitusta").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fi-rFI-w320dp-h891dp-xhdpi")
    fun `all choice rows keep full labels and clickable targets at large fonts`() {
        var scale by mutableFloatStateOf(1.5f)
        var source by mutableIntStateOf(0)
        var interval by mutableIntStateOf(0)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                AppTheme(dynamic = false) {
                    Column(Modifier.width(224.dp)) {
                        ChoiceRow(listOf("FMI", "Open-Meteo"), source, { source = it })
                        ChoiceRow(listOf("15 min", "30 min", "60 min"), interval, { interval = it })
                    }
                }
            }
        }
        for (size in listOf(1.5f, 1.8f, 2f)) {
            compose.runOnIdle { scale = size }
            for (label in listOf("Open-Meteo", "60 min")) {
                compose.onNodeWithText(label, useUnmergedTree = true).assertTextFits()
                compose.onNodeWithText(label).assertHeightIsAtLeast(44.dp).performClick().assertIsSelected()
            }
        }
        screenshot("review-choice-320-2.0")
    }

    @Test fun `metric values identify provider metric and unit without relying on color`() {
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Tuuli · m/s"))
        compose.onNodeWithContentDescription("Ilmatieteen laitos: Tuuli 4,0 m/s").assertExists()
        compose.onNodeWithContentDescription("Open-Meteo: Tuuli 4,0 m/s").assertExists()
        compose.onNode(hasText("FMI") and hasAnyAncestor(hasContentDescription("Ilmatieteen laitos: Tuuli 4,0 m/s")), useUnmergedTree = true).assertExists()
    }

    @Test fun `price timeline height stays constant when current slot scrolls out`() {
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasTestTag("price-timeline"))
        compose.onNodeWithTag("price-timeline").performScrollToNode(hasText("Nyt"))
        val withNow = compose.onNodeWithTag("price-timeline").fetchSemanticsNode().size.height
        compose.onNodeWithTag("price-timeline").performScrollToIndex(0)
        assertEquals(withNow, compose.onNodeWithTag("price-timeline").fetchSemanticsNode().size.height)
    }

    @Test
    @Config(qualifiers = "fi-rFI-w320dp-h891dp-xhdpi")
    fun `narrow sun block retains both times and source across themes and font scales`() {
        var fontScale by mutableFloatStateOf(1f)
        var dark by mutableStateOf(false)
        var source by mutableStateOf(WeatherSource.FMI)
        val preview = PreviewData.state
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme(dark = dark, dynamic = false) {
                    val state = if (source == WeatherSource.FMI) preview else preview.copy(weather = preview.weather - WeatherSource.FMI)
                    AppScreen(state, true, {}, {}, {}, {}, {}, {})
                }
            }
        }
        for (night in listOf(false, true)) for (provider in WeatherSource.entries) for (scale in listOf(1f, 1.25f, 1.6f)) {
            compose.runOnIdle { dark = night; source = provider; fontScale = scale }
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("sun-source"))
            compose.onNodeWithTag("sun-source").assertTextEquals(provider.title).assertTextFits()
            compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Auringonnousu 06.50"))
            compose.onNodeWithContentDescription("Auringonnousu 06.50").assertTextFits()
            compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Auringonlasku 19.39"))
            compose.onNodeWithContentDescription("Auringonlasku 19.39").assertTextFits()
            if (provider == WeatherSource.FMI) screenshot("weather-sun-320-$scale-${if (night) "dark" else "light"}")
        }
    }

    @Test
    @Config(qualifiers = "fi-rFI-w320dp-h891dp-xhdpi")
    fun `narrow electricity selectors keep labels readable and choices usable`() {
        checkElectricitySelectors(listOf(1f, 1.25f, 1.6f), "320")
    }

    @Test fun `electricity selectors fit at moderately increased font scale`() {
        checkElectricitySelectors(listOf(1.2f, 1f, 1.6f), "411")
    }

    private fun checkElectricitySelectors(scales: List<Float>, width: String) {
        var state by mutableStateOf(PreviewData.state)
        var fontScale by mutableFloatStateOf(scales.first())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme(dynamic = false) {
                    AppScreen(state, true, {}, {}, {}, {}, {}, { state = state.copy(resolution = it) })
                }
            }
        }
        compose.onNodeWithTag("tab-prices").performClick()
        for (scale in scales) {
            compose.runOnIdle { fontScale = scale }
            for (label in listOf("Huomenna", "Tänään", "Vartti", "Tunti")) {
                compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText(label))
                compose.onNodeWithText(label, useUnmergedTree = true).assertTextFits()
                compose.onNodeWithText(label).assertHeightIsAtLeast(44.dp).performClick().assertIsSelected()
            }
            assertEquals(Resolution.HOUR, state.resolution)
            screenshot("electricity-selectors-$width-$scale")
        }
    }

    @Test
    @Config(qualifiers = "fi-rFI-w320dp-h891dp-xhdpi")
    fun `narrow price summaries retain every digit at different font scales`() {
        val preview = PreviewData.state
        val state = preview.copy(includeVat = false, prices = preview.prices!!.copy(
            quarters = preview.prices.quarters.map { it.copy(euroPerMwh = BigDecimal("-1234.56")) }))
        var fontScale by mutableFloatStateOf(1.25f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme(dynamic = false) { AppScreen(state, true, {}, {}, {}, {}, {}, {}) }
            }
        }
        compose.onNodeWithTag("tab-prices").performClick()
        for (scale in listOf(1.25f, 1f, 1.6f)) {
            compose.runOnIdle { fontScale = scale }
            for (label in listOf("Halvin", "Keskihinta", "Kallein")) {
                compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText(label))
                compose.onNode(hasText("-123,456") and
                    hasAnySibling(hasText(label) or hasAnyDescendant(hasText(label))), useUnmergedTree = true)
                    .assertTextFits()
            }
            screenshot("electricity-summary-320-$scale")
        }
    }

    @Test fun `nearby name is separate from district and electricity test can be scheduled`() {
        val state=PreviewData.state.copy(place=PreviewData.state.place!!.copy(nearbyName="Kaivopuisto"))
        var pending by mutableStateOf(false)
        compose.setContent {
            AppTheme(dynamic=false) {
                AppScreen(state,true,{},{},{},{},{},{},priceAlerts=PriceAlertState(true,true,pending),
                    onPriceAlertsTest={ pending=true })
            }
        }
        compose.onNodeWithText("Tikkurila, Vantaa").assertIsDisplayed()
        compose.onNodeWithText("Kaivopuisto").assertIsDisplayed()
        compose.onNodeWithText("Lähin paikannimi · Maanmittauslaitos").assertIsDisplayed()
        screenshot("nearby-place")
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText("Testaa ilmoitus 10 s kuluttua"))
        compose.onNodeWithText("Testaa ilmoitus 10 s kuluttua").performClick().assertIsNotEnabled()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText("Lukitse puhelin nyt ja odota ilmoitusta kelloon."))
        compose.onNodeWithText("Lukitse puhelin nyt ja odota ilmoitusta kelloon.").assertIsDisplayed()
    }

    @Test fun `light theme navigation and hourly switch work`() {
        var state by mutableStateOf(PreviewData.state)
        compose.setContent {
            AppTheme(dark = false, dynamic = false) {
                AppScreen(state, true, {}, {}, {}, {}, {}, { state = state.copy(resolution = it) })
            }
        }
        compose.onNodeWithText("Tikkurila, Vantaa").assertIsDisplayed()
        screenshot("weather-light")
        launcherIconPreview()
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithText("NYKYINEN VARTTI").assertIsDisplayed()
        screenshot("electricity-light")
        compose.onNodeWithText("Tunti").performClick()
        compose.onNodeWithText("NYKYISEN TUNNIN KESKIHINTA").assertIsDisplayed()
        assertEquals(Resolution.HOUR, state.resolution)
        compose.onNodeWithText("Huomenna").performClick()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText("Tuntikeskiarvot · 24 tuntia"))
        compose.onNodeWithText("Tuntikeskiarvot · 24 tuntia").assertIsDisplayed()
        compose.onNodeWithTag("tab-weather").performClick()
        compose.onNodeWithText("Tikkurila, Vantaa").assertIsDisplayed()
    }

    @Test fun `dark theme shows price content and renders charts`() {
        var dark by mutableStateOf(true)
        compose.setContent {
            AppTheme(dark = dark, dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) }
        }
        screenshot("weather-dark")
        compose.onNodeWithTag("tab-prices").performClick()
        screenshot("electricity-dark")
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText("Hintavärit · snt/kWh"))
        compose.onNodeWithText("Hintavärit · snt/kWh").assertIsDisplayed()
        screenshot("electricity-chart-dark")
        compose.runOnIdle { dark = false }
        screenshot("electricity-chart-light")
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText("Kaikki vartit"))
        screenshot("electricity-list-light")
    }

    @Test fun `denied location does not prevent electricity use`() {
        compose.setContent {
            AppTheme(dynamic = false) { AppScreen(AppState(now = PreviewData.now), false, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Salli sijainti").assertIsDisplayed()
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithText("Sähkön hinta").assertIsDisplayed()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText("Hintoja ei ole saatavilla. Päivitä näkymä tai tarkista verkkoyhteys."))
        compose.onNodeWithText("Hintoja ei ole saatavilla. Päivitä näkymä tai tarkista verkkoyhteys.").assertIsDisplayed()
    }

    @Test fun `large font retains navigation and scrollable prices`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) }
            }
        }
        screenshot("weather-large-font")
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("sun-source"))
        screenshot("weather-sun-large-font")
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText("Tunti"))
        compose.onNodeWithText("Tunti").assertIsDisplayed()
        screenshot("electricity-large-font")
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasTestTag("price-timeline"))
        compose.onNodeWithTag("price-timeline").performScrollTo()
        screenshot("electricity-chart-large-font")
    }

    @Test fun `wide price bars scroll independently keep axis fixed and select exact interval`() {
        var state by mutableStateOf(PreviewData.state)
        compose.setContent {
            AppTheme(dark = true, dynamic = false) { AppScreen(state, true, {}, {}, {}, {}, {},
                onResolution = { state = state.copy(resolution = it) }) }
        }
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasTestTag("price-timeline"))
        compose.onNodeWithTag("price-timeline").performScrollTo()
        val axisBefore = compose.onNodeWithTag("price-axis").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("price-bar-0").assertWidthIsAtLeast(44.dp)
        // First price is negative; a tap anywhere in the column must still select it.
        compose.onNodeWithTag("price-bar-0").performTouchInput { click(center) }
        compose.onNodeWithTag("selected-price").assertTextContains("00.00–00.15", substring = true)
        compose.onNodeWithTag("price-timeline").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("selected-price").assertTextContains("00.00–00.15", substring = true)
        val axisAfter = compose.onNodeWithTag("price-axis").getUnclippedBoundsInRoot()
        assertEquals(axisBefore.left.value, axisAfter.left.value, 0.1f)
        assertEquals(axisBefore.right.value, axisAfter.right.value, 0.1f)
        compose.onNodeWithTag("price-timeline").performScrollToIndex(95)
        compose.onNodeWithTag("price-bar-95").performTouchInput { click(center) }
        compose.onNodeWithTag("selected-price").assertTextContains("23.45–00.00", substring = true)
        screenshot("electricity-chart-scrolled")
        compose.runOnIdle { state = state.copy(resolution = Resolution.HOUR) }
        compose.onNodeWithTag("price-timeline").performScrollToIndex(23)
        compose.onNodeWithTag("price-bar-23").performClick()
        compose.onNodeWithTag("selected-price").assertTextContains("23.00–00.00", substring = true)
    }

    @Test fun `weather blocks stack full width and observation gets its own block`() {
        var state by mutableStateOf(PreviewData.state)
        compose.setContent { AppTheme(dark = true, dynamic = false) { AppScreen(state, true, {}, {}, {}, {}, {}, {}) } }
        val a = compose.onNodeWithTag("current-FMI").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("current-OPEN_METEO").getUnclippedBoundsInRoot()
        assertEquals((a.right - a.left).value, (b.right - b.left).value, 0.1f)
        assert(b.top >= a.bottom) { "Open-Meteo-lohkon pitää olla FMI-lohkon alla" }
        compose.onNodeWithTag("current-time").assertTextEquals("Nyt 12.10")
        compose.runOnIdle { state = state.copy(now = state.now.plusSeconds(60)) }
        compose.onNodeWithTag("current-time").assertTextEquals("Nyt 12.11")
        compose.onNodeWithText("Lähimmän FMI-aseman havainto").assertDoesNotExist()
        compose.runOnIdle {
            val fmi = state.weather.getValue(WeatherSource.FMI)
            state = state.copy(weather = state.weather + (WeatherSource.FMI to fmi.copy(
                forecast = fmi.forecast!!.copy(observation = WeatherHour(state.now, 15.0)),
                error = "Päivitys epäonnistui. Näytetään viimeisin onnistunut ennuste.")))
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Lähimmän FMI-aseman havainto"))
        compose.onNodeWithText("Lähimmän FMI-aseman havainto").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Tuuli · m/s"))
        compose.onNodeWithText("Tuuli · m/s").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(weather = state.weather + (WeatherSource.OPEN_METEO to SourceState())) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("current-OPEN_METEO"))
        compose.onNodeWithTag("current-OPEN_METEO").assertIsDisplayed()
    }

    @Test fun `weekly arrow expands and collapses day and solar source prefers FMI`() {
        compose.setContent { AppTheme(dark = true, dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("sun-source").assertTextEquals("Ilmatieteen laitos")
        val tag = "day-2026-09-16"
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Suljettu"))
        compose.onNodeWithTag(tag).assertTextContains("Ilmatieteen laitos", substring = true)
        compose.onNodeWithTag(tag).assertTextContains("Open-Meteo", substring = true)
        screenshot("week-dark")
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Avattu"))
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Suljettu"))
    }

    @Test fun `VAT switch updates visible price and stays selected across tabs`() {
        val preview = PreviewData.state
        val hour = preview.now.truncatedTo(ChronoUnit.HOURS)
        val quarters = preview.prices!!.quarters.map { q ->
            val index = ChronoUnit.MINUTES.between(hour, q.start) / 15
            if (q.start >= hour && q.start < hour.plusSeconds(3600))
                q.copy(euroPerMwh = BigDecimal(listOf("90", "150", "210", "270")[index.toInt()])) else q
        }
        var state by mutableStateOf(preview.copy(prices = preview.prices.copy(quarters = quarters)))
        compose.setContent {
            AppTheme(dynamic = false) { AppScreen(state, true, {}, {}, {}, {}, {},
                onResolution = { state = state.copy(resolution = it) }, onVat = { state = state.copy(includeVat = it) }) }
        }
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithTag("current-price-level").assertTextEquals("Melko kallista")
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasTestTag("vat-switch"))
        compose.onNodeWithTag("vat-switch").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasTestTag("current-price"))
        compose.onNodeWithTag("current-price-level").assertTextEquals("Kohtuullista")
        val date = state.now.atZone(HELSINKI).toLocalDate()
        val net = Prices.slots(state.prices!!.quarters, date, Resolution.QUARTER, false).first { it.contains(state.now) }
        compose.onNodeWithTag("current-price").assertTextEquals(Prices.format(net.centsPerKwh))
        compose.onNodeWithText("Veroton hinta · ALV 0 %").assertIsDisplayed()
        screenshot("electricity-vat-off")
        compose.onNodeWithText("Tunti").performClick()
        compose.onNodeWithTag("current-price-level").assertTextEquals("Todella kallista")
        val hourly = Prices.slots(state.prices!!.quarters, date, Resolution.HOUR, false).first { it.contains(state.now) }
        compose.onNodeWithTag("current-price").assertTextEquals(Prices.format(hourly.centsPerKwh))
        compose.onNodeWithTag("tab-weather").performClick()
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasTestTag("vat-switch"))
        compose.onNodeWithTag("vat-switch").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasTestTag("current-price"))
        compose.onNodeWithTag("current-price-level").assertTextEquals("Poikkeuksellisen kallista")
        val gross = Prices.slots(state.prices!!.quarters, date, Resolution.HOUR, true).first { it.contains(state.now) }
        compose.onNodeWithTag("current-price").assertTextEquals(Prices.format(gross.centsPerKwh))
    }

    @Test fun `warnings tab shows local future warnings and notification settings in both themes`() {
        val app = PreviewData.state
        val polygon = listOf(GeoPoint(60.0,24.0),GeoPoint(61.0,24.0),GeoPoint(61.0,26.0),GeoPoint(60.0,26.0),GeoPoint(60.0,24.0))
        val warning = WeatherWarning("preview", "rain", "Sadevaroitus", WarningLevel.ORANGE,
            app.now.plusSeconds(86400),app.now.plusSeconds(120000),"Voimakasta sadetta. Testien esimerkkivaroitus.","",
            listOf(WarningArea("Uusimaa",listOf(polygon))),app.now)
        var dark by mutableStateOf(true)
        var warningFontScale by mutableFloatStateOf(1f)
        var warnings by mutableStateOf(WarningsState(snapshot=WarningSnapshot(app.now,app.now,listOf(warning))))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, warningFontScale)) {
                AppTheme(dark=dark,dynamic=false) { AppScreen(app,true,{},{},{},{},{},{},warnings=warnings,
                    onWarningsInterval={ warnings=warnings.copy(intervalMinutes=it) }) }
            }
        }
        compose.onNodeWithTag("tab-warnings").performClick()
        screenshot("warnings-dark")
        compose.onNodeWithText("1 varoitus").assertIsDisplayed()
        compose.onNodeWithTag("warnings-scroll").performScrollToNode(hasText("Sadevaroitus"))
        compose.onNodeWithText("Sadevaroitus").assertIsDisplayed()
        compose.onNodeWithText("Uusimaa").assertIsDisplayed()
        compose.onNodeWithText("Oranssi · Tulossa").assertIsDisplayed()
        screenshot("warnings-future-dark")
        compose.runOnIdle { dark=false }
        screenshot("warnings-future-light")
        compose.onNodeWithTag("warnings-scroll").performScrollToIndex(1)
        compose.onNodeWithTag("warning-day-2026-09-16").performClick()
        compose.onNodeWithTag("warnings-scroll").performScrollToNode(hasText("Tälle päivälle ei ole julkaistu alueesi varoituksia."))
        compose.onNodeWithText("Tälle päivälle ei ole julkaistu alueesi varoituksia.").assertIsDisplayed()
        compose.onNodeWithText("Sadevaroitus").assertDoesNotExist()
        compose.onNodeWithTag("warnings-scroll").performScrollToIndex(1)
        compose.onNodeWithTag("warning-day-2026-09-17").performClick()
        compose.onNodeWithTag("warnings-scroll").performScrollToNode(hasText("Sadevaroitus"))
        compose.onNodeWithText("Sadevaroitus").assertIsDisplayed()
        compose.onNodeWithTag("warnings-scroll").performScrollToNode(hasText("Ilmoitusasetukset · 30 min ↓"))
        compose.onNodeWithText("Ilmoitusasetukset · 30 min ↓").performClick()
        compose.onNodeWithText("60 min").performScrollTo().performClick()
        assertEquals(60,warnings.intervalMinutes)
        compose.runOnIdle { warningFontScale = 1.6f }
        compose.onNodeWithTag("warnings-scroll").performScrollToIndex(1)
        compose.onNodeWithTag("warning-days").performScrollToIndex(2)
        screenshot("warnings-days-large-font-light")
        compose.runOnIdle { dark = true }
        screenshot("warnings-days-large-font-dark")
    }

    @Test fun `price alert switch is independent and notification opens its delivery day`() {
        var alerts by mutableStateOf(PriceAlertState(allowed=true))
        var request by mutableIntStateOf(0)
        var date by mutableStateOf("2026-09-17")
        compose.setContent { AppTheme(dynamic=false) { AppScreen(PreviewData.state,true,{},{},{},{},{},{},
            priceAlerts=alerts,onPriceAlerts={ alerts=alerts.copy(enabled=it) },pricesRequest=request,priceDateRequest=date) } }
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasTestTag("price-alert-switch"))
        compose.onNodeWithTag("price-alert-switch").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { request++ }
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText("Torstaina 17.9."))
        compose.onNodeWithText("Torstaina 17.9.").assertIsDisplayed()
        // A notification opened on its delivery day must select today, not the following day.
        compose.runOnIdle { date="2026-09-16"; request++ }
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasText("Keskiviikkona 16.9."))
        compose.onNodeWithText("Keskiviikkona 16.9.").assertIsDisplayed()
    }

    @Test fun `place search favorites and return to current location work without changing device place`() {
        val current = PreviewData.state.place!!
        val porvoo = PlaceResult("P_10125180", "Porvoo", "Porvoo", 60.3953719, 25.6665595, "Kunta")
        var state by mutableStateOf(PreviewData.state.copy(devicePlace = current))
        compose.setContent {
            AppTheme(dark = false, dynamic = false) {
                AppScreen(state, true, {}, {}, {}, {}, {}, {},
                    onPlaceSearch = { state = state.copy(searchQuery = it, searchResults = if (it.length >= 2) listOf(porvoo) else emptyList()) },
                    onPlaceSelect = { state = state.copy(selectedPlace = it, place = it.place(state.now)) },
                    onPlaceFavorite = { state = state.copy(favorites = if (state.favorites.isEmpty()) listOf(it) else emptyList()) },
                    onCurrentLocation = { state = state.copy(selectedPlace = null, place = current) })
            }
        }
        compose.onNodeWithTag("open-place-search").performClick()
        compose.onNodeWithTag("place-search").performTextInput("Porvoo")
        compose.onNodeWithContentDescription("Lisää suosikiksi Porvoo").performClick()
        assertEquals(listOf(porvoo), state.favorites)
        screenshot("place-search")
        compose.onAllNodesWithTag("place-choice-${porvoo.id}").onFirst().performClick()
        compose.onNodeWithText("VALITTU PAIKKA").assertIsDisplayed()
        assertEquals(current, state.devicePlace)
        screenshot("selected-place")
        compose.onNodeWithText("Nykyinen sijainti").performClick()
        compose.onNodeWithText(current.name).assertIsDisplayed()
        compose.onNodeWithTag("open-place-search").performClick()
        compose.onNodeWithText("Suosikit").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Poista suosikki Porvoo").onFirst().performClick()
        assertEquals(emptyList<PlaceResult>(), state.favorites)
    }

    @Test fun `search drag does not refresh weather and closing preserves the expanded day`() {
        var refreshes = 0
        compose.setContent {
            AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, { refreshes++ }, {}, {}) }
        }
        val day = "day-2026-09-16"
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(day))
        compose.onNodeWithTag(day).performClick()
        compose.onNodeWithTag("open-place-search").performClick()
        compose.onNodeWithTag("place-results").performTouchInput { swipeDown() }
        assertEquals(0, refreshes)
        compose.onNodeWithText("Sulje").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(day))
        compose.onNodeWithTag(day).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Avattu"))
    }

    @Test fun `selected place without location permission has both empty warning states`() {
        val place = PlaceResult("porvoo", "Porvoo", "Porvoo", 60.39, 25.66)
        val state = PreviewData.state.copy(selectedPlace = place, place = place.place(PreviewData.now))
        compose.setContent {
            AppTheme(dynamic = false) { AppScreen(state, false, {}, {}, {}, {}, {}, {},
                warnings = WarningsState(snapshot = WarningSnapshot(state.now, state.now, emptyList()))) }
        }
        compose.onNodeWithTag("tab-warnings").performClick()
        compose.onNodeWithTag("warnings-scroll").performScrollToNode(hasText("FMI:n viimeisimmässä syötteessä ei ole tälle sijainnille voimassa olevia tai tulevia varoituksia."))
        compose.onNodeWithText("FMI:n viimeisimmässä syötteessä ei ole tälle sijainnille voimassa olevia tai tulevia varoituksia.").assertIsDisplayed()
        compose.onNodeWithTag("warnings-scroll").performScrollToIndex(1)
        compose.onNodeWithTag("warning-day-2026-09-16").performClick()
        compose.onNodeWithTag("warnings-scroll").performScrollToNode(hasText("Tälle päivälle ei ole julkaistu alueesi varoituksia."))
        compose.onNodeWithText("Tälle päivälle ei ole julkaistu alueesi varoituksia.").assertIsDisplayed()
    }

    @Test fun `price hero is a flat block and chart marks the current slot`() {
        compose.setContent { AppTheme(dark = false, dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("tab-prices").performClick()
        compose.onNodeWithTag("current-price").assertIsDisplayed()
        compose.onNodeWithText("Sisältää ALV 25,5 %").assertIsDisplayed()
        compose.onNodeWithTag("electricity-scroll").performScrollToNode(hasTestTag("price-timeline"))
        compose.onNodeWithTag("price-timeline").performScrollToNode(hasText("Nyt"))
        compose.onNodeWithText("Nyt").assertIsDisplayed()
    }

    @Test fun `floating navigation selects tabs and hides while place search is open`() {
        compose.setContent { AppTheme(dynamic = false) { AppScreen(PreviewData.state, true, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("tab-weather").assertIsSelected().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag("tab-prices").assertIsNotSelected().performClick().assertIsSelected()
        compose.onNodeWithText("Sähkön hinta").assertIsDisplayed()
        compose.onNodeWithTag("open-place-search").assertDoesNotExist()
        compose.onNodeWithTag("tab-warnings").performClick().assertIsSelected()
        compose.onNodeWithText("Tikkurila, Vantaa").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithTag("tab-weather").performClick()
        compose.onNodeWithTag("open-place-search").performClick()
        compose.onNodeWithTag("tab-weather").assertDoesNotExist()
        compose.onNodeWithText("Sulje").performClick()
        compose.onNodeWithTag("tab-weather").assertIsDisplayed()
    }

    private fun SemanticsNodeInteraction.assertTextFits(singleLine: Boolean = true) {
        assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertTrue("Text must expose its actual layout", layouts.isNotEmpty())
        layouts.forEach { layout ->
            val text = layout.layoutInput.text.text
            if (singleLine) assertEquals("Keep the text on one line: $text", 1, layout.lineCount)
            assertFalse("Ellipsized text: $text", layout.isLineEllipsized(0))
            assertEquals("All characters must be drawn: $text", text.length,
                layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            // Compare the drawn line, not the paragraph's possibly wider cached constraints.
            for (line in 0 until layout.lineCount) {
                assertFalse("Ellipsized line: $text", layout.isLineEllipsized(line))
                assertTrue("Clipped right edge: $text (${layout.getLineRight(line)} > ${layout.size.width}, " +
                    "scale=${layout.layoutInput.density.fontScale})", layout.getLineRight(line) <= layout.size.width + 1f)
                assertTrue("Clipped left edge: $text", layout.getLineLeft(line) >= -1f)
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File("build/screenshots/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Visual contact sheet rendered from the actual packaged Android drawables. */
    private fun launcherIconPreview() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(960, 370, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(237, 241, 245))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(36, 48, 60)
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        listOf("Värillinen", "Vaalea teema", "Tumma teema").forEachIndexed { index, title ->
            val icon = context.getDrawable(R.mipmap.ic_launcher)!!.mutate() as AdaptiveIconDrawable
            val drawable = if (index == 0) icon else {
                val mono = icon.monochrome!!.mutate()
                mono.setTint(Color.parseColor(if (index == 1) "#154D50" else "#9BD4D2"))
                AdaptiveIconDrawable(ColorDrawable(Color.parseColor(if (index == 1) "#C3E8E6" else "#183D40")), mono)
            }
            val left = index * 320
            drawable.setBounds(left + 56, 24, left + 264, 232)
            drawable.draw(canvas)
            // 48 px approximates the small launcher icon, without magnifying details.
            drawable.setBounds(left + 136, 260, left + 184, 308)
            drawable.draw(canvas)
            canvas.drawText(title, left + 160f, 349f, label)
        }
        val file = File("build/screenshots/launcher-icons.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
