package fi.omasaasahko.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.omasaasahko.*
import fi.omasaasahko.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun WeatherScreen(state: AppState, permitted: Boolean, onPermission: () -> Unit, onSettings: () -> Unit,
                  onLocationSettings: () -> Unit, scroll: LazyListState,
                  onOpenPlacePicker: () -> Unit = {}, onCurrentLocation: () -> Unit = {}) {
    var expandedHours by rememberSaveable { mutableStateOf(false) }
    var expandedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var rainSource by rememberSaveable { mutableIntStateOf(0) }
    val fmi = state.weather[WeatherSource.FMI]?.forecast
    val meteo = state.weather[WeatherSource.OPEN_METEO]?.forecast
    val zone = meteo?.zone ?: HELSINKI
    val today = state.now.atZone(zone).toLocalDate()
    val nextHour = state.now.truncatedTo(ChronoUnit.HOURS).plusSeconds(3600)
    val times = (0 until 24).map { nextHour.plusSeconds(it * 3600L) }
    val fmiHours = remember(fmi) { fmi?.hours?.associateBy { it.time }.orEmpty() }
    val meteoHours = remember(meteo) { meteo?.hours?.associateBy { it.time }.orEmpty() }
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
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WeatherSource.entries.forEach { source ->
                    CurrentCard(source, state.weather.getValue(source), state.now, zone,
                        Modifier.weight(1f).fillMaxHeight().testTag("current-${source.name}"))
                }
            }
        }
        val fmiSun = fmi?.days?.firstOrNull { it.date == today && (it.sunrise != null || it.sunset != null) }
        val sun = fmiSun ?: meteo?.days?.firstOrNull { it.date == today }
        if (sun != null) item {
            val colors = sunColors()
            Surface(color = Color.Transparent, contentColor = colors.ink, shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.2f))) {
                Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(colors.top, colors.bottom))).padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Auringonnousu", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                            Text("↑ ${sun.sunrise?.let { clockLabel(it, zone) } ?: "–"}", style = MaterialTheme.typography.titleLarge, color = colors.accent)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Auringonlasku", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                            Text("↓ ${sun.sunset?.let { clockLabel(it, zone) } ?: "–"}", style = MaterialTheme.typography.titleLarge, color = colors.accent)
                        }
                    }
                    Text(if (fmiSun != null) "Ilmatieteen laitos" else "Open-Meteo", style = MaterialTheme.typography.labelSmall,
                        color = colors.muted, modifier = Modifier.padding(top = 10.dp).testTag("sun-source"))
                }
            }
        }
        item { SectionTitle("Seuraavat 24 tuntia", "Lämpötila · tuuli · sade") }
        item { ProviderHeading() }
        items(if (expandedHours) times else times.take(8), key = { "hour-$it" }) { time ->
            HourComparison(time, fmiHours[time], meteoHours[time], zone)
        }
        item {
            TextButton(onClick = { expandedHours = !expandedHours }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expandedHours) "Näytä vähemmän" else "Näytä kaikki 24 tuntia")
            }
        }
        item {
            SectionTitle("Sademäärä", "Seuraavat 24 tuntia · mm")
            ChoiceRow(listOf("FMI", "Open-Meteo"), rainSource, { rainSource = it }, Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        item { RainChart(times.map { if (rainSource == 0) fmiHours[it]?.rain else meteoHours[it]?.rain }, times, zone) }
        item { SectionTitle("Viikko", "Avaa päivä nähdäksesi tuntiennusteet") }
        (0..6).forEach { offset ->
            val date = today.plusDays(offset.toLong())
            val a = fmi?.days?.firstOrNull { it.date == date }
            val b = meteo?.days?.firstOrNull { it.date == date }
            item(key = "day-$date") {
                val expanded = expandedDay == date.toString()
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().testTag("day-$date").semantics {
                        stateDescription = if (expanded) "Avattu" else "Suljettu"
                    }.clickable(onClickLabel = if (expanded) "Sulje tuntiennuste" else "Avaa tuntiennuste") {
                        expandedDay = if (expanded) null else date.toString()
                    }) {
                    Column(Modifier.padding(16.dp)) {
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
                                Text(source.title, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold, color = forecastColors(source).accent)
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
                items(dayTimes, key = { "detail-$it" }) { t -> HourComparison(t, fmiHours[t], meteoHours[t], zone) }
            }
        }
        item {
            Text("Sää: Ilmatieteen laitos (CC BY 4.0) ja Open-Meteo (CC BY 4.0). Ennusteet näytetään erillisinä. Sademäärä tarkoittaa kellonaikaa edeltävän tunnin kertymää. FMI:n päiväsymboli kuvaa keskipäivän lähintä ennustetta, Open-Meteon päiväsymboli päivän voimakkainta sääilmiötä. Kellonajat: ${zone.id}.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun CurrentCard(source: WeatherSource, state: SourceState, now: Instant, zone: ZoneId, modifier: Modifier) {
    val forecast = state.forecast
    val current = forecast?.current(now)
    val observed = forecast?.recentObservation(now)
    val stale = forecast != null && Duration.between(forecast.fetchedAt, now).toMinutes() >= 60
    val colors = forecastColors(source)
    Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = Color.Transparent, contentColor = colors.ink,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.2f))) {
        Column(Modifier.fillMaxHeight().background(Brush.verticalGradient(listOf(colors.top, colors.bottom))).padding(16.dp)) {
            Text(source.title, style = MaterialTheme.typography.labelLarge, color = colors.accent, modifier = Modifier.heightIn(min = 38.dp))
            WeatherSymbol(current?.condition ?: Condition.UNKNOWN, current?.night ?: false, Modifier.size(86.dp), current?.description ?: Condition.UNKNOWN.label)
            Text(temperature(current?.temperature), fontSize = 46.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
            Text(current?.description ?: if (state.loading) "Haetaan säätä…" else "Tieto puuttuu", style = MaterialTheme.typography.bodyMedium)
            Text("Ennuste ${current?.let { clockLabel(it.time, zone) } ?: "–"}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            Metric("Tuntuu kuin", temperature(current?.feelsLike))
            Metric("Tuuli", "${decimal(current?.wind)} m/s")
            Metric("Sade", "${decimal(current?.rain)} mm")
            Metric("Sateen riski", current?.rainProbability?.let { "${decimal(it, 0)} %" } ?: "–")
            val uv = forecast?.days?.firstOrNull { it.date == now.atZone(zone).toLocalDate() }?.uvMax
            if (source == WeatherSource.OPEN_METEO) Metric("UV, päivän maks.", decimal(uv))
            if (observed != null) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = colors.accent.copy(alpha = 0.25f))
                Text("Lähimmän FMI-aseman havainto", style = MaterialTheme.typography.labelSmall)
                forecast.observationStation?.let { station ->
                    Text(station.name, style = MaterialTheme.typography.bodyMedium)
                    Text("${decimal(station.distanceMeters / 1000)} km valitusta paikasta", style = MaterialTheme.typography.bodySmall)
                }
                Text("${temperature(observed.temperature)} · ${clockLabel(observed.time, zone)}", style = MaterialTheme.typography.titleLarge)
                Text("Tuuli ${decimal(observed.wind)} m/s", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(1f))
            Text((if (stale) "Vanha ennuste · " else "Päivitetty ") + updatedLabel(forecast?.fetchedAt),
                style = MaterialTheme.typography.labelSmall, color = colors.muted, modifier = Modifier.padding(top = 14.dp))
            if (state.error != null) Text(state.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(Modifier.padding(bottom = 9.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProviderHeading() {
    Row(Modifier.fillMaxWidth().padding(start = 54.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FMI", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text("OPEN-METEO", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun HourComparison(time: Instant, a: WeatherHour?, b: WeatherHour?, zone: ZoneId) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(clockLabel(time, zone), Modifier.width(44.dp), style = MaterialTheme.typography.labelMedium)
            HourCell(a, Modifier.weight(1f)); HourCell(b, Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun HourCell(hour: WeatherHour?, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherSymbol(hour?.condition ?: Condition.UNKNOWN, hour?.night ?: false, Modifier.size(34.dp), hour?.description ?: Condition.UNKNOWN.label)
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
            WeatherSymbol(day?.condition ?: Condition.UNKNOWN, false, modifier = Modifier.size(42.dp), description = day?.description ?: Condition.UNKNOWN.label)
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
private fun RainChart(rain: List<Double?>, times: List<Instant>, zone: ZoneId) {
    val colors = forecastColors(WeatherSource.FMI)
    val color = colors.accent
    val line = MaterialTheme.colorScheme.outlineVariant
    val maximum = (rain.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val total = if (rain.all { it != null }) rain.sumOf { it!! } else null
    Surface(shape = RoundedCornerShape(24.dp), color = colors.bottom, contentColor = colors.ink) {
        Column(Modifier.padding(18.dp)) {
            Text(if (total != null) "Yhteensä ${decimal(total)} mm" else "Osa sadetiedoista puuttuu", style = MaterialTheme.typography.titleMedium)
            Text("Asteikko 0–${decimal(maximum)} mm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 16.dp).semantics {
                contentDescription = rain.mapIndexed { i, r -> "${clockLabel(times[i], zone)}: ${decimal(r)} millimetriä" }.joinToString(", ")
            }) {
                repeat(3) { i -> val y = size.height * i / 2; drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
                val width = size.width / rain.size
                rain.forEachIndexed { i, value ->
                    if (value == null) drawCircle(line, 2.dp.toPx(), Offset(width * (i + 0.5f), size.height - 4.dp.toPx()), style = Stroke(1.dp.toPx()))
                    else if (value > 0) {
                        val h = size.height * (value / maximum).toFloat()
                        drawRoundRect(color, Offset(width * i + width * 0.15f, size.height - h), Size(width * 0.7f, h), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 6, 12, 18, 23).forEach { Text(clockLabel(times[it], zone), style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
