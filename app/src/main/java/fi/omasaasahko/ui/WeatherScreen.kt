package fi.omasaasahko.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import fi.omasaasahko.*
import fi.omasaasahko.R
import fi.omasaasahko.domain.*
import java.time.*
import java.time.temporal.ChronoUnit

@Composable
fun WeatherScreen(state: AppState, permitted: Boolean, onPermission: () -> Unit, onSettings: () -> Unit,
                  onLocationSettings: () -> Unit, scroll: LazyListState,
                  onCurrentLocation: () -> Unit = {}) {
    var expandedHours by rememberSaveable { mutableStateOf(false) }
    var expandedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var rainSource by rememberSaveable { mutableIntStateOf(0) }
    val largeFont = LocalDensity.current.fontScale > 1.25f
    val language = LocalAppLanguage.current
    val fmi = state.weather[WeatherSource.FMI]?.forecast
    val meteo = state.weather[WeatherSource.OPEN_METEO]?.forecast
    val zone = meteo?.zone ?: HELSINKI
    val today = state.now.atZone(zone).toLocalDate()
    val nextHour = state.now.truncatedTo(ChronoUnit.HOURS).plusSeconds(3600)
    val times = (0 until 24).map { nextHour.plusSeconds(it * 3600L) }
    val fmiHours = remember(fmi) { fmi?.hours?.associateBy { it.time }.orEmpty() }
    val meteoHours = remember(meteo) { meteo?.hours?.associateBy { it.time }.orEmpty() }
    val timeMeasurer = rememberTextMeasurer()
    val timeStyle = MaterialTheme.typography.labelMedium
    val timeWidthPx = remember(times, fmiHours, meteoHours, zone, timeStyle, timeMeasurer, language, LocalDensity.current) {
        (times + fmiHours.keys + meteoHours.keys).maxOf {
            timeMeasurer.measure(clockLabel(it, language, zone), timeStyle, softWrap = false).size.width
        }
    }
    val timeWidth = with(LocalDensity.current) { timeWidthPx.toDp().coerceAtLeast(44.dp) }
    LazyColumn(state = scroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text(stringResource(if (state.selectedPlace != null) R.string.selected_place else if (state.locating) R.string.locating else R.string.your_location), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                val selected = state.selectedPlace
                if (selected != null) {
                    val favorite = state.favorites.any { it.id == selected.id }
                    val favouriteDescription = stringResource(R.string.favourite_description, selected.label)
                    Text((if (favorite) "★ " else "") + selected.label, style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("selected-place-name")
                            .then(if (favorite) Modifier.semantics { contentDescription = favouriteDescription } else Modifier))
                    Text(stringResource(R.string.place_source, selected.kind.ifBlank { stringResource(R.string.place_kind_search) }), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else state.place?.nearbyName?.let { name ->
                    Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.nearest_place_name), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.selectedPlace == null) {
                    if (state.namingLocation) Text(stringResource(R.string.naming_location), style = MaterialTheme.typography.bodySmall)
                    state.place?.nearbyDistanceMeters?.takeIf { it > fi.omasaasahko.data.PlaceNames.RADIUS_METERS }?.let {
                        Text(stringResource(R.string.name_point_distance, decimal(it / 1000, language)), style = MaterialTheme.typography.bodySmall)
                    }
                    state.place?.accuracyMeters?.takeIf { it > 200 }?.let {
                        Text(stringResource(R.string.approximate_location, decimal(it.toDouble(), language, 0)), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (state.selectedPlace != null) TextButton(onClick = {
                    onCurrentLocation(); if (!permitted) onPermission()
                }) { Text(stringResource(R.string.back_to_current_location)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)) {
                    Text(language.longDay(today),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.now_time, clockLabel(state.now, language, zone)), color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("current-time"))
                }
            }
        }
        if (!permitted && state.selectedPlace == null) item {
            Notice(stringResource(R.string.location_permission_notice), stringResource(R.string.allow_location), onPermission)
            TextButton(onClick = onSettings) { Text(stringResource(R.string.open_app_settings)) }
        }
        state.locationError?.takeIf { state.selectedPlace == null }?.let { error -> item {
            Notice(error.text(), stringResource(R.string.location_settings), onLocationSettings)
        } }
        if (state.selectedPlace == null && state.place != null && (!permitted || state.locationError != null)) item {
            Text(stringResource(R.string.last_location, updatedLabel(state.place.locatedAt, language)), style = MaterialTheme.typography.bodySmall)
        }
        items(WeatherSource.entries, key = { "current-$it" }) { source ->
            CurrentBlock(source, state.weather.getValue(source), state.now, zone, largeFont,
                Modifier.fillMaxWidth().testTag("current-${source.name}"))
        }
        item {
            val a = fmi?.current(state.now); val b = meteo?.current(state.now)
            val tiles: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> MetricTile(stringResource(R.string.wind), "m/s", decimal(a?.wind, language), decimal(b?.wind, language), m) },
                { m -> MetricTile(stringResource(R.string.rain), "mm", decimal(a?.rain, language), decimal(b?.rain, language), m) },
                { m -> MetricTile(stringResource(R.string.rain_probability), "%", decimal(a?.rainProbability, language, 0), decimal(b?.rainProbability, language, 0), m) },
            )
            if (largeFont) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
            else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
        fmi?.recentObservation(state.now)?.let { observed -> item {
            Block(radius = Radius.tile, padding = PaddingValues(16.dp)) {
                Text(stringResource(R.string.nearest_station_observation), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                fmi.observationStation?.let { station ->
                    Text(station.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.station_distance, decimal(station.distanceMeters / 1000, language)), style = MaterialTheme.typography.bodySmall)
                }
                Text("${temperature(observed.temperature, language)} · ${clockLabel(observed.time, language, zone)}", style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.wind_speed, decimal(observed.wind, language)), style = MaterialTheme.typography.bodySmall)
            }
        } }
        val fmiSun = fmi?.days?.firstOrNull { it.date == today && (it.sunrise != null || it.sunset != null) }
        val sun = fmiSun ?: meteo?.days?.firstOrNull { it.date == today }
        if (sun != null) item {
            val colors = sunColors()
            val rise = sun.sunrise?.let { clockLabel(it, language, zone) } ?: "–"
            val set = sun.sunset?.let { clockLabel(it, language, zone) } ?: "–"
            Block(color = colors.top, contentColor = colors.ink, radius = Radius.panel, padding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val sourceName = stringResource(if (fmiSun != null) R.string.source_fmi else R.string.source_open_meteo)
                    val sunLabel = stringResource(R.string.sun)
                    val sunriseDescription = stringResource(R.string.sunrise_description, rise)
                    val sunsetDescription = stringResource(R.string.sunset_description, set)
                    val measurer = rememberTextMeasurer()
                    val timesWidth = listOf("↑ $rise", "↓ $set").sumOf {
                        measurer.measure(it, MaterialTheme.typography.headlineSmall, softWrap = false).size.width
                    }
                    val sourceWidth = maxOf(
                        measurer.measure(sunLabel, MaterialTheme.typography.labelMedium, softWrap = false).size.width,
                        measurer.measure(sourceName, MaterialTheme.typography.labelSmall, softWrap = false).size.width)
                    val gap = 8.dp
                    val sourceAbove = largeFont || with(LocalDensity.current) { (maxWidth - gap * 2).roundToPx() < timesWidth + sourceWidth }
                    val source: @Composable () -> Unit = {
                        Column(horizontalAlignment = if (sourceAbove) Alignment.Start else Alignment.CenterHorizontally) {
                            Text(sunLabel, style = MaterialTheme.typography.labelMedium, color = colors.muted)
                            Text(sourceName, style = MaterialTheme.typography.labelSmall,
                                color = colors.muted, modifier = Modifier.testTag("sun-source"))
                        }
                    }
                    Column {
                        if (sourceAbove) source()
                        // At very large font sizes the complete times can also move onto separate lines.
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            Text("↑ $rise", style = MaterialTheme.typography.headlineSmall, color = colors.accent, softWrap = false,
                                modifier = Modifier.semantics { contentDescription = sunriseDescription })
                            if (!sourceAbove) source()
                            Text("↓ $set", style = MaterialTheme.typography.headlineSmall, color = colors.accent, softWrap = false,
                                modifier = Modifier.semantics { contentDescription = sunsetDescription })
                        }
                    }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.next_24_hours), stringResource(R.string.next_24_hours_subtitle)) }
        item { ProviderHeading(timeWidth) }
        items(if (expandedHours) times else times.take(8), key = { "hour-$it" }) { time ->
            HourComparison(time, fmiHours[time], meteoHours[time], zone, timeWidth)
        }
        item {
            TextButton(onClick = { expandedHours = !expandedHours }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (expandedHours) R.string.show_less else R.string.show_all_24_hours))
            }
        }
        item {
            val source = if (rainSource == 0) WeatherSource.FMI else WeatherSource.OPEN_METEO
            RainBlock(times.map { if (rainSource == 0) fmiHours[it]?.rain else meteoHours[it]?.rain }, times, zone, source) {
                ChoiceRow(listOf("FMI", "Open-Meteo"), rainSource, { rainSource = it }, Modifier.fillMaxWidth())
            }
        }
        item { SectionTitle(stringResource(R.string.week), stringResource(R.string.week_subtitle)) }
        (0..6).forEach { offset ->
            val date = today.plusDays(offset.toLong())
            val a = fmi?.days?.firstOrNull { it.date == date }
            val b = meteo?.days?.firstOrNull { it.date == date }
            item(key = "day-$date") {
                val expanded = expandedDay == date.toString()
                val stateLabel = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
                Surface(shape = RoundedCornerShape(Radius.block), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().testTag("day-$date").semantics {
                        stateDescription = stateLabel
                    }.clickable(onClickLabel = stringResource(if (expanded) R.string.close_hourly_forecast else R.string.open_hourly_forecast)) {
                        expandedDay = if (expanded) null else date.toString()
                    }) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (offset == 0) stringResource(R.string.today) else language.weekdayLong(date),
                                style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(language.shortDate(date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ExpandArrow(expanded)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            WeatherSource.entries.forEach { source ->
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SourceDot(source)
                                    Text(source.title(), style = MaterialTheme.typography.labelMedium, color = forecastColors(source).accent)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            DaySummary(a, Modifier.weight(1f)); DaySummary(b, Modifier.weight(1f))
                        }
                    }
                }
            }
            if (expandedDay == date.toString()) {
                val dayTimes = (fmiHours.keys + meteoHours.keys).filter { it.atZone(zone).toLocalDate() == date }.sorted()
                if (dayTimes.isEmpty()) item { Text(stringResource(R.string.no_hourly_forecast)) }
                if (dayTimes.isNotEmpty()) item { ProviderHeading(timeWidth) }
                items(dayTimes, key = { "detail-$it" }) { t -> HourComparison(t, fmiHours[t], meteoHours[t], zone, timeWidth) }
            }
        }
        item {
            Text(stringResource(R.string.weather_footer, zone.id),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun CurrentBlock(source: WeatherSource, state: SourceState, now: Instant, zone: ZoneId, largeFont: Boolean, modifier: Modifier) {
    val language = LocalAppLanguage.current
    val forecast = state.forecast
    val current = forecast?.current(now)
    val stale = forecast != null && Duration.between(forecast.fetchedAt, now).toMinutes() >= 60
    val colors = forecastColors(source)
    Block(modifier, color = colors.top, contentColor = colors.ink, radius = Radius.hero,
        padding = PaddingValues(start = 24.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(source.title(), style = MaterialTheme.typography.labelLarge, color = colors.accent)
                Text(temperature(current?.temperature, language), style = MaterialTheme.typography.displayLarge.copy(letterSpacing = (-0.02).em, lineHeight = 1.em),
                    autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = if (largeFont) 64.sp else 92.sp),
                    maxLines = 1)
                Text(current?.description?.label() ?: stringResource(if (state.loading) R.string.loading_weather else R.string.no_data), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.feels_like, temperature(current?.feelsLike, language)), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            WeatherSymbol(current?.condition ?: Condition.UNKNOWN, current?.night ?: false,
                Modifier.size(if (largeFont) 64.dp else 92.dp), (current?.description ?: WeatherText.UNKNOWN).label())
        }
        val uv = forecast?.days?.firstOrNull { it.date == now.atZone(zone).toLocalDate() }?.uvMax
        val updated = updatedLabel(forecast?.fetchedAt, language)
        Text(listOfNotNull(stringResource(R.string.forecast_time, current?.let { clockLabel(it.time, language, zone) } ?: "–"),
            if (source == WeatherSource.OPEN_METEO) stringResource(R.string.uv_max, decimal(uv, language)) else null,
            stringResource(if (stale) R.string.stale_forecast else R.string.updated_at, updated)).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = colors.muted, modifier = Modifier.padding(top = 10.dp))
        if (state.error != null) Text(state.error.text(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MetricTile(label: String, unit: String, fmi: String, meteo: String, modifier: Modifier) {
    Block(modifier, radius = Radius.tile, padding = PaddingValues(14.dp)) {
        Text(if (unit == "%") label else "$label · $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val noData = stringResource(R.string.no_data)
        listOf(WeatherSource.FMI to fmi, WeatherSource.OPEN_METEO to meteo).forEach { (source, value) ->
            val description = "${source.title()}: $label " + when {
                value == "–" -> noData
                unit == "%" -> stringResource(R.string.percent, value)
                else -> "$value $unit"
            }
            Column(Modifier.padding(top = 8.dp).semantics(mergeDescendants = true) { contentDescription = description }) {
                // Visible source names also distinguish the values without colour vision.
                Text(if (source == WeatherSource.FMI) "FMI" else "Open-Meteo", style = MaterialTheme.typography.labelSmall,
                    color = forecastColors(source).accent)
                Text(if (unit == "%" && value != "–") stringResource(R.string.percent, value) else value, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

@Composable
private fun ProviderHeading(timeWidth: Dp) {
    HourColumns(timeWidth) {
        Text("FMI", Modifier.weight(1f).testTag("hour-heading-FMI"), style = MaterialTheme.typography.labelSmall, color = forecastColors(WeatherSource.FMI).accent)
        Text("Open-Meteo", Modifier.weight(1f).testTag("hour-heading-OPEN_METEO"), style = MaterialTheme.typography.labelSmall, color = forecastColors(WeatherSource.OPEN_METEO).accent)
    }
}

@Composable
private fun HourComparison(time: Instant, a: WeatherHour?, b: WeatherHour?, zone: ZoneId, timeWidth: Dp) {
    val language = LocalAppLanguage.current
    Column {
        HourColumns(timeWidth, Modifier.padding(vertical = 6.dp), time = {
            Text(clockLabel(time, language, zone), style = MaterialTheme.typography.labelMedium, softWrap = false)
        }) {
            HourCell(a, Modifier.weight(1f).testTag("hour-FMI-$time")); HourCell(b, Modifier.weight(1f).testTag("hour-OPEN_METEO-$time"))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

/** Both the header and every row use the same measured time column and gaps. */
@Composable
private fun HourColumns(timeWidth: Dp, modifier: Modifier = Modifier, time: (@Composable () -> Unit)? = null,
                        cells: @Composable RowScope.() -> Unit) {
    val gap = 10.dp
    val measurer = rememberTextMeasurer()
    val columnWidth = with(LocalDensity.current) {
        measurer.measure("Open-Meteo", MaterialTheme.typography.labelSmall, softWrap = false).size.width.toDp()
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val timeAbove = maxWidth < timeWidth + gap * 2 + columnWidth * 2
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (timeAbove) time?.invoke()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (!timeAbove) Box(Modifier.width(timeWidth)) { time?.invoke() }
                cells()
            }
        }
    }
}

@Composable
private fun HourCell(hour: WeatherHour?, modifier: Modifier) {
    val language = LocalAppLanguage.current
    Column(modifier.semantics(mergeDescendants = true) {}) {
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
            WeatherSymbol(hour?.condition ?: Condition.UNKNOWN, hour?.night ?: false, Modifier.size(44.dp), (hour?.description ?: WeatherText.UNKNOWN).label())
            Text(temperature(hour?.temperature, language), style = MaterialTheme.typography.titleMedium)
        }
        Text("${decimal(hour?.wind, language)} m/s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text((hour?.rainProbability?.let { stringResource(R.string.percent, decimal(it, language, 0)) + " · " } ?: "") + "${decimal(hour?.rain, language)} mm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DaySummary(day: WeatherDay?, modifier: Modifier) {
    val language = LocalAppLanguage.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherSymbol(day?.condition ?: Condition.UNKNOWN, false, modifier = Modifier.size(52.dp), description = (day?.description ?: WeatherText.UNKNOWN).label())
            Column {
                Text(temperature(day?.high, language), style = MaterialTheme.typography.titleMedium)
                Text(temperature(day?.low, language), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("${decimal(day?.rain, language)} mm · ${decimal(day?.wind, language)} m/s", style = MaterialTheme.typography.bodySmall)
        day?.probability?.let { Text(stringResource(R.string.rain_chance_day, decimal(it, language, 0)), style = MaterialTheme.typography.bodySmall) }
        if (day != null && !day.complete) Text(stringResource(R.string.partial_day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun RainBlock(rain: List<Double?>, times: List<Instant>, zone: ZoneId, source: WeatherSource, sourcePicker: @Composable () -> Unit) {
    val language = LocalAppLanguage.current
    val color = forecastColors(source).accent
    val line = MaterialTheme.colorScheme.outlineVariant
    val maximum = (rain.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val total = if (rain.all { it != null }) rain.sumOf { it!! } else null
    Block {
        Text(stringResource(R.string.rainfall), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.rain_summary, if (total != null) stringResource(R.string.rain_total, decimal(total, language)) else stringResource(R.string.rain_partial), decimal(maximum, language)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        sourcePicker()
        val plotDescription = rain.mapIndexed { i, r -> stringResource(R.string.rain_plot_point, clockLabel(times[i], language, zone), decimal(r, language)) }.joinToString(", ")
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 16.dp).testTag("rain-plot").semantics { contentDescription = plotDescription }) {
            val base = 4.dp.toPx()
            val baseline = size.height - base
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = baseline * (1f - fraction)
                drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val width = size.width / rain.size
            rain.forEachIndexed { i, value ->
                if (value == null) drawCircle(line, 2.dp.toPx(), Offset(width * (i + 0.5f), size.height - base - 6.dp.toPx()), style = Stroke(1.dp.toPx()))
                else if (value > 0) {
                    val bar = width * 0.7f
                    val h = (baseline * (value / maximum).toFloat()).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(color, Offset(width * i + width * 0.15f, baseline - h), Size(bar, h), CornerRadius(minOf(bar / 2, h / 2)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18, 23).forEach { Text(clockLabel(times[it], language, zone), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
