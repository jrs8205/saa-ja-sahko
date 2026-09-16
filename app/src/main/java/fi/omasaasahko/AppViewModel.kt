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
    val locating: Boolean = false,
    val locationError: String? = null,
    val weather: Map<WeatherSource, SourceState> = WeatherSource.entries.associateWith { SourceState() },
    val prices: PriceData? = null,
    val pricesLoading: Boolean = false,
    val pricesError: String? = null,
    val resolution: Resolution = Resolution.QUARTER,
    val includeVat: Boolean = true,
) {
    val weatherLoading: Boolean get() = locating || weather.values.any { it.loading }
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
) : ViewModel() {
    private val mutable = MutableStateFlow(AppState(now = clock.instant(), resolution = initialResolution, includeVat = initialVat))
    val state = mutable.asStateFlow()
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
        mutable.update { s -> s.copy(place = place, prices = cache.prices,
            weather = WeatherSource.entries.associateWith { source ->
                SourceState(cache.weather[source]?.takeIf { place != null && samePlace(it.place, place) })
            }) }
    }

    fun start(permitted: Boolean) {
        hasPermission = permitted
        if (foregroundJob?.isActive == true) return
        foregroundJob = viewModelScope.launch {
            cacheJob.join()
            while (isActive) {
                val now = clock.instant()
                val changedDay = mutable.value.now.atZone(HELSINKI).toLocalDate() != now.atZone(HELSINKI).toLocalDate()
                mutable.update { it.copy(now = now) }
                if (hasPermission && due(lastWeatherAttempt, now) && weatherJob?.isActive != true) refreshWeather()
                if ((changedDay || due(lastPriceAttempt, now)) && priceJob?.isActive != true) refreshPrices()
                delay(30_000L - now.toEpochMilli().mod(30_000L))
            }
        }
    }

    fun stop() {
        foregroundJob?.cancel(); foregroundJob = null
        weatherGeneration++; priceGeneration++
        weatherJob?.cancel(); priceJob?.cancel()
        // An interrupted refresh should retry when the user returns.
        if (mutable.value.weatherLoading) lastWeatherAttempt = null
        if (mutable.value.pricesLoading) lastPriceAttempt = null
        mutable.update { it.copy(locating = false, pricesLoading = false,
            weather = it.weather.mapValues { (_, s) -> s.copy(loading = false) }) }
    }

    fun permissionChanged(permitted: Boolean) {
        hasPermission = permitted
        if (permitted) refreshWeather()
        else {
            weatherGeneration++; weatherJob?.cancel()
            mutable.update { it.copy(locating = false, weather = it.weather.mapValues { (_, s) -> s.copy(loading = false) }) }
        }
    }
    fun selectResolution(resolution: Resolution) {
        mutable.update { it.copy(resolution = resolution) }; saveResolution(resolution)
    }
    fun selectVat(include: Boolean) {
        mutable.update { it.copy(includeVat = include) }; saveVat(include)
    }

    fun refreshWeather() {
        if (!hasPermission) return
        weatherJob?.cancel()
        val generation = ++weatherGeneration
        weatherJob = viewModelScope.launch {
            cacheJob.join()
            val now = clock.instant()
            lastWeatherAttempt = now
            mutable.update { it.copy(locating = true, locationError = null) }
            try {
                val place = location.locate()
                ensureActive()
                if (generation != weatherGeneration) return@launch
                mutable.update { s -> s.copy(place = place, locating = false,
                    weather = s.weather.mapValues { (_, old) ->
                        old.copy(forecast = old.forecast?.takeIf { samePlace(it.place, place) }, loading = true, error = null)
                    }) }
                supervisorScope {
                    WeatherSource.entries.forEach { source -> launch {
                        try {
                            val forecast = withContext(ioDispatcher) { repository.weather(source, place, now) }
                            ensureActive()
                            if (generation == weatherGeneration) mutable.update { s ->
                                s.copy(weather = s.weather + (source to SourceState(forecast)))
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            if (generation == weatherGeneration) mutable.update { s -> s.copy(weather = s.weather +
                                (source to s.weather.getValue(source).copy(loading = false, error = "Päivitys epäonnistui. Yritä uudelleen."))) }
                        }
                    } }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == weatherGeneration) mutable.update { it.copy(locating = false,
                    locationError = e.message ?: "Paikannus epäonnistui.") }
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
