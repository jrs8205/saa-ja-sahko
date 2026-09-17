package fi.omasaasahko

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class SourceState(val forecast: Forecast? = null, val loading: Boolean = false, val error: String? = null)
data class AppState(
    val now: Instant = Instant.now(),
    val place: Place? = null,
    val devicePlace: Place? = null,
    val selectedPlace: PlaceResult? = null,
    val favorites: List<PlaceResult> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<PlaceResult> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val namingLocation: Boolean = false,
    val locating: Boolean = false,
    val locationError: String? = null,
    val weather: Map<WeatherSource, SourceState> = WeatherSource.entries.associateWith { SourceState() },
    val prices: PriceData? = null,
    val pricesLoading: Boolean = false,
    val pricesError: String? = null,
    val resolution: Resolution = Resolution.QUARTER,
    val includeVat: Boolean = true,
) {
    val weatherLoading: Boolean get() = (selectedPlace == null && locating) || weather.values.any { it.loading }
}

class AppViewModel(
    private val repository: DataRepository,
    private val location: LocationProvider,
    initialResolution: Resolution = Resolution.QUARTER,
    private val saveResolution: (Resolution) -> Unit = {},
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    initialVat: Boolean = true,
    private val saveVat: (Boolean) -> Unit = {},
    private val placeSearch: PlaceSearch? = null,
    private val favoriteStore: FavoritePlaces? = null,
    private val onDeviceLocationStart: () -> Unit = {},
    private val onDevicePlace: (Place, Boolean) -> Unit = { _, _ -> },
) : ViewModel() {
    private val mutable = MutableStateFlow(AppState(now = clock.instant(), resolution = initialResolution, includeVat = initialVat, favorites = favoriteStore?.load().orEmpty()))
    val state = mutable.asStateFlow()
    private var locationJob: Job? = null
    private var nameJob: Job? = null
    private var searchJob: Job? = null
    private var locationGeneration = 0
    private var searchGeneration = 0
    private var weatherJob: Job? = null
    private var priceJob: Job? = null
    private var foregroundJob: Job? = null
    private var lastWeatherAttempt: Instant? = null
    private var lastPriceAttempt: Instant? = null
    private var weatherGeneration = 0
    private var priceGeneration = 0
    private var hasPermission = false

    private val cacheJob = viewModelScope.launch {
        val cache = repository.cached()
        val place = cache.weather.values.maxByOrNull { it.fetchedAt }?.place
        mutable.update { s -> if (s.place != null || s.selectedPlace != null) s.copy(prices = s.prices ?: cache.prices)
            else s.copy(place = place, prices = cache.prices,
            weather = WeatherSource.entries.associateWith { source ->
                SourceState(cache.weather[source]?.takeIf { place != null && samePlace(it.place, place) })
            }) }
    }

    fun start(permitted: Boolean) {
        hasPermission = permitted
        if (foregroundJob?.isActive == true) return
        if (hasPermission) onDeviceLocationStart()
        foregroundJob = viewModelScope.launch {
            cacheJob.join()
            // A foreground entry is a location refresh, even inside the 15-minute forecast interval.
            if (hasPermission) refreshDeviceLocation()
            if (mutable.value.selectedPlace != null) refreshWeather()
            refreshPrices()
            while (isActive) {
                val now = clock.instant()
                val changedDay = mutable.value.now.atZone(HELSINKI).toLocalDate() != now.atZone(HELSINKI).toLocalDate()
                mutable.update { it.copy(now = now) }
                if ((hasPermission || mutable.value.selectedPlace != null) && due(lastWeatherAttempt, now) &&
                    weatherJob?.isActive != true && locationJob?.isActive != true) refreshWeather()
                if ((changedDay || due(lastPriceAttempt, now)) && priceJob?.isActive != true) refreshPrices()
                delay(30_000L - now.toEpochMilli().mod(30_000L))
            }
        }
    }

    fun stop() {
        // Coordinates are already fresh; closing during the optional name lookup must not
        // leave background warnings paused until the next foreground visit.
        if (mutable.value.namingLocation) mutable.value.devicePlace?.let { onDevicePlace(it, true) }
        foregroundJob?.cancel(); foregroundJob = null
        weatherGeneration++; priceGeneration++; locationGeneration++; searchGeneration++
        locationJob?.cancel(); nameJob?.cancel(); searchJob?.cancel()
        weatherJob?.cancel(); priceJob?.cancel()
        // An interrupted refresh should retry when the user returns.
        if (mutable.value.weatherLoading) lastWeatherAttempt = null
        if (mutable.value.pricesLoading) lastPriceAttempt = null
        mutable.update { it.copy(locating = false, namingLocation = false, searching = false, pricesLoading = false,
            weather = it.weather.mapValues { (_, s) -> s.copy(loading = false) }) }
    }

    fun permissionChanged(permitted: Boolean) {
        hasPermission = permitted
        if (permitted) refreshDeviceLocation()
        else {
            locationGeneration++; locationJob?.cancel(); nameJob?.cancel()
            if (mutable.value.selectedPlace == null) { weatherGeneration++; weatherJob?.cancel() }
            mutable.update { it.copy(locating = false, namingLocation = false,
                weather = if (it.selectedPlace == null) it.weather.mapValues { (_, value) -> value.copy(loading = false) } else it.weather) }
        }
    }

    fun searchPlaces(query: String) {
        searchJob?.cancel()
        val generation = ++searchGeneration
        val text = query.take(200)
        mutable.update { it.copy(searchQuery = text, searchResults = emptyList(), searchError = null, searching = text.trim().length >= 2) }
        if (text.trim().length < 2) return
        searchJob = viewModelScope.launch {
            delay(350)
            try {
                val results = checkNotNull(placeSearch) { "Paikkahaku ei ole käytettävissä." }.search(text.trim())
                ensureActive()
                if (generation == searchGeneration) mutable.update { it.copy(searchResults = results, searching = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (generation == searchGeneration) mutable.update { it.copy(searching = false,
                searchError = "Paikkahaku epäonnistui. Tarkista yhteys ja yritä uudelleen.") } }
        }
    }

    fun selectPlace(place: PlaceResult) {
        searchJob?.cancel(); searchGeneration++
        mutable.update { it.copy(selectedPlace = place, searching = false) }
        loadWeather(place.place(clock.instant()))
    }

    fun useCurrentLocation() {
        mutable.update { it.copy(selectedPlace = null) }
        if (hasPermission) refreshDeviceLocation()
        else {
            weatherGeneration++; weatherJob?.cancel()
            mutable.update { it.copy(place = it.devicePlace, weather = WeatherSource.entries.associateWith { SourceState() }) }
        }
    }

    fun toggleFavorite(place: PlaceResult) {
        val old = mutable.value.favorites
        val next = if (old.any { it.id == place.id }) old.filterNot { it.id == place.id }
            else if (old.size < 50) old + place else old
        favoriteStore?.save(next)
        mutable.update { it.copy(favorites = next) }
    }

    fun selectResolution(resolution: Resolution) {
        mutable.update { it.copy(resolution = resolution) }; saveResolution(resolution)
    }
    fun selectVat(include: Boolean) {
        mutable.update { it.copy(includeVat = include) }; saveVat(include)
    }

    fun refreshWeather() {
        val selected = mutable.value.selectedPlace
        if (selected != null) loadWeather(selected.place(clock.instant()))
        else if (hasPermission) refreshDeviceLocation()
    }

    private fun refreshDeviceLocation() {
        locationJob?.cancel(); nameJob?.cancel()
        val generation = ++locationGeneration
        if (mutable.value.selectedPlace == null) lastWeatherAttempt = clock.instant()
        onDeviceLocationStart()
        mutable.update { it.copy(locating = true, namingLocation = false, locationError = null) }
        locationJob = viewModelScope.launch {
            cacheJob.join()
            try {
                val place = location.locate()
                ensureActive()
                if (generation != locationGeneration) return@launch
                // Persist coordinates before UI effects, forecasts or name-service requests can run.
                onDevicePlace(place, false)
                mutable.update { it.copy(devicePlace = place, locating = false, namingLocation = true) }
                if (mutable.value.selectedPlace == null) loadWeather(place)
                nameJob = viewModelScope.launch {
                    val named = try { location.describe(place) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { place }
                    ensureActive()
                    if (generation != locationGeneration) return@launch
                    onDevicePlace(named, true)
                    repository.rememberPlaceName(named)
                    mutable.update { s -> s.copy(devicePlace = named, namingLocation = false,
                        place = if (s.selectedPlace == null) named else s.place,
                        weather = if (s.selectedPlace == null) s.weather.mapValues { (_, old) ->
                            old.copy(forecast = old.forecast?.let { if (samePlace(it.place, named)) it.copy(place = named) else it })
                        } else s.weather) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (generation == locationGeneration) mutable.update { it.copy(locating = false,
                namingLocation = false, locationError = "Tuoretta sijaintia ei saatu. Päivitä sijainti säävaroitusilmoituksia varten.") } }
        }
    }

    private fun loadWeather(place: Place) {
        weatherJob?.cancel()
        val generation = ++weatherGeneration
        val now = clock.instant()
        lastWeatherAttempt = now
        mutable.update { s -> s.copy(place = place, weather = s.weather.mapValues { (_, old) ->
            old.copy(forecast = old.forecast?.takeIf { samePlace(it.place, place) }, loading = true, error = null)
        }) }
        weatherJob = viewModelScope.launch {
            cacheJob.join()
            supervisorScope {
                WeatherSource.entries.forEach { source -> launch {
                    try {
                        val forecast = withContext(ioDispatcher) { repository.weather(source, place, now) }
                        ensureActive()
                        if (generation == weatherGeneration) mutable.update { s ->
                            val label = s.place?.takeIf { samePlace(it, place) } ?: place
                            s.copy(weather = s.weather + (source to SourceState(forecast.copy(place = label))))
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { if (generation == weatherGeneration) mutable.update { s -> s.copy(weather = s.weather +
                        (source to s.weather.getValue(source).copy(loading = false, error = "Päivitys epäonnistui. Yritä uudelleen."))) } }
                } }
            }
        }
    }

    fun refreshPrices() {
        priceJob?.cancel()
        val generation = ++priceGeneration
        priceJob = viewModelScope.launch {
            cacheJob.join()
            val now = clock.instant()
            lastPriceAttempt = now
            mutable.update { it.copy(pricesLoading = true, pricesError = null, now = now) }
            try {
                val prices = withContext(ioDispatcher) { repository.prices(now) }
                ensureActive()
                if (generation == priceGeneration) mutable.update { it.copy(prices = prices, pricesLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (generation == priceGeneration) mutable.update { it.copy(pricesLoading = false,
                    pricesError = "Hintojen päivitys epäonnistui. Näytetään viimeksi haetut tiedot, jos niitä on.") }
            }
        }
    }

    private fun due(previous: Instant?, now: Instant): Boolean = previous == null ||
        now < previous || Duration.between(previous, now).toMinutes() >= 15
    private fun samePlace(a: Place, b: Place) =
        kotlin.math.abs(a.latitude - b.latitude) < 0.002 && kotlin.math.abs(a.longitude - b.longitude) < 0.002
}
