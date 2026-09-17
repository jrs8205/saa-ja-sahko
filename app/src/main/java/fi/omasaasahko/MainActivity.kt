package fi.omasaasahko

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.edit
import androidx.core.net.toUri
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.Resolution
import fi.omasaasahko.ui.*

class MainActivity : ComponentActivity() {
    private var warningsRequest by mutableIntStateOf(0)
    private var pricesRequest by mutableIntStateOf(0)
    private var priceDateRequest by mutableStateOf<String?>(null)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("showWarnings", false)) warningsRequest++
        if (intent.getBooleanExtra("showPrices", false)) { priceDateRequest=intent.getStringExtra("priceDate"); pricesRequest++ }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make both stable channels visible to Android and companion apps before the first alert.
        WarningService(applicationContext).createChannel()
        PriceAlerts(applicationContext).channel()
        if (intent.getBooleanExtra("showWarnings", false)) warningsRequest++
        if (intent.getBooleanExtra("showPrices", false)) { priceDateRequest=intent.getStringExtra("priceDate"); pricesRequest++ }
        enableEdgeToEdge()
        val preferences = getSharedPreferences("preferences", MODE_PRIVATE)
        val appContext = applicationContext
        val locator = DeviceLocation(appContext)
        val warningService = WarningService(appContext)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(
                Repository(appContext), locator,
                runCatching { Resolution.valueOf(preferences.getString("resolution", "QUARTER")!!) }.getOrDefault(Resolution.QUARTER),
                { resolution -> preferences.edit { putString("resolution", resolution.name) } },
                initialVat = preferences.getBoolean("includeVat", true),
                saveVat = { include -> preferences.edit { putBoolean("includeVat", include) } },
                placeSearch = MmlPlaceNames(), favoriteStore = PlaceStorage(appContext),
                onDeviceLocationStart = warningService::beginLocationUpdate,
                onDeviceLocationEnd = warningService::endLocationUpdate,
                onDevicePlace = warningService::savePlace,
            ) as T
        }
        setContent {
            AppTheme {
                val model: AppViewModel = viewModel(factory = factory)
                val warningsModel: WarningsViewModel = viewModel()
                val priceAlertModel: PriceAlertViewModel = viewModel()
                val priceAlerts by priceAlertModel.state.collectAsStateWithLifecycle()
                val warnings by warningsModel.state.collectAsStateWithLifecycle()
                val state by model.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.devicePlace, state.namingLocation) {
                    if (!state.namingLocation) warningsModel.place(state.devicePlace)
                }
                LaunchedEffect(state.prices) { priceAlertModel.prices(state.prices) }
                LaunchedEffect(pricesRequest) { if (pricesRequest > 0) model.refreshPrices() }
                val priceNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> priceAlertModel.enable(granted) }
                val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    warningsModel.enable(granted)
                }
                var permitted by remember { mutableStateOf(locator.permitted()) }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permitted = locator.permitted(); model.permissionChanged(permitted)
                }
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner, model) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) {
                            permitted = locator.permitted(); model.start(permitted); warningsModel.start(); priceAlertModel.resume()
                        } else if (event == Lifecycle.Event.ON_STOP) { model.stop(); warningsModel.stop() }
                    }
                    owner.lifecycle.addObserver(observer)
                    if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { model.start(permitted); warningsModel.start(); priceAlertModel.resume() }
                    onDispose { owner.lifecycle.removeObserver(observer); model.stop(); warningsModel.stop() }
                }
                AppScreen(state, permitted,
                    onPermission = { permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
                    onSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) },
                    onLocationSettings = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onWeatherRefresh = model::refreshWeather, onPricesRefresh = model::refreshPrices,
                    onResolution = model::selectResolution, onVat = model::selectVat,
                    warnings = warnings, onWarningsRefresh = warningsModel::refresh,
                    onWarningsEnable = { enabled ->
                        if (!enabled) warningsModel.enable(false)
                        else if (WarningService(applicationContext).allowed()) warningsModel.enable(true)
                        else notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }, onWarningsInterval = warningsModel::interval, onWarningsTest = warningsModel::test,
                    onNotificationSettings = { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) },
                    warningsRequest = warningsRequest, priceAlerts=priceAlerts, pricesRequest=pricesRequest, priceDateRequest=priceDateRequest,
                    onPriceAlertsTest = priceAlertModel::test,
                    onPlaceSearch = model::searchPlaces, onPlaceSelect = model::selectPlace,
                    onPlaceFavorite = model::toggleFavorite, onCurrentLocation = model::useCurrentLocation,
                    onPriceAlerts = { enabled ->
                        if (!enabled) priceAlertModel.enable(false)
                        else if (PriceAlerts(applicationContext).allowed()) priceAlertModel.enable(true)
                        else priceNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
            }
        }
    }
}
