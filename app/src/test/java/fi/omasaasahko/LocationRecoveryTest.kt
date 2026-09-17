package fi.omasaasahko

import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class LocationRecoveryTest {
    private val now = Instant.parse("2026-09-17T12:00:00Z")
    private val device = Place(60.27, 24.75, "Espoo", now, "Nikunmäki", 10f, origin = PlaceOrigin.DEVICE)
    private val selected = PlaceResult("porvoo", "Porvoo", "Porvoo", 60.39, 25.66)
    private open class Repo(private val cache: CachedData = CachedData(emptyMap(), null)) : DataRepository {
        override suspend fun cached() = cache
        override suspend fun weather(source: WeatherSource, place: Place, now: Instant) = Forecast(source, place, now, emptyList(), emptyList())
        override suspend fun prices(now: Instant) = PriceData(now, emptyList())
    }

    @Test fun `cold cache never identifies selected or untyped legacy place as the device`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            for (origin in listOf(PlaceOrigin.SELECTED, PlaceOrigin.UNKNOWN)) {
                val forecast = Forecast(WeatherSource.FMI, selected.place(now).copy(origin = origin), now, emptyList(), emptyList())
                val model = AppViewModel(Repo(CachedData(mapOf(WeatherSource.FMI to forecast), null, device)),
                    object : LocationProvider { override suspend fun locate() = device }, ioDispatcher = dispatcher)
                runCurrent()
                assertEquals(device, model.state.value.place)
                assertEquals(device, model.state.value.devicePlace)
                assertNull(model.state.value.weather.getValue(WeatherSource.FMI).forecast)
                model.stop()
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `return from a favorite clears its forecast even when device location fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            var fail = false
            var pending = false
            val model = AppViewModel(Repo(), object : LocationProvider {
                override suspend fun locate(): Place {
                    if (fail) throw LocationFailure("Ota puhelimen sijainti käyttöön.")
                    return device
                }
            }, ioDispatcher = dispatcher, onDeviceLocationStart = { pending = true }, onDeviceLocationEnd = { pending = false })
            model.start(true); runCurrent(); model.selectPlace(selected); runCurrent()
            fail = true; model.useCurrentLocation()
            assertEquals(device, model.state.value.place)
            assertTrue(model.state.value.weather.values.all { it.forecast?.place == device })
            runCurrent()
            assertEquals("Ota puhelimen sijainti käyttöön.", model.state.value.locationError)
            assertEquals(device, model.state.value.place)
            assertFalse(pending)
            assertTrue(model.state.value.weather.values.all { it.forecast?.place == device })
            model.stop()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `closing before the first fix or before cache load clears the pending flag`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            for (slowCache in listOf(false, true)) {
                var pending = false
                val repo = object : Repo() { override suspend fun cached(): CachedData { if (slowCache) delay(5_000); return super.cached() } }
                val model = AppViewModel(repo, object : LocationProvider { override suspend fun locate(): Place { delay(12_000); return device } },
                    ioDispatcher = dispatcher, onDeviceLocationStart = { pending = true }, onDeviceLocationEnd = { pending = false })
                model.start(true); runCurrent(); assertTrue(pending)
                model.stop(); runCurrent(); assertFalse(pending)
                advanceTimeBy(13_000); runCurrent(); assertFalse(pending)
                assertNull(model.state.value.devicePlace)
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `offline return without permission restores device forecasts without copying favorite weather`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val cached = Forecast(WeatherSource.FMI, device, now, listOf(WeatherHour(now, 14.0)), emptyList())
            val repo = object : Repo(CachedData(mapOf(WeatherSource.FMI to cached), null, device)) {
                override suspend fun weather(source: WeatherSource, place: Place, now: Instant): Forecast = error("offline")
            }
            val model = AppViewModel(repo, object : LocationProvider { override suspend fun locate(): Place = error("No permission") }, ioDispatcher = dispatcher)
            runCurrent(); model.start(false); model.selectPlace(selected); runCurrent()
            model.useCurrentLocation(); runCurrent()
            assertEquals(device, model.state.value.place)
            assertEquals(cached, model.state.value.weather.getValue(WeatherSource.FMI).forecast)
            assertNotNull(model.state.value.weather.getValue(WeatherSource.FMI).error)
            model.stop()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `an unresolved refresh keeps a nearby known name but never carries it to another city`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            var fix = device
            val saved = mutableListOf<Place>()
            val model = AppViewModel(Repo(), object : LocationProvider {
                override suspend fun locate() = fix
                override suspend fun describe(place: Place): Place { delay(6_000); return place }
            }, ioDispatcher = dispatcher, onDevicePlace = { p, _ -> saved += p })
            model.start(true); advanceTimeBy(6_001); runCurrent()
            fix = device.copy(name = CURRENT_LOCATION_NAME, nameResolved = false, latitude = device.latitude + 0.0001)
            model.refreshWeather(); runCurrent()
            assertEquals("Espoo", model.state.value.place?.name)
            advanceTimeBy(6_001); runCurrent()
            assertTrue(saved.all { it.name == "Espoo" })
            fix = selected.place(now).copy(origin = PlaceOrigin.DEVICE, name = CURRENT_LOCATION_NAME, nameResolved = false, accuracyMeters = 10f)
            model.refreshWeather(); runCurrent()
            assertFalse(model.state.value.place!!.nameResolved)
            assertNull(model.state.value.place!!.nearbyName)
            model.stop()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `completion of a cancelled old lookup cannot clear the newer pending request`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            var calls = 0; var pending = false
            val model = AppViewModel(Repo(), object : LocationProvider {
                override suspend fun locate(): Place {
                    if (++calls == 1) withContext(NonCancellable) { delay(1_000) } else delay(12_000)
                    return device
                }
            }, ioDispatcher = dispatcher, onDeviceLocationStart = { pending = true }, onDeviceLocationEnd = { pending = false })
            model.start(true); runCurrent(); model.refreshWeather(); runCurrent()
            advanceTimeBy(1_001); runCurrent()
            assertTrue(pending)
            assertTrue(model.state.value.locating)
            model.stop(); runCurrent(); assertFalse(pending)
        } finally { Dispatchers.resetMain() }
    }
}
