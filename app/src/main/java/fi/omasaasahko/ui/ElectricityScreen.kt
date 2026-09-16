package fi.omasaasahko.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.omasaasahko.AppState
import fi.omasaasahko.data.PriceAlertState
import fi.omasaasahko.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ElectricityScreen(state: AppState, onResolution: (Resolution) -> Unit, scroll: LazyListState, onVat: (Boolean) -> Unit = {},
    alerts: PriceAlertState = PriceAlertState(), onAlerts: (Boolean) -> Unit = {}, onNotificationSettings: () -> Unit = {}, pricesRequest: Int = 0, priceDateRequest: String? = null) {
    var tomorrow by rememberSaveable { mutableStateOf(false) }
    val today = state.now.atZone(HELSINKI).toLocalDate()
    LaunchedEffect(pricesRequest) { if (pricesRequest > 0) tomorrow = priceDateRequest == today.plusDays(1).toString() }
    val date = today.plusDays(if (tomorrow) 1 else 0)
    val rows = remember(state.prices, date, state.resolution, state.includeVat) { Prices.slots(state.prices?.quarters.orEmpty(), date, state.resolution, state.includeVat) }
    val current = remember(state.prices, today, state.resolution, state.now, state.includeVat) {
        Prices.slots(state.prices?.quarters.orEmpty(), today, state.resolution, state.includeVat).firstOrNull { it.contains(state.now) }
    }
    val available = rows.filter { it.centsPerKwh != null }
    val minimum = available.minByOrNull { it.centsPerKwh!! }
    val maximum = available.maxByOrNull { it.centsPerKwh!! }
    var selected by remember(date, state.resolution) { mutableIntStateOf(-1) }
    val largeFont = LocalDensity.current.fontScale > 1.25f
    LazyColumn(state = scroll, modifier = Modifier.testTag("electricity-scroll"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Text("SUOMEN HINTA-ALUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("Sähkön hinta", style = MaterialTheme.typography.headlineLarge)
                Text(if (state.includeVat) "Sisältää ALV 25,5 %" else "Veroton hinta · ALV 0 %", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Haettu ${updatedLabel(state.prices?.fetchedAt)} · Elering", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            val band = priceBand(current?.centsPerKwh)
            val colors = priceColors(band)
            Surface(shape = RoundedCornerShape(30.dp), color = Color.Transparent, contentColor = colors.ink,
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.25f))) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(colors.top, colors.bottom))).padding(24.dp)) {
                    Text(if (state.resolution == Resolution.QUARTER) "NYKYINEN VARTTI" else "NYKYISEN TUNNIN KESKIHINTA", style = MaterialTheme.typography.labelSmall)
                    Text(Prices.format(current?.centsPerKwh), style = MaterialTheme.typography.displayLarge, color = colors.accent,
                        modifier = Modifier.padding(top = 10.dp).testTag("current-price"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("snt/kWh", style = MaterialTheme.typography.titleMedium)
                        Text(current?.let { intervalLabel(it) } ?: "Tietoa odotetaan", style = MaterialTheme.typography.labelMedium)
                    }
                    if (band != null) Surface(color = colors.accent.copy(alpha = 0.12f), contentColor = colors.accent,
                        shape = RoundedCornerShape(50), modifier = Modifier.padding(top = 16.dp)) {
                        Text(band.label, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).testTag("current-price-level"))
                    }
                }
            }
        }
        state.pricesError?.let { item { Notice(it) } }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ilmoita huomisen hinnat", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(checked=alerts.enabled,onCheckedChange=onAlerts,modifier=Modifier.testTag("price-alert-switch"))
                    }
                    Text("Keskihinta sekä halvin ja kallein tunti. Ilmoitus kerran, kun kaikki huomisen hinnat ovat saatavilla.",style=MaterialTheme.typography.bodySmall)
                    Text("Taustatarkistus 30 min välein. Android voi viivästyttää ilmoitusta.",style=MaterialTheme.typography.bodySmall)
                    if (!alerts.allowed) TextButton(onClick=onNotificationSettings) { Text("Ilmoitusasetukset") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().testTag("vat-switch")
                .toggleable(value = state.includeVat, role = Role.Switch, onValueChange = onVat)
                .padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ALV 25,5 %", style = MaterialTheme.typography.titleMedium)
                    Text(if (state.includeVat) "Mukana hinnoissa" else "Hinnat ilman ALV:tä", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.includeVat, onCheckedChange = null)
            }
        }
        item {
            ChoiceRow(listOf("Tänään", "Huomenna"), if (tomorrow) 1 else 0, { tomorrow = it == 1 }, Modifier.fillMaxWidth())
            ChoiceRow(Resolution.entries.map { it.label }, state.resolution.ordinal, { onResolution(Resolution.entries[it]) }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        item {
            SectionTitle(date.format(DateTimeFormatter.ofPattern("EEEE d.M.", FINNISH)).replaceFirstChar { it.titlecase(FINNISH) },
                if (state.resolution == Resolution.QUARTER) "Varttihinnat · ${rows.size} varttia" else "Tuntikeskiarvot · ${rows.size} tuntia")
        }
        if (available.isEmpty()) item {
            Notice(if (state.pricesLoading) "Haetaan hintoja…" else if (tomorrow && state.prices != null)
                "Huomisen hinnat eivät ole vielä saatavilla. Ne ilmestyvät tähän, kun ne julkaistaan hintapalvelussa."
                else "Hintoja ei ole saatavilla. Päivitä näkymä tai tarkista verkkoyhteys.")
        } else {
            if (available.size < rows.size) item { Notice("Päivän hinnoista puuttuu tietoja. Minimi ja maksimi koskevat saatavilla olevia hintoja. Päivän keskihinta näytetään vasta, kun kaikki hinnat ovat saatavilla.") }
            item {
                if (largeFont) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PriceSummary("Halvin", minimum?.centsPerKwh, minimum?.start?.let(::clockLabel), Modifier.fillMaxWidth(), true)
                        PriceSummary("Keskihinta", Prices.average(rows), "snt/kWh", Modifier.fillMaxWidth(), true)
                        PriceSummary("Kallein", maximum?.centsPerKwh, maximum?.start?.let(::clockLabel), Modifier.fillMaxWidth(), true)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PriceSummary("Halvin", minimum?.centsPerKwh, minimum?.start?.let(::clockLabel), Modifier.weight(1f))
                        PriceSummary("Keskihinta", Prices.average(rows), "snt/kWh", Modifier.weight(1f))
                        PriceSummary("Kallein", maximum?.centsPerKwh, maximum?.start?.let(::clockLabel), Modifier.weight(1f))
                    }
                }
            }
            item(key = "price-chart") {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(16.dp)) {
                        val picked = rows.getOrNull(selected)
                        val pickedBand = priceBand(picked?.centsPerKwh)
                        Text(picked?.let { "${intervalLabel(it)} · ${Prices.format(it.centsPerKwh)} snt/kWh" } ?: "Paina pylvästä nähdäksesi hinnan",
                            style = MaterialTheme.typography.bodyMedium, color = priceColors(pickedBand).accent,
                            modifier = Modifier.testTag("selected-price"))
                        if (pickedBand != null) Text(pickedBand.label, style = MaterialTheme.typography.labelSmall,
                            color = priceColors(pickedBand).accent)
                        key(date, state.resolution) { PriceChart(rows, selected, { selected = it }) }
                        PriceLegend()
                    }
                }
            }
            item { SectionTitle(if (state.resolution == Resolution.QUARTER) "Kaikki vartit" else "Kaikki tunnit", if (state.includeVat) "snt/kWh · ALV 25,5 %" else "snt/kWh · ALV 0 %") }
            items(rows, key = { "price-${it.start}" }) { row ->
                val isNow = row.contains(state.now)
                val band = priceBand(row.centsPerKwh)
                val colors = priceColors(band)
                Surface(shape = RoundedCornerShape(18.dp), color = if (isNow) colors.top else colors.bottom, contentColor = colors.ink,
                    border = if (isNow) BorderStroke(1.dp, colors.accent) else null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(intervalLabel(row), style = MaterialTheme.typography.bodyMedium)
                            Text(listOfNotNull(if (isNow) "NYT" else null, band?.label).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = colors.accent)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(Prices.format(row.centsPerKwh), style = MaterialTheme.typography.titleLarge, color = colors.accent)
                    }
                }
            }
        }
        item {
            Text("Päivitetty ${updatedLabel(state.prices?.fetchedAt)}\nLähde: Elering / Nord Pool, Suomi. " +
                (if (state.includeVat) "Hinta sisältää ALV:n 25,5 %. " else "Hinta on veroton (ALV 0 %). ") +
                "Hinta ei sisällä myyjän marginaalia eikä sähkönsiirtoa. Kellonajat ovat Suomen aikaa.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

private fun intervalLabel(slot: PriceSlot): String = "${clockLabel(slot.start)}–${clockLabel(slot.end)}"

@Composable
private fun PriceSummary(label: String, value: BigDecimal?, footnote: String?, modifier: Modifier, horizontal: Boolean = false) {
    val colors = priceColors(priceBand(value))
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = colors.top, contentColor = colors.ink) {
        if (horizontal) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Text(footnote ?: "–", style = MaterialTheme.typography.labelSmall)
                }
                Text(Prices.format(value), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.accent)
            }
        } else {
            Column(Modifier.padding(12.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(Prices.format(value), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.accent, modifier = Modifier.padding(vertical = 7.dp))
                Text(footnote ?: "–", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PriceChart(rows: List<PriceSlot>, selected: Int, onSelected: (Int) -> Unit) {
    val axis = remember(rows) { priceAxis(rows) }
    val horizontalScroll = rememberLazyListState()
    val selectedColor = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.outlineVariant
    val labelHeight = with(LocalDensity.current) { 16.sp.toDp() }
    val plotHeight = 220.dp
    val cellWidth = 52.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp)
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("snt/kWh", style = labelStyle)
            Text("Vieritä sivulle ↔", style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // This column is outside the horizontal list, so prices remain visible while scrolling.
            Column(Modifier.width(IntrinsicSize.Max).height(plotHeight).padding(end = 10.dp).testTag("price-axis"),
                verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                axis.ticks.forEach { tick ->
                    Text(Prices.format(tick), style = labelStyle, modifier = Modifier.height(labelHeight))
                }
            }
            LazyRow(state = horizontalScroll, modifier = Modifier.weight(1f).testTag("price-timeline")) {
                itemsIndexed(rows, key = { _, row -> row.start.toString() }) { index, row ->
                    val color = priceColors(priceBand(row.centsPerKwh)).accent
                    val description = "${intervalLabel(row)} · ${Prices.format(row.centsPerKwh)} snt/kWh" +
                        (priceBand(row.centsPerKwh)?.let { " · ${it.label}" } ?: " · Hintatieto puuttuu")
                    Column(Modifier.width(cellWidth).testTag("price-bar-$index")
                        .selectable(selected = index == selected, role = Role.Button, onClick = { onSelected(index) })
                        .semantics(mergeDescendants = true) { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        // The entire column is tappable, including very short or negative bars.
                        Canvas(Modifier.fillMaxWidth().height(plotHeight)) {
                            val inset = labelHeight.toPx() / 2f
                            val height = size.height - 2f * inset
                            val span = axis.high - axis.low
                            fun y(value: Double) = inset + ((axis.high - value) / span * height).toFloat()
                            axis.ticks.forEach { tick ->
                                val lineY = y(tick.toDouble())
                                drawLine(guide, Offset(0f, lineY), Offset(size.width, lineY), 1.dp.toPx(),
                                    pathEffect = if (tick.signum() == 0) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                            }
                            val zero = y(0.0)
                            val value = row.centsPerKwh?.toDouble()
                            if (value == null) drawCircle(guide, 3.dp.toPx(), Offset(size.width / 2f, zero))
                            else {
                                val width = 44.dp.toPx()
                                val position = Offset((size.width - width) / 2f, minOf(y(value), zero))
                                val barSize = Size(width, kotlin.math.abs(y(value) - zero).coerceAtLeast(2.dp.toPx()))
                                drawRect(color, position, barSize)
                                if (index == selected) drawRect(selectedColor, position, barSize, style = Stroke(2.dp.toPx()))
                            }
                        }
                        Text(clockLabel(row.start).replace(" (", "\n("), style = labelStyle, textAlign = TextAlign.Center,
                            color = if (index == selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp))
                    }
                }
            }
        }
    }
}

private data class PriceAxis(val low: Double, val high: Double, val ticks: List<BigDecimal>)

/** Rounded, evenly spaced ticks include zero and enclose every price for the whole day. */
private fun priceAxis(rows: List<PriceSlot>): PriceAxis {
    val values = rows.mapNotNull { it.centsPerKwh }
    val low = (values.minOrNull() ?: BigDecimal.ZERO).min(BigDecimal.ZERO)
    val high = (values.maxOrNull() ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
    val roughStep = (high - low).max(BigDecimal("0.004")).divide(BigDecimal(4)).stripTrailingZeros()
    val magnitude = BigDecimal.ONE.scaleByPowerOfTen(roughStep.precision() - roughStep.scale() - 1)
    val step = listOf(1, 2, 5, 10).map { magnitude.multiply(BigDecimal(it)) }.first { it >= roughStep }
    val lower = low.divide(step, 0, RoundingMode.FLOOR).multiply(step)
    val upper = high.divide(step, 0, RoundingMode.CEILING).multiply(step).max(lower + step)
    val count = (upper - lower).divide(step).toInt()
    return PriceAxis(lower.toDouble(), upper.toDouble(), (0..count).map { upper - step.multiply(BigDecimal(it)) })
}

@Composable
private fun PriceLegend() {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text("Hintavärit · snt/kWh", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp)) {
            PriceBand.entries.forEach { band ->
                val colors = priceColors(band)
                Surface(color = colors.top, contentColor = colors.accent, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp).semantics(mergeDescendants = true) {}) {
                        Text(band.range, style = MaterialTheme.typography.labelMedium)
                        Text(band.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
