package fi.omasaasahko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fi.omasaasahko.AppState
import fi.omasaasahko.WarningsState
import fi.omasaasahko.domain.*
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WarningsScreen(app: AppState, state: WarningsState, permitted: Boolean, scroll: LazyListState,
    onEnable: (Boolean) -> Unit, onInterval: (Int) -> Unit, onTest: () -> Unit, onSettings: () -> Unit, onPermission: () -> Unit) {
    val place = app.place
    val snapshot = state.snapshot
    val local = if (permitted) snapshot?.local(place, app.now).orEmpty() else emptyList()
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
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("warnings-scroll"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(place?.name ?: "Oman alueen varoitukset", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Nyt ${updatedLabel(app.now)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Ilmatieteen laitos · ${local.size} ${if (local.size == 1) "varoitus" else "varoitusta"}", style = MaterialTheme.typography.titleMedium)
                snapshot?.let { Text("Tarkistettu ${updatedLabel(it.fetchedAt)} · FMI julkaisi ${updatedLabel(it.publishedAt)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            Text("Valitse päivä · vieritä sivulle", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("warning-days")) {
                item { FilterChip(selected = selectedDay == "all", onClick = { selectedDay = "all" },
                    label = { Text("Kaikki") }, modifier = Modifier.testTag("warning-day-all")) }
                items(selectableDays, key = { it.toString() }) { day ->
                    FilterChip(selected = selectedDay == day.toString(), onClick = { selectedDay = day.toString() },
                        label = { Column { Text(dayLabel(day)); Text(day.format(DateTimeFormatter.ofPattern("d.M.")), style = MaterialTheme.typography.labelSmall) } },
                        modifier = Modifier.testTag("warning-day-$day"))
                }
            }
        }
        if (!permitted || place == null) item {
            Card { Column(Modifier.padding(16.dp)) {
                Text("Salli sijainti sääsivulla, jotta varoitukset kohdistuvat oikealle alueelle.")
                TextButton(onClick = onPermission) { Text("Salli sijainti") }
            } }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Varoitusilmoitukset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Keltainen, oranssi ja punainen", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = state.enabled, onCheckedChange = onEnable, enabled = permitted && place != null)
                    }
                    Text("Seurataan viimeksi haettua sijaintia. Paikka päivittyy avatessasi sovelluksen.", style = MaterialTheme.typography.bodySmall)
                    place?.let { Text("Sijainti haettu ${updatedLabel(it.locatedAt)}", style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { settingsOpen = !settingsOpen }) { Text(if (settingsOpen) "Sulje ilmoitusasetukset ↑" else "Ilmoitusasetukset · ${state.intervalMinutes} min ↓") }
                    if (settingsOpen) {
                    Text("Taustatarkistus", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(15, 30, 60).forEachIndexed { index, minutes ->
                            SegmentedButton(selected = state.intervalMinutes == minutes, onClick = { onInterval(minutes) },
                                shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text("$minutes min") }
                        }
                    }
                    Text("Ei taustapaikannusta. Virransäästö ja verkkoyhteys voivat viivästyttää ilmoituksia.", style = MaterialTheme.typography.bodySmall)
                    if (!state.allowed) {
                        Text("Puhelimen ilmoituslupa tai ilmoituskanava on pois päältä.")
                        TextButton(onClick = onSettings) { Text("Avaa ilmoitusasetukset") }
                    } else if (state.enabled) TextButton(onClick = onTest) { Text("Testaa ilmoitus") }
                    }
                }
            }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (stale) item { Text("Varoitustietoja ei ole tarkistettu äskettäin. Päivitä näkymä.", color = MaterialTheme.colorScheme.error) }
        if (snapshot?.partial == true) item { Text("Kaikkia varoituksia ei voitu tulkita. Tarkista myös FMI:n varoitussivu.", color = MaterialTheme.colorScheme.error) }
        if (snapshot == null && !state.loading) item { Text("Varoitustietoja ei ole vielä saatavilla.") }
        if (snapshot != null && permitted && place != null && local.isEmpty() && selectedDay == "all") item {
            Card { Text(if (stale || state.error != null || snapshot.partial) "Tallennetuissa tiedoissa ei ole alueelle kohdistuvia varoituksia."
                else "FMI:n viimeisimmässä syötteessä ei ole tälle sijainnille voimassa olevia tai tulevia varoituksia.", Modifier.padding(20.dp)) }
        }
        val dates = if (selectedDay == "all") warningDates else listOf(LocalDate.parse(selectedDay))
        dates.forEach { date ->
            item(key = "date-$date") { Text(when (date) { today -> "Tänään"; today.plusDays(1) -> "Huomenna"; else -> date.format(DateTimeFormatter.ofPattern("EEEE d.M.", FINNISH)) },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (snapshot != null && permitted && place != null && forDay(date).isEmpty()) item {
                Card { Text(if (stale || state.error != null || snapshot.partial) "Tallennetuissa tiedoissa ei ole tälle päivälle alueesi varoituksia. Päivitä tiedot."
                    else "Tälle päivälle ei ole julkaistu alueesi varoituksia.", Modifier.padding(20.dp)) }
            }
            items(forDay(date), key = { "$date-${it.id}" }) { warning ->
                val dark = MaterialTheme.colorScheme.background.luminance() < .5f
                val accent = when (warning.level) {
                    WarningLevel.YELLOW -> if (dark) Color(0xFFFFDC76) else Color(0xFF765600)
                    WarningLevel.ORANGE -> if (dark) Color(0xFFFFB475) else Color(0xFF923900)
                    WarningLevel.RED -> if (dark) Color(0xFFFFA7A2) else Color(0xFFAF1C24)
                }
                Card(colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = if (dark) .15f else .09f))) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠ ${warning.level.label} · ${if (app.now < warning.onset) "Tulossa" else "Voimassa"}", color = accent, fontWeight = FontWeight.Bold)
                        Text(warning.event, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(warning.localAreas(place!!).joinToString { it.name }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${updatedLabel(warning.onset)} – ${updatedLabel(warning.expires)}", style = MaterialTheme.typography.labelLarge)
                        Text(warning.description)
                        if (warning.instruction.isNotBlank()) Text(warning.instruction)
                    }
                }
            }
        }
        item {
            Text("Alue määräytyy FMI:n varoitusrajoista. Likimääräinen sijainti voi vaikuttaa kohdistukseen lähellä alueen rajaa. Mukana ovat myös kaikki syötteessä julkaistut tulevat varoitukset.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { uri.openUri("https://www.ilmatieteenlaitos.fi/varoitukset") }) { Text("Varoitukset FMI:n sivuilla ↗") }
        }
    }
}
