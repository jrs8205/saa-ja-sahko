package fi.omasaasahko

import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val now = Instant.parse("2026-09-16T09:00:00Z")
    private val place = Place(60.29, 24.84, "Vantaa", now)
    private fun forecast(source: WeatherSource, p: Place = place) = Forecast(source, p, now, listOf(WeatherHour(now, 15.0)), emptyList())
    private open inner class Repo : DataRepository {
        override suspend fun cached() = CachedData(emptyMap(), null)
        override suspend fun weather(source: WeatherSource, place: Place, now: Instant) = forecast(source, place)
        override suspend fun prices(now: Instant) = PriceData(now, emptyList())
    }
    @Test fun `one weather service can fail without hiding the other`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val repo = object : Repo() {
                override suspend fun weather(source: WeatherSource, place: Place, now: Instant): Forecast {
                    if (source == WeatherSource.FMI) error("offline")
                    return forecast(source, place)
                }
            }
            val model = AppViewModel(repo, object : LocationProvider { override suspend fun locate() = place },
                clock = Clock.fixed(now, ZoneOffset.UTC), ioDispatcher = dispatcher)
            model.start(true); runCurrent()
            assertNull(model.state.value.weather.getValue(WeatherSource.FMI).forecast)
            assertNotNull(model.state.value.weather.getValue(WeatherSource.FMI).error)
            assertNotNull(model.state.value.weather.getValue(WeatherSource.OPEN_METEO).forecast)
            assertFalse(model.state.value.weatherLoading)
            model.stop(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `revoking permission cancels pending location and electricity still works`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val locator = object : LocationProvider { override suspend fun locate(): Place { delay(10_000); return place } }
            val model = AppViewModel(Repo(), locator, clock = Clock.fixed(now, ZoneOffset.UTC), ioDispatcher = dispatcher)
            model.start(true); runCurrent()
            assertTrue(model.state.value.locating)
            model.permissionChanged(false); advanceTimeBy(12_000); runCurrent()
            assertNull(model.state.value.place)
            assertFalse(model.state.value.weatherLoading)
            assertNotNull(model.state.value.prices)
            model.stop(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `different location clears cached forecast before failed refresh`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val repo = object : Repo() {
                override suspend fun cached() = CachedData(mapOf(WeatherSource.FMI to forecast(WeatherSource.FMI)), null)
                override suspend fun weather(source: WeatherSource, place: Place, now: Instant): Forecast { error("offline") }
            }
            val model = AppViewModel(repo, object : LocationProvider { override suspend fun locate() = place.copy(latitude = 61.5, name = "Tampere") },
                clock = Clock.fixed(now, ZoneOffset.UTC), ioDispatcher = dispatcher)
            model.start(true); runCurrent()
            assertEquals("Tampere", model.state.value.place?.name)
            assertNull(model.state.value.weather.getValue(WeatherSource.FMI).forecast)
            model.stop(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `stopping foreground cancels delayed work and permits retry on resume`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            var calls = 0
            val repo = object : Repo() {
                override suspend fun prices(now: Instant): PriceData { calls++; delay(10_000); return super.prices(now) }
            }
            val model = AppViewModel(repo, object : LocationProvider { override suspend fun locate() = place },
                clock = Clock.fixed(now, ZoneOffset.UTC), ioDispatcher = dispatcher)
            model.start(false); runCurrent(); model.stop(); advanceTimeBy(12_000); runCurrent()
            assertNull(model.state.value.prices)
            model.start(false); runCurrent(); assertEquals(2, calls)
            advanceTimeBy(10_001); runCurrent(); assertNotNull(model.state.value.prices)
            model.stop(); runCurrent()
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `VAT preference is restored and saved independently of resolution`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            var persisted: Boolean? = null
            val model = AppViewModel(Repo(), object : LocationProvider { override suspend fun locate() = place },
                initialResolution = Resolution.HOUR, initialVat = false, saveVat = { persisted = it }, ioDispatcher = dispatcher)
            runCurrent()
            assertFalse(model.state.value.includeVat)
            model.selectVat(true)
            assertTrue(model.state.value.includeVat)
            assertEquals(true, persisted)
            assertEquals(Resolution.HOUR, model.state.value.resolution)
        } finally { Dispatchers.resetMain() }
    }
}
