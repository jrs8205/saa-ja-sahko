package fi.omasaasahko.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    LaunchedEffect(warningsRequest) { if (warningsRequest > 0) tab = 2 }
    LaunchedEffect(pricesRequest) { if (pricesRequest > 0) tab = 1 }
    val weatherScroll = rememberLazyListState()
    val priceScroll = rememberLazyListState()
    val warningsScroll = rememberLazyListState()
    val refreshing = when (tab) { 0 -> state.weatherLoading; 1 -> state.pricesLoading; else -> warnings.loading }
    val refresh = when (tab) { 0 -> onWeatherRefresh; 1 -> onPricesRefresh; else -> onWarningsRefresh }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sää & Sähkö", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = refresh,
                    enabled = !refreshing && (tab != 0 || permitted || state.selectedPlace != null)) {
                    Text("Päivitä")
                }
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Sää", "Pörssi-sähkö", "Varoitukset").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = {
                        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal)
                    })
                }
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            PullToRefreshBox(isRefreshing = refreshing,
                onRefresh = refresh,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                if (tab == 0) WeatherScreen(state, permitted, onPermission, onSettings, onLocationSettings, weatherScroll,
                    onPlaceSearch, onPlaceSelect, onPlaceFavorite, onCurrentLocation)
                else if (tab == 1) ElectricityScreen(state, onResolution, priceScroll, onVat, priceAlerts, onPriceAlerts, onNotificationSettings, pricesRequest, priceDateRequest, onPriceAlertsTest)
                else WarningsScreen(state, warnings, permitted, warningsScroll, onWarningsEnable, onWarningsInterval,
                    onWarningsTest, onNotificationSettings, onPermission)
            }
        }
    }
}
