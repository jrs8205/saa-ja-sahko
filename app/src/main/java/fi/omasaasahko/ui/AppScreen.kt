package fi.omasaasahko.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import fi.omasaasahko.domain.HELSINKI
import fi.omasaasahko.domain.Prices
import fi.omasaasahko.domain.updatedLabel
import fi.omasaasahko.AppState
import fi.omasaasahko.WarningsState
import fi.omasaasahko.data.PriceAlertState
import fi.omasaasahko.domain.Resolution
import fi.omasaasahko.domain.PlaceResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    state: AppState, permitted: Boolean, onPermission: () -> Unit, onSettings: () -> Unit,
    onLocationSettings: () -> Unit, onWeatherRefresh: () -> Unit, onPricesRefresh: () -> Unit,
    onResolution: (Resolution) -> Unit,
    onVat: (Boolean) -> Unit = {},
    warnings: WarningsState = WarningsState(), onWarningsRefresh: () -> Unit = {},
    onWarningsEnable: (Boolean) -> Unit = {}, onWarningsInterval: (Int) -> Unit = {},
    onWarningsTest: () -> Unit = {}, onNotificationSettings: () -> Unit = {}, warningsRequest: Int = 0,
    priceAlerts: PriceAlertState = PriceAlertState(), onPriceAlerts: (Boolean) -> Unit = {}, pricesRequest: Int = 0, priceDateRequest: String? = null,
    onPriceAlertsTest: () -> Unit = {},
    onPlaceSearch: (String) -> Unit = {}, onPlaceSelect: (PlaceResult) -> Unit = {},
    onPlaceFavorite: (PlaceResult) -> Unit = {}, onCurrentLocation: () -> Unit = {},
) {
    // Restore the tab on rotation/process recreation; a new launch always starts with weather.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tab) { if (tab != 0) pickerOpen = false }
    LaunchedEffect(warningsRequest) { if (warningsRequest > 0) tab = 2 }
    LaunchedEffect(pricesRequest) { if (pricesRequest > 0) tab = 1 }
    val weatherScroll = rememberLazyListState()
    val priceScroll = rememberLazyListState()
    val warningsScroll = rememberLazyListState()
    val refreshing = when (tab) { 0 -> state.weatherLoading; 1 -> state.pricesLoading; else -> warnings.loading }
    val refresh = when (tab) { 0 -> onWeatherRefresh; 1 -> onPricesRefresh; else -> onWarningsRefresh }
    val current = AppTab.entries[tab]
    val scheme = MaterialTheme.colorScheme
    val currentBand = remember(state.prices, state.now, state.resolution, state.includeVat) {
        priceBand(Prices.slots(state.prices?.quarters.orEmpty(), state.now.atZone(HELSINKI).toLocalDate(), state.resolution, state.includeVat)
            .firstOrNull { it.contains(state.now) }?.centsPerKwh)
    }
    val topLevel = remember(warnings.snapshot, state.place, state.now, permitted, state.selectedPlace) {
        if (permitted || state.selectedPlace != null) warnings.snapshot?.local(state.place, state.now).orEmpty().maxByOrNull { it.level.rank }?.level else null
    }
    val tint = navTint(current, currentBand, topLevel)
    Scaffold(containerColor = scheme.background, topBar = {
        Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (current == AppTab.PRICES) Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Sähkön hinta", style = MaterialTheme.typography.headlineLarge)
                    Text("Suomi · haettu ${updatedLabel(state.prices?.fetchedAt)} · Elering",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                } else LocationPill(
                    name = state.place?.name ?: if (current == AppTab.WEATHER) "Sää lähelläsi" else "Oman alueen varoitukset",
                    onClick = if (current == AppTab.WEATHER && !pickerOpen) ({ pickerOpen = true }) else null,
                    modifier = Modifier.weight(1f))
                RefreshButton(enabled = !pickerOpen && !refreshing && (tab != 0 || permitted || state.selectedPlace != null), onClick = refresh)
            }
        }
    }, bottomBar = {
        if (!pickerOpen) Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center) {
            FloatingNavBar(current, tint, { tab = it.ordinal }, Modifier.widthIn(max = 480.dp))
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            PullToRefreshBox(isRefreshing = refreshing,
                onRefresh = { if (!pickerOpen) refresh() },
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()
                    .then(if (pickerOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
                if (tab == 0) WeatherScreen(state, permitted, onPermission, onSettings, onLocationSettings, weatherScroll,
                    { pickerOpen = true }, onCurrentLocation)
                else if (tab == 1) ElectricityScreen(state, onResolution, priceScroll, onVat, priceAlerts, onPriceAlerts, onNotificationSettings, pricesRequest, priceDateRequest, onPriceAlertsTest)
                else WarningsScreen(state, warnings, permitted, warningsScroll, onWarningsEnable, onWarningsInterval,
                    onWarningsTest, onNotificationSettings, onPermission)
            }
            // A sibling overlay keeps weather's remembered state and scroll position alive,
            // while the search list is outside the pull-to-refresh gesture hierarchy.
            if (pickerOpen && tab == 0) Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                PlacePicker(state, onPlaceSearch, onPlaceSelect, onPlaceFavorite) { pickerOpen = false }
            }
        }
    }
}
