package fi.omasaasahko.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.omasaasahko.AppState
import fi.omasaasahko.R
import fi.omasaasahko.data.PriceAlertState
import fi.omasaasahko.domain.*
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun ElectricityScreen(state: AppState, onResolution: (Resolution) -> Unit, scroll: LazyListState, onVat: (Boolean) -> Unit = {},
    alerts: PriceAlertState = PriceAlertState(), onAlerts: (Boolean) -> Unit = {}, onNotificationSettings: () -> Unit = {}, pricesRequest: Int = 0, priceDateRequest: String? = null,
    onAlertsTest: () -> Unit = {}, todayRows: List<PriceSlot> = rememberPriceRows(state, state.now.atZone(HELSINKI).toLocalDate())) {
    val language = LocalAppLanguage.current
    var tomorrow by rememberSaveable { mutableStateOf(false) }
    val today = state.now.atZone(HELSINKI).toLocalDate()
    LaunchedEffect(pricesRequest) { if (pricesRequest > 0) tomorrow = priceDateRequest == today.plusDays(1).toString() }
    val date = today.plusDays(if (tomorrow) 1 else 0)
    val rows = if (tomorrow) rememberPriceRows(state, date) else todayRows
    val current = todayRows.firstOrNull { it.contains(state.now) }
    val available = rows.filter { it.centsPerKwh != null }
    val minimum = available.minByOrNull { it.centsPerKwh!! }
    val maximum = available.maxByOrNull { it.centsPerKwh!! }
    var selected by remember(date, state.resolution) { mutableIntStateOf(-1) }
    val largeFont = LocalDensity.current.fontScale > 1.25f
    // VAT and alerts must stay reachable even when the day has no prices yet.
    val settings: LazyListScope.() -> Unit = {
        item(key = "price-settings") { PriceSettingsBlock(state, alerts, onVat, onAlerts, onNotificationSettings, onAlertsTest) }
    }
    LazyColumn(state = scroll, modifier = Modifier.testTag("electricity-scroll"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            val band = priceBand(current?.centsPerKwh)
            val colors = priceColors(band)
            val dark = isDarkTheme()
            Block(color = colors.top, contentColor = colors.ink, radius = Radius.hero, padding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(if (state.resolution == Resolution.QUARTER) R.string.current_quarter else R.string.current_hour_average), style = MaterialTheme.typography.labelMedium)
                    Text(current?.let { intervalLabel(it, language) } ?: stringResource(R.string.awaiting_data), style = MaterialTheme.typography.labelMedium)
                }
                Text(Prices.format(current?.centsPerKwh, language), style = MaterialTheme.typography.displayMedium, color = colors.accent, maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 40.sp, maxFontSize = 80.sp),
                    modifier = Modifier.padding(top = 6.dp).testTag("current-price"))
                FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    if (band != null) Box(Modifier.clip(PillShape).background(colors.accent).heightIn(min = 36.dp).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center) {
                        Text(stringResource(band.label), style = MaterialTheme.typography.labelLarge, color = if (dark) colors.bottom else Color.White,
                            modifier = Modifier.testTag("current-price-level"))
                    }
                    Column(horizontalAlignment = if (largeFont) Alignment.Start else Alignment.End) {
                        Text(stringResource(R.string.price_unit), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (state.includeVat) R.string.includes_vat else R.string.excludes_vat), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        state.pricesError?.let { item { Notice(it) } }
        item {
            val dayLabels = listOf(stringResource(R.string.today), stringResource(R.string.tomorrow))
            val unitLabels = Resolution.entries.map { stringResource(it.labelRes()) }
            val day: @Composable (Modifier) -> Unit = { ChoiceRow(dayLabels, if (tomorrow) 1 else 0, { tomorrow = it == 1 }, it) }
            val unit: @Composable (Modifier) -> Unit = { ChoiceRow(unitLabels, state.resolution.ordinal, { i -> onResolution(Resolution.entries[i]) }, it) }
            val selectorWidth = maxOf(choiceRowMinWidth(dayLabels), choiceRowMinWidth(unitLabels))
            val gap = 10.dp
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fits = (maxWidth - gap) / 2 >= selectorWidth
                if (largeFont || !fits) Column(verticalArrangement = Arrangement.spacedBy(gap)) { day(Modifier.fillMaxWidth()); unit(Modifier.fillMaxWidth()) }
                else Row(horizontalArrangement = Arrangement.spacedBy(gap)) { day(Modifier.weight(1f)); unit(Modifier.weight(1f)) }
            }
        }
        item {
            SectionTitle(language.day(date),
                stringResource(if (state.resolution == Resolution.QUARTER) R.string.quarter_prices_count else R.string.hour_averages_count, rows.size))
        }
        if (available.isEmpty()) {
            item {
                Notice(stringResource(if (state.pricesLoading) R.string.loading_prices else if (tomorrow && state.prices != null)
                    R.string.tomorrow_prices_unavailable else R.string.prices_unavailable))
            }
            settings()
        } else {
            if (available.size < rows.size) item { Notice(stringResource(R.string.prices_partial)) }
            item {
                val summaryWidth = priceSummaryMinWidth(listOf(minimum?.centsPerKwh, Prices.average(rows), maximum?.centsPerKwh))
                val gap = 10.dp
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (largeFont || (maxWidth - gap * 2) / 3 < summaryWidth) {
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            PriceSummary(stringResource(R.string.cheapest), minimum?.centsPerKwh, minimum?.start?.let { clockLabel(it, language) }, Modifier.fillMaxWidth(), true)
                            PriceSummary(stringResource(R.string.average_price), Prices.average(rows), stringResource(R.string.price_unit), Modifier.fillMaxWidth(), true)
                            PriceSummary(stringResource(R.string.most_expensive), maximum?.centsPerKwh, maximum?.start?.let { clockLabel(it, language) }, Modifier.fillMaxWidth(), true)
                        }
                    } else {
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            PriceSummary(stringResource(R.string.cheapest), minimum?.centsPerKwh, minimum?.start?.let { clockLabel(it, language) }, Modifier.weight(1f).fillMaxHeight())
                            PriceSummary(stringResource(R.string.average_price), Prices.average(rows), stringResource(R.string.price_unit), Modifier.weight(1f).fillMaxHeight())
                            PriceSummary(stringResource(R.string.most_expensive), maximum?.centsPerKwh, maximum?.start?.let { clockLabel(it, language) }, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
            item(key = "price-chart") {
                Block {
                    val picked = rows.getOrNull(selected)
                    val pickedBand = priceBand(picked?.centsPerKwh)
                    Text(picked?.let { "${intervalLabel(it, language)} · ${Prices.format(it.centsPerKwh, language)} ${stringResource(R.string.price_unit)}" } ?: stringResource(R.string.tap_bar_hint),
                        style = MaterialTheme.typography.titleMedium, color = priceColors(pickedBand).accent,
                        modifier = Modifier.testTag("selected-price"))
                    if (pickedBand != null) Text(stringResource(pickedBand.label), style = MaterialTheme.typography.labelMedium,
                        color = priceColors(pickedBand).accent)
                    key(date, state.resolution) { PriceChart(rows, selected, rows.indexOfFirst { it.contains(state.now) }, { selected = it }) }
                    PriceLegend()
                }
            }
            settings()
            item { SectionTitle(stringResource(if (state.resolution == Resolution.QUARTER) R.string.all_quarters else R.string.all_hours), stringResource(if (state.includeVat) R.string.unit_with_vat else R.string.unit_without_vat)) }
            items(rows, key = { "price-${it.start}" }) { row ->
                val isNow = row.contains(state.now)
                val band = priceBand(row.centsPerKwh)
                val colors = priceColors(band)
                Surface(shape = RoundedCornerShape(Radius.row), color = if (isNow) colors.top else colors.bottom, contentColor = colors.ink) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(intervalLabel(row, language), style = MaterialTheme.typography.bodyMedium)
                            Text(listOfNotNull(if (isNow) stringResource(R.string.now_caps) else null, band?.let { stringResource(it.label) }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = colors.accent)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(Prices.format(row.centsPerKwh, language), style = MaterialTheme.typography.titleLarge, color = colors.accent)
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.prices_footer, updatedLabel(state.prices?.fetchedAt, language),
                stringResource(if (state.includeVat) R.string.prices_footer_vat_included else R.string.prices_footer_vat_excluded)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun PriceSettingsBlock(state: AppState, alerts: PriceAlertState, onVat: (Boolean) -> Unit, onAlerts: (Boolean) -> Unit,
                               onNotificationSettings: () -> Unit, onAlertsTest: () -> Unit) {
    Block(radius = Radius.panel, padding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
        SettingSwitchRow(stringResource(R.string.vat_switch), stringResource(if (state.includeVat) R.string.vat_included_subtitle else R.string.vat_excluded_subtitle),
            state.includeVat, onVat, Modifier.testTag("vat-switch"))
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
        SettingSwitchRow(stringResource(R.string.price_alert_switch), stringResource(R.string.price_alert_subtitle), alerts.enabled, onAlerts,
            Modifier.testTag("price-alert-switch"))
        Text(stringResource(R.string.price_alert_info),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!alerts.allowed) TextButton(onClick = onNotificationSettings) { Text(stringResource(R.string.notification_settings)) }
        else if (alerts.enabled) {
            TextButton(onClick = onAlertsTest, enabled = !alerts.testPending) { Text(stringResource(R.string.test_notification)) }
            if (alerts.testPending) Text(stringResource(R.string.lock_phone_hint), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun intervalLabel(slot: PriceSlot, language: AppLanguage): String = "${clockLabel(slot.start, language)}–${clockLabel(slot.end, language)}"

private val SummaryPadding = 14.dp
private val SummaryMinFontSize = 14.sp

@Composable
private fun priceSummaryMinWidth(values: List<BigDecimal?>): androidx.compose.ui.unit.Dp {
    val language = LocalAppLanguage.current
    val measurer = rememberTextMeasurer()
    val width = values.maxOf { measurer.measure(Prices.format(it, language),
        MaterialTheme.typography.titleLarge.copy(fontSize = SummaryMinFontSize), softWrap = false).size.width }
    return with(LocalDensity.current) { width.toDp() } + SummaryPadding * 2
}

@Composable
private fun PriceSummary(label: String, value: BigDecimal?, footnote: String?, modifier: Modifier, horizontal: Boolean = false) {
    val language = LocalAppLanguage.current
    val colors = priceColors(priceBand(value))
    Surface(modifier = modifier, shape = RoundedCornerShape(Radius.tile), color = colors.top, contentColor = colors.ink) {
        if (horizontal) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Text(footnote ?: "–", style = MaterialTheme.typography.labelSmall)
                }
                Text(Prices.format(value, language), style = MaterialTheme.typography.titleLarge, color = colors.accent)
            }
        } else {
            Column(Modifier.padding(SummaryPadding)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(Prices.format(value, language), style = MaterialTheme.typography.titleLarge, color = colors.accent, maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = SummaryMinFontSize, maxFontSize = 22.sp), modifier = Modifier.padding(vertical = 4.dp))
                Text(footnote ?: "–", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun PriceChart(rows: List<PriceSlot>, selected: Int, nowIndex: Int, onSelected: (Int) -> Unit) {
    val language = LocalAppLanguage.current
    val axis = remember(rows) { priceAxis(rows) }
    val horizontalScroll = rememberLazyListState()
    val selectedColor = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.outlineVariant
    val labelHeight = with(LocalDensity.current) { 16.sp.toDp() }
    val plotHeight = 220.dp
    val cellWidth = 52.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp)
    val labelLines = remember(rows, language) { if (rows.any { '(' in clockLabel(it.start, language) }) 2 else 1 }
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.price_unit), style = labelStyle)
            Text(stringResource(R.string.scroll_sideways), style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // This column is outside the horizontal list, so prices remain visible while scrolling.
            Column(Modifier.width(IntrinsicSize.Max).height(plotHeight).padding(end = 10.dp).testTag("price-axis"),
                verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                axis.ticks.forEach { tick ->
                    Text(Prices.format(tick, language), style = labelStyle, modifier = Modifier.height(labelHeight))
                }
            }
            LazyRow(state = horizontalScroll, modifier = Modifier.weight(1f).testTag("price-timeline")) {
                itemsIndexed(rows, key = { _, row -> row.start.toString() }) { index, row ->
                    val color = priceColors(priceBand(row.centsPerKwh)).accent
                    val description = "${intervalLabel(row, language)} · ${Prices.format(row.centsPerKwh, language)} ${stringResource(R.string.price_unit)} · " +
                        (priceBand(row.centsPerKwh)?.let { stringResource(it.label) } ?: stringResource(R.string.price_missing))
                    Column(Modifier.width(cellWidth).testTag("price-bar-$index")
                        .selectable(selected = index == selected, role = Role.Button, onClick = { onSelected(index) })
                        .semantics(mergeDescendants = true) { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        // The entire column is tappable, including very short or negative bars.
                        Canvas(Modifier.fillMaxWidth().height(plotHeight).testTag("price-plot-$index")) {
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
                                val width = 36.dp.toPx()
                                val barHeight = if (value == 0.0) 1.dp.toPx() else kotlin.math.abs(y(value) - zero).coerceAtLeast(2.dp.toPx())
                                val top = when {
                                    value > 0 -> zero - barHeight
                                    value < 0 -> zero
                                    else -> zero - barHeight / 2f
                                }
                                val position = Offset((size.width - width) / 2f, top)
                                val barSize = Size(width, barHeight)
                                val corner = CornerRadius(minOf(12.dp.toPx(), barSize.height / 2f))
                                drawRoundRect(color, position, barSize, corner)
                                if (index == selected) drawRoundRect(selectedColor, position, barSize, corner, style = Stroke(2.dp.toPx()))
                            }
                        }
                        val isNow = index == nowIndex
                        val clock = clockLabel(row.start, language).replace(" (", "\n(")
                        // Reserve the longest clock label for every cell, including DST offsets.
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).height(labelHeight * labelLines + 4.dp),
                            contentAlignment = Alignment.TopCenter) {
                            Text(if (isNow) stringResource(R.string.now) else clock, style = labelStyle, textAlign = TextAlign.Center,
                                softWrap = false, maxLines = if (isNow) 1 else labelLines,
                                color = if (isNow) MaterialTheme.colorScheme.inverseOnSurface else if (index == selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (index == selected || isNow) FontWeight.Bold else FontWeight.Medium,
                                modifier = (if (isNow) Modifier.clip(PillShape).background(MaterialTheme.colorScheme.inverseSurface) else Modifier.fillMaxWidth())
                                    .padding(horizontal = if (isNow) 10.dp else 0.dp, vertical = 2.dp))
                        }
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
        Text(stringResource(R.string.price_colours), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp)) {
            PriceBand.entries.forEach { band ->
                val colors = priceColors(band)
                Surface(color = colors.top, contentColor = colors.accent, shape = PillShape) {
                    Text("${stringResource(band.range)} · ${stringResource(band.label)}", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}
