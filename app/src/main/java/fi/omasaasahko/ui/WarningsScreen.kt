package fi.omasaasahko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import fi.omasaasahko.AppState
import fi.omasaasahko.R
import fi.omasaasahko.WarningsState
import fi.omasaasahko.domain.*
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WarningsScreen(app: AppState, state: WarningsState, permitted: Boolean, scroll: LazyListState,
    onEnable: (Boolean) -> Unit, onInterval: (Int) -> Unit, onTest: () -> Unit, onSettings: () -> Unit, onPermission: () -> Unit,
    local: List<WeatherWarning> = rememberLocalWarnings(app, state, permitted)) {
    val place = app.place
    val notificationPlace = app.devicePlace
    val snapshot = state.snapshot
    val uri = LocalUriHandler.current
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableStateOf("all") }
    val today = app.now.atZone(HELSINKI).toLocalDate()
    val warningDates = local.flatMap { warning ->
        val start = maxOf(today, warning.onset.atZone(HELSINKI).toLocalDate())
        val end = warning.expires.minusNanos(1).atZone(HELSINKI).toLocalDate()
        generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.take(31).toList()
    }.distinct().sorted()
    val selectableDays = ((0..4).map { today.plusDays(it.toLong()) } + warningDates).distinct().sorted()
    LaunchedEffect(today, selectedDay) { if (selectedDay != "all" && LocalDate.parse(selectedDay) !in selectableDays) selectedDay = "all" }
    fun dayLabel(date: LocalDate) = when (date) { today -> "Tänään"; today.plusDays(1) -> "Huomenna"; else -> date.format(DateTimeFormatter.ofPattern("EEE", FINNISH)) }
    fun forDay(date: LocalDate): List<WeatherWarning> {
        val start = date.atStartOfDay(HELSINKI).toInstant(); val end = date.plusDays(1).atStartOfDay(HELSINKI).toInstant()
        return local.filter { it.onset < end && it.expires > start }
    }
    val stale = snapshot != null && Duration.between(snapshot.fetchedAt, app.now).toMinutes() > state.intervalMinutes * 2
    val heading = when {
        (!permitted && app.selectedPlace == null) || place == null -> "Varoitusalue puuttuu"
        snapshot == null && state.loading -> "Haetaan varoituksia…"
        snapshot == null -> "Varoitustietoa ei saatavilla"
        stale || state.error != null || snapshot.partial -> "Varoitustieto epävarma"
        else -> "${local.size} ${if (local.size == 1) "varoitus" else "varoitusta"}"
    }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("warnings-scroll"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(heading, style = MaterialTheme.typography.headlineLarge)
                if (snapshot != null && place != null && (permitted || app.selectedPlace != null) && (stale || state.error != null || snapshot.partial))
                    Text("Tallennetuissa tiedoissa: ${local.size} ${if (local.size == 1) "varoitus" else "varoitusta"}", style = MaterialTheme.typography.bodyMedium)
                Text("Ilmatieteen laitos · Nyt ${updatedLabel(app.now)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                snapshot?.let { Text("Tarkistettu ${updatedLabel(it.fetchedAt)} · FMI julkaisi ${updatedLabel(it.publishedAt)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("warning-days")) {
                item { WarningDayCard("Kaikki", "päivät", selectedDay == "all", "warning-day-all") { selectedDay = "all" } }
                items(selectableDays, key = { it.toString() }) { day ->
                    WarningDayCard(dayLabel(day), day.format(DateTimeFormatter.ofPattern("d.M.")),
                        selectedDay == day.toString(), "warning-day-$day") { selectedDay = day.toString() }
                }
            }
        }
        if ((!permitted && app.selectedPlace == null) || place == null) item {
            Block(radius = Radius.tile, padding = PaddingValues(16.dp)) {
                Text("Salli sijainti sääsivulla, jotta varoitukset kohdistuvat oikealle alueelle.")
                TextButton(onClick = onPermission) { Text("Salli sijainti") }
            }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (stale) item { Text("Varoitustietoja ei ole tarkistettu äskettäin. Päivitä näkymä.", color = MaterialTheme.colorScheme.error) }
        if (snapshot?.partial == true) item { Text("Kaikkia varoituksia ei voitu tulkita. Tarkista myös FMI:n varoitussivu.", color = MaterialTheme.colorScheme.error) }
        if (snapshot == null && !state.loading) item { Text("Varoitustietoja ei ole vielä saatavilla.") }
        if (snapshot != null && (permitted || app.selectedPlace != null) && place != null && local.isEmpty() && selectedDay == "all") item {
            Block(radius = Radius.tile) { Text(if (stale || state.error != null || snapshot.partial) "Tallennetuissa tiedoissa ei ole alueelle kohdistuvia varoituksia."
                else "FMI:n viimeisimmässä syötteessä ei ole tälle sijainnille voimassa olevia tai tulevia varoituksia.") }
        }
        val dates = if (selectedDay == "all") warningDates else listOf(LocalDate.parse(selectedDay))
        dates.forEach { date ->
            item(key = "date-$date") { Text(when (date) { today -> "Tänään"; today.plusDays(1) -> "Huomenna"; else -> date.format(DateTimeFormatter.ofPattern("EEEE d.M.", FINNISH)) },
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
            if (snapshot != null && (permitted || app.selectedPlace != null) && place != null && forDay(date).isEmpty()) item {
                Block(radius = Radius.tile) { Text(if (stale || state.error != null || snapshot.partial) "Tallennetuissa tiedoissa ei ole tälle päivälle alueesi varoituksia. Päivitä tiedot."
                    else "Tälle päivälle ei ole julkaistu alueesi varoituksia.") }
            }
            items(forDay(date), key = { "$date-${it.id}" }) { warning ->
                val colors = warningColors(warning.level)
                Block(color = colors.container, contentColor = colors.ink, radius = Radius.hero,
                    padding = PaddingValues(horizontal = 24.dp, vertical = 22.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_tab_warnings), contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(26.dp))
                        }
                        TonePill("${warning.level.label} · ${if (app.now < warning.onset) "Tulossa" else "Voimassa"}", colors.accent, colors.onAccent)
                    }
                    Text(warning.event, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
                    Text(warning.localAreas(place!!).joinToString { it.name }, style = MaterialTheme.typography.titleSmall, color = colors.muted)
                    TonePill("${updatedLabel(warning.onset)} – ${updatedLabel(warning.expires)}",
                        colors.accent.copy(alpha = 0.16f), colors.ink, Modifier.padding(top = 12.dp), R.drawable.ic_clock)
                    Text(warning.description, modifier = Modifier.padding(top = 12.dp))
                    if (warning.instruction.isNotBlank()) Text(warning.instruction, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item(key = "warning-notifications") {
            Block(radius = Radius.panel, padding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)) {
                SettingSwitchRow("Varoitusilmoitukset", "Keltainen, oranssi ja punainen", state.enabled, onEnable,
                    enabled = permitted && notificationPlace != null)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ilmoitukset seuraavat puhelimen sijaintia. Suosikin tai hakutuloksen katselu ei muuta seurantaa.", style = MaterialTheme.typography.bodySmall)
                    notificationPlace?.let { Text("Seurataan: ${it.name} · ${updatedLabel(it.locatedAt)}", style = MaterialTheme.typography.bodySmall) }
                    if (app.locating || app.namingLocation) Text("Päivitetään ilmoitusten sijaintia…", style = MaterialTheme.typography.bodySmall)
                    app.locationError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { settingsOpen = !settingsOpen }) { Text(if (settingsOpen) "Sulje ilmoitusasetukset ↑" else "Ilmoitusasetukset · ${state.intervalMinutes} min ↓") }
                    if (settingsOpen) {
                        val intervals = listOf(15, 30, 60)
                        Text("Taustatarkistus", style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(intervals.map { "$it min" }, intervals.indexOf(state.intervalMinutes), { onInterval(intervals[it]) }, Modifier.fillMaxWidth())
                        Text("Ei taustapaikannusta. Virransäästö ja verkkoyhteys voivat viivästyttää ilmoituksia.", style = MaterialTheme.typography.bodySmall)
                        if (!state.allowed) {
                            Text("Puhelimen ilmoituslupa tai ilmoituskanava on pois päältä.")
                            TextButton(onClick = onSettings) { Text("Avaa ilmoitusasetukset") }
                        } else if (state.enabled) {
                            TextButton(onClick = onTest, enabled = !state.testPending) { Text("Testaa ilmoitus 10 s kuluttua") }
                            if (state.testPending) Text("Lukitse puhelin nyt ja odota ilmoitusta kelloon.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Text("Alue määräytyy FMI:n varoitusrajoista. Likimääräinen sijainti voi vaikuttaa kohdistukseen lähellä alueen rajaa. Mukana ovat myös kaikki syötteessä julkaistut tulevat varoitukset.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { uri.openUri("https://www.ilmatieteenlaitos.fi/varoitukset") }) { Text("Varoitukset FMI:n sivuilla ↗") }
        }
    }
}

@Composable
private fun WarningDayCard(title: String, subtitle: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(selected = selected, onClick = onClick, modifier = Modifier.testTag(tag), shape = PillShape,
        color = if (selected) scheme.inverseSurface else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.inverseOnSurface else scheme.onSurface) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, softWrap = false)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, softWrap = false)
        }
    }
}
