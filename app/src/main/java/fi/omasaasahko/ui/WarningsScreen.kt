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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import fi.omasaasahko.AppState
import fi.omasaasahko.R
import fi.omasaasahko.labelRes
import fi.omasaasahko.WarningsState
import fi.omasaasahko.domain.*
import java.time.Duration
import java.time.LocalDate

@Composable
fun WarningsScreen(app: AppState, state: WarningsState, permitted: Boolean, scroll: LazyListState,
    onEnable: (Boolean) -> Unit, onInterval: (Int) -> Unit, onTest: () -> Unit, onSettings: () -> Unit, onPermission: () -> Unit,
    local: List<WeatherWarning> = rememberLocalWarnings(app, state, permitted)) {
    val place = app.place
    val notificationPlace = app.devicePlace
    val snapshot = state.snapshot
    val uri = LocalUriHandler.current
    val language = LocalAppLanguage.current
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
    val todayLabel = stringResource(R.string.today); val tomorrowLabel = stringResource(R.string.tomorrow)
    fun dayLabel(date: LocalDate) = when (date) { today -> todayLabel; today.plusDays(1) -> tomorrowLabel; else -> language.weekday(date) }
    fun forDay(date: LocalDate): List<WeatherWarning> {
        val start = date.atStartOfDay(HELSINKI).toInstant(); val end = date.plusDays(1).atStartOfDay(HELSINKI).toInstant()
        return local.filter { it.onset < end && it.expires > start }
    }
    val stale = snapshot != null && Duration.between(snapshot.fetchedAt, app.now).toMinutes() > state.intervalMinutes * 2
    val count = pluralStringResource(R.plurals.warning_count, local.size, local.size)
    val heading = when {
        (!permitted && app.selectedPlace == null) || place == null -> stringResource(R.string.no_warning_area)
        snapshot == null && state.loading -> stringResource(R.string.loading_warnings)
        snapshot == null -> stringResource(R.string.warnings_unavailable)
        stale || state.error != null || snapshot.partial -> stringResource(R.string.warnings_uncertain)
        else -> count
    }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("warnings-scroll"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(heading, style = MaterialTheme.typography.headlineLarge)
                if (snapshot != null && place != null && (permitted || app.selectedPlace != null) && (stale || state.error != null || snapshot.partial))
                    Text(stringResource(R.string.stored_warning_count, count), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.fmi_now, updatedLabel(app.now, language)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                snapshot?.let { Text(stringResource(R.string.checked_published, updatedLabel(it.fetchedAt, language), updatedLabel(it.publishedAt, language)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("warning-days")) {
                item { WarningDayCard(stringResource(R.string.all_days_title), stringResource(R.string.all_days_subtitle), selectedDay == "all", "warning-day-all") { selectedDay = "all" } }
                items(selectableDays, key = { it.toString() }) { day ->
                    WarningDayCard(dayLabel(day), language.shortDate(day),
                        selectedDay == day.toString(), "warning-day-$day") { selectedDay = day.toString() }
                }
            }
        }
        if ((!permitted && app.selectedPlace == null) || place == null) item {
            Block(radius = Radius.tile, padding = PaddingValues(16.dp)) {
                Text(stringResource(R.string.allow_location_for_warnings))
                TextButton(onClick = onPermission) { Text(stringResource(R.string.allow_location)) }
            }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (stale) item { Text(stringResource(R.string.warnings_stale), color = MaterialTheme.colorScheme.error) }
        if (snapshot?.partial == true) item { Text(stringResource(R.string.warnings_partial), color = MaterialTheme.colorScheme.error) }
        if (snapshot == null && !state.loading) item { Text(stringResource(R.string.warnings_not_yet)) }
        if (snapshot != null && (permitted || app.selectedPlace != null) && place != null && local.isEmpty() && selectedDay == "all") item {
            Block(radius = Radius.tile) { Text(stringResource(if (stale || state.error != null || snapshot.partial) R.string.no_local_warnings_stored else R.string.no_local_warnings)) }
        }
        val dates = if (selectedDay == "all") warningDates else listOf(LocalDate.parse(selectedDay))
        dates.forEach { date ->
            item(key = "date-$date") { Text(when (date) { today -> todayLabel; today.plusDays(1) -> tomorrowLabel; else -> language.day(date) },
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
            if (snapshot != null && (permitted || app.selectedPlace != null) && place != null && forDay(date).isEmpty()) item {
                Block(radius = Radius.tile) { Text(stringResource(if (stale || state.error != null || snapshot.partial) R.string.no_day_warnings_stored else R.string.no_day_warnings)) }
            }
            items(forDay(date), key = { "$date-${it.id}" }) { warning ->
                val colors = warningColors(warning.level)
                Block(color = colors.container, contentColor = colors.ink, radius = Radius.hero,
                    padding = PaddingValues(horizontal = 24.dp, vertical = 22.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_tab_warnings), contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(26.dp))
                        }
                        TonePill("${stringResource(warning.level.labelRes())} · ${stringResource(if (app.now < warning.onset) R.string.upcoming else R.string.in_force)}", colors.accent, colors.onAccent)
                    }
                    Text(warning.event, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
                    Text(warning.localAreas(place!!).joinToString { it.name }, style = MaterialTheme.typography.titleSmall, color = colors.muted)
                    TonePill("${updatedLabel(warning.onset, language)} – ${updatedLabel(warning.expires, language)}",
                        colors.pill, colors.ink, Modifier.padding(top = 12.dp), R.drawable.ic_clock)
                    Text(warning.description, modifier = Modifier.padding(top = 12.dp))
                    if (warning.instruction.isNotBlank()) Text(warning.instruction, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item(key = "warning-notifications") {
            Block(radius = Radius.panel, padding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)) {
                SettingSwitchRow(stringResource(R.string.warning_notifications), stringResource(R.string.warning_levels_subtitle), state.enabled, onEnable,
                    enabled = permitted && notificationPlace != null)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.notifications_follow_phone), style = MaterialTheme.typography.bodySmall)
                    notificationPlace?.let { Text(stringResource(R.string.tracking, it.name, updatedLabel(it.locatedAt, language)), style = MaterialTheme.typography.bodySmall) }
                    if (app.locating || app.namingLocation) Text(stringResource(R.string.updating_notification_location), style = MaterialTheme.typography.bodySmall)
                    app.locationError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { settingsOpen = !settingsOpen }) { Text(if (settingsOpen) stringResource(R.string.close_notification_settings) else stringResource(R.string.notification_settings_interval, state.intervalMinutes)) }
                    if (settingsOpen) {
                        val intervals = listOf(15, 30, 60)
                        Text(stringResource(R.string.background_check), style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(intervals.map { "$it min" }, intervals.indexOf(state.intervalMinutes), { onInterval(intervals[it]) }, Modifier.fillMaxWidth())
                        Text(stringResource(R.string.no_background_location), style = MaterialTheme.typography.bodySmall)
                        if (!state.allowed) {
                            Text(stringResource(R.string.notifications_blocked))
                            TextButton(onClick = onSettings) { Text(stringResource(R.string.open_notification_settings)) }
                        } else if (state.enabled) {
                            TextButton(onClick = onTest, enabled = !state.testPending) { Text(stringResource(R.string.test_notification)) }
                            if (state.testPending) Text(stringResource(R.string.lock_phone_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.warnings_footer),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val warningsUrl = stringResource(R.string.fmi_warnings_url)
            TextButton(onClick = { uri.openUri(warningsUrl) }) { Text(stringResource(R.string.warnings_on_fmi)) }
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
