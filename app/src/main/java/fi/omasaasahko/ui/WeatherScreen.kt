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
import fi.omasaasahko.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun WeatherScreen(state: AppState, permitted: Boolean, onPermission: () -> Unit, onSettings: () -> Unit,
                  onLocationSettings: () -> Unit, scroll: LazyListState,
                  onCurrentLocation: () -> Unit = {}) {
    var expandedHours by rememberSaveable { mutableStateOf(false) }
    var expandedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var rainSource by rememberSaveable { mutableIntStateOf(0) }
    val largeFont = LocalDensity.current.fontScale > 1.25f
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
    val timeWidthPx = remember(times, fmiHours, meteoHours, zone, timeStyle, timeMeasurer, LocalDensity.current) {
        (times + fmiHours.keys + meteoHours.keys).maxOf {
            timeMeasurer.measure(clockLabel(it, zone), timeStyle, softWrap = false).size.width
        }
    }
    val timeWidth = with(LocalDensity.current) { timeWidthPx.toDp().coerceAtLeast(44.dp) }
    LazyColumn(state = scroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text(if (state.selectedPlace != null) "VALITTU PAIKKA" else if (state.locating) "PAIKANNETAAN…" else "SIJAINTISI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                state.place?.nearbyName?.let { name ->
                    Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Lähin paikannimi · Maanmittauslaitos", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.selectedPlace == null) {
                    if (state.namingLocation) Text("Haetaan paikannimeä…", style = MaterialTheme.typography.bodySmall)
                    state.place?.nearbyDistanceMeters?.takeIf { it > fi.omasaasahko.data.PlaceNames.RADIUS_METERS }?.let {
                        Text("Nimipiste ${decimal(it / 1000)} km päässä", style = MaterialTheme.typography.bodySmall)
                    }
                    state.place?.accuracyMeters?.takeIf { it > 200 }?.let {
                        Text("Likimääräinen sijainti · tarkkuus noin ${decimal(it.toDouble(), 0)} m", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (state.selectedPlace != null) TextButton(onClick = {
                    onCurrentLocation(); if (!permitted) onPermission()
                }) { Text("Nykyinen sijainti") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)) {
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE d. MMMM", FINNISH)).replaceFirstChar { it.titlecase(FINNISH) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Nyt ${clockLabel(state.now, zone)}", color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("current-time"))
                }
            }
        }
        if (!permitted && state.selectedPlace == null) item {
            Notice("Salli sijainti, niin saat oman alueesi ennusteet. Sijaintia käytetään vain sovelluksen ollessa auki.", "Salli sijainti", onPermission)
            TextButton(onClick = onSettings) { Text("Avaa sovelluksen asetukset") }
        }
        state.locationError?.takeIf { state.selectedPlace == null }?.let { error -> item {
            Notice(error, "Sijaintiasetukset", onLocationSettings)
        } }
        if (state.selectedPlace == null && state.place != null && (!permitted || state.locationError != null)) item {
            Text("Viimeisin sijainti · ${updatedLabel(state.place.locatedAt)}", style = MaterialTheme.typography.bodySmall)
        }
        items(WeatherSource.entries, key = { "current-$it" }) { source ->
            CurrentBlock(source, state.weather.getValue(source), state.now, zone, largeFont,
                Modifier.fillMaxWidth().testTag("current-${source.name}"))
        }
        item {
            val a = fmi?.current(state.now); val b = meteo?.current(state.now)
            val tiles: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> MetricTile("Tuuli", "m/s", decimal(a?.wind), decimal(b?.wind), m) },
                { m -> MetricTile("Sade", "mm", decimal(a?.rain), decimal(b?.rain), m) },
                { m -> MetricTile("Sateen riski", "%", decimal(a?.rainProbability, 0), decimal(b?.rainProbability, 0), m) },
            )
            if (largeFont) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
            else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
        fmi?.recentObservation(state.now)?.let { observed -> item {
            Block(radius = Radius.tile, padding = PaddingValues(16.dp)) {
                Text("Lähimmän FMI-aseman havainto", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                fmi.observationStation?.let { station ->
                    Text(station.name, style = MaterialTheme.typography.titleMedium)
                    Text("${decimal(station.distanceMeters / 1000)} km valitusta paikasta", style = MaterialTheme.typography.bodySmall)
                }
                Text("${temperature(observed.temperature)} · ${clockLabel(observed.time, zone)}", style = MaterialTheme.typography.titleLarge)
                Text("Tuuli ${decimal(observed.wind)} m/s", style = MaterialTheme.typography.bodySmall)
            }
        } }
        val fmiSun = fmi?.days?.firstOrNull { it.date == today && (it.sunrise != null || it.sunset != null) }
        val sun = fmiSun ?: meteo?.days?.firstOrNull { it.date == today }
        if (sun != null) item {
            val colors = sunColors()
            val rise = sun.sunrise?.let { clockLabel(it, zone) } ?: "–"
            val set = sun.sunset?.let { clockLabel(it, zone) } ?: "–"
            Block(color = colors.top, contentColor = colors.ink, radius = Radius.panel, padding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val sourceName = if (fmiSun != null) "Ilmatieteen laitos" else "Open-Meteo"
                    val measurer = rememberTextMeasurer()
                    val timesWidth = listOf("↑ $rise", "↓ $set").sumOf {
                        measurer.measure(it, MaterialTheme.typography.headlineSmall, softWrap = false).size.width
                    }
                    val sourceWidth = maxOf(
                        measurer.measure("Aurinko", MaterialTheme.typography.labelMedium, softWrap = false).size.width,
                        measurer.measure(sourceName, MaterialTheme.typography.labelSmall, softWrap = false).size.width)
                    val gap = 8.dp
                    val sourceAbove = largeFont || with(LocalDensity.current) { (maxWidth - gap * 2).roundToPx() < timesWidth + sourceWidth }
                    val source: @Composable () -> Unit = {
                        Column(horizontalAlignment = if (sourceAbove) Alignment.Start else Alignment.CenterHorizontally) {
                            Text("Aurinko", style = MaterialTheme.typography.labelMedium, color = colors.muted)
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
                                modifier = Modifier.semantics { contentDescription = "Auringonnousu $rise" })
                            if (!sourceAbove) source()
                            Text("↓ $set", style = MaterialTheme.typography.headlineSmall, color = colors.accent, softWrap = false,
                                modifier = Modifier.semantics { contentDescription = "Auringonlasku $set" })
                        }
                    }
                }
            }
        }
        item { SectionTitle("Seuraavat 24 tuntia", "Lämpötila · tuuli · sade") }
        item { ProviderHeading(timeWidth) }
        items(if (expandedHours) times else times.take(8), key = { "hour-$it" }) { time ->
            HourComparison(time, fmiHours[time], meteoHours[time], zone, timeWidth)
        }
        item {
            TextButton(onClick = { expandedHours = !expandedHours }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expandedHours) "Näytä vähemmän" else "Näytä kaikki 24 tuntia")
            }
        }
        item {
            val source = if (rainSource == 0) WeatherSource.FMI else WeatherSource.OPEN_METEO
            RainBlock(times.map { if (rainSource == 0) fmiHours[it]?.rain else meteoHours[it]?.rain }, times, zone, source) {
                ChoiceRow(listOf("FMI", "Open-Meteo"), rainSource, { rainSource = it }, Modifier.fillMaxWidth())
            }
        }
        item { SectionTitle("Viikko", "Avaa päivä nähdäksesi tuntiennusteet") }
        (0..6).forEach { offset ->
            val date = today.plusDays(offset.toLong())
            val a = fmi?.days?.firstOrNull { it.date == date }
            val b = meteo?.days?.firstOrNull { it.date == date }
            item(key = "day-$date") {
                val expanded = expandedDay == date.toString()
                Surface(shape = RoundedCornerShape(Radius.block), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().testTag("day-$date").semantics {
                        stateDescription = if (expanded) "Avattu" else "Suljettu"
                    }.clickable(onClickLabel = if (expanded) "Sulje tuntiennuste" else "Avaa tuntiennuste") {
                        expandedDay = if (expanded) null else date.toString()
                    }) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (offset == 0) "Tänään" else date.format(DateTimeFormatter.ofPattern("EEEE", FINNISH)).replaceFirstChar { it.titlecase(FINNISH) },
                                style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(date.format(DateTimeFormatter.ofPattern("d.M.")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ExpandArrow(expanded)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            WeatherSource.entries.forEach { source ->
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SourceDot(source)
                                    Text(source.title, style = MaterialTheme.typography.labelMedium, color = forecastColors(source).accent)
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
                if (dayTimes.isEmpty()) item { Text("Päivän tuntiennustetta ei ole saatavilla.") }
                if (dayTimes.isNotEmpty()) item { ProviderHeading(timeWidth) }
                items(dayTimes, key = { "detail-$it" }) { t -> HourComparison(t, fmiHours[t], meteoHours[t], zone, timeWidth) }
            }
        }
        item {
            Text("Sää: Ilmatieteen laitos (CC BY 4.0) ja Open-Meteo (CC BY 4.0). Ennusteet näytetään erillisinä. Sademäärä tarkoittaa kellonaikaa edeltävän tunnin kertymää. FMI:n päiväsymboli kuvaa keskipäivän lähintä ennustetta, Open-Meteon päiväsymboli päivän voimakkainta sääilmiötä. Kellonajat: ${zone.id}.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun CurrentBlock(source: WeatherSource, state: SourceState, now: Instant, zone: ZoneId, largeFont: Boolean, modifier: Modifier) {
    val forecast = state.forecast
    val current = forecast?.current(now)
    val stale = forecast != null && Duration.between(forecast.fetchedAt, now).toMinutes() >= 60
    val colors = forecastColors(source)
    Block(modifier, color = colors.top, contentColor = colors.ink, radius = Radius.hero,
        padding = PaddingValues(start = 24.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(source.title, style = MaterialTheme.typography.labelLarge, color = colors.accent)
                Text(temperature(current?.temperature), style = MaterialTheme.typography.displayLarge.copy(letterSpacing = (-0.02).em, lineHeight = 1.em),
                    autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = if (largeFont) 64.sp else 92.sp),
                    maxLines = 1)
                Text(current?.description ?: if (state.loading) "Haetaan säätä…" else "Tieto puuttuu", style = MaterialTheme.typography.titleMedium)
                Text("Tuntuu kuin ${temperature(current?.feelsLike)}", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            WeatherSymbol(current?.condition ?: Condition.UNKNOWN, current?.night ?: false,
                Modifier.size(if (largeFont) 64.dp else 92.dp), current?.description ?: Condition.UNKNOWN.label)
        }
        val uv = forecast?.days?.firstOrNull { it.date == now.atZone(zone).toLocalDate() }?.uvMax
        Text(listOfNotNull("Ennuste ${current?.let { clockLabel(it.time, zone) } ?: "–"}",
            if (source == WeatherSource.OPEN_METEO) "UV, päivän maks. ${decimal(uv)}" else null,
            (if (stale) "Vanha ennuste · " else "Päivitetty ") + updatedLabel(forecast?.fetchedAt)).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = colors.muted, modifier = Modifier.padding(top = 10.dp))
        if (state.error != null) Text(state.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MetricTile(label: String, unit: String, fmi: String, meteo: String, modifier: Modifier) {
    Block(modifier, radius = Radius.tile, padding = PaddingValues(14.dp)) {
        Text(if (unit == "%") label else "$label · $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(WeatherSource.FMI to fmi, WeatherSource.OPEN_METEO to meteo).forEach { (source, value) ->
            Column(Modifier.padding(top = 8.dp).semantics(mergeDescendants = true) {
                contentDescription = "${source.title}: $label ${if (value == "–") "Tieto puuttuu" else "$value $unit"}"
            }) {
                // Visible source names also distinguish the values without colour vision.
                Text(if (source == WeatherSource.FMI) "FMI" else "Open-Meteo", style = MaterialTheme.typography.labelSmall,
                    color = forecastColors(source).accent)
                Text(if (unit == "%" && value != "–") "$value %" else value, style = MaterialTheme.typography.titleMedium,
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
    Column {
        HourColumns(timeWidth, Modifier.padding(vertical = 6.dp), time = {
            Text(clockLabel(time, zone), style = MaterialTheme.typography.labelMedium, softWrap = false)
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
    Column(modifier.semantics(mergeDescendants = true) {}) {
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
            WeatherSymbol(hour?.condition ?: Condition.UNKNOWN, hour?.night ?: false, Modifier.size(44.dp), hour?.description ?: Condition.UNKNOWN.label)
            Text(temperature(hour?.temperature), style = MaterialTheme.typography.titleMedium)
        }
        Text("${decimal(hour?.wind)} m/s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text((hour?.rainProbability?.let { "${decimal(it, 0)} % · " } ?: "") + "${decimal(hour?.rain)} mm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DaySummary(day: WeatherDay?, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherSymbol(day?.condition ?: Condition.UNKNOWN, false, modifier = Modifier.size(52.dp), description = day?.description ?: Condition.UNKNOWN.label)
            Column {
                Text(temperature(day?.high), style = MaterialTheme.typography.titleMedium)
                Text(temperature(day?.low), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("${decimal(day?.rain)} mm · ${decimal(day?.wind)} m/s", style = MaterialTheme.typography.bodySmall)
        day?.probability?.let { Text("Sateen riski ${decimal(it, 0)} %", style = MaterialTheme.typography.bodySmall) }
        if (day != null && !day.complete) Text("Osittainen päivä", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun RainBlock(rain: List<Double?>, times: List<Instant>, zone: ZoneId, source: WeatherSource, sourcePicker: @Composable () -> Unit) {
    val color = forecastColors(source).accent
    val line = MaterialTheme.colorScheme.outlineVariant
    val maximum = (rain.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val total = if (rain.all { it != null }) rain.sumOf { it!! } else null
    Block {
        Text("Sademäärä", style = MaterialTheme.typography.titleLarge)
        Text((if (total != null) "Yhteensä ${decimal(total)} mm" else "Osa sadetiedoista puuttuu") + " · asteikko 0–${decimal(maximum)} mm",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        sourcePicker()
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 16.dp).testTag("rain-plot").semantics {
            contentDescription = rain.mapIndexed { i, r -> "${clockLabel(times[i], zone)}: ${decimal(r)} millimetriä" }.joinToString(", ")
        }) {
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
            listOf(0, 6, 12, 18, 23).forEach { Text(clockLabel(times[it], zone), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
