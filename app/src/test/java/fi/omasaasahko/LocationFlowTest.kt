package fi.omasaasahko

import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class LocationFlowTest {
    private val now = Instant.parse("2026-09-17T12:00:00Z")
    private val espoo = Place(60.27, 24.75, "Espoo", now)
    private val porvoo = Place(60.39, 25.66, "Porvoo", now.plusSeconds(60))
    private val favorite = PlaceResult("porvoo", "Porvoo", "Porvoo", 60.39, 25.66)
    private class Repo : DataRepository {
        val places = mutableListOf<Place>()
        var priceCalls = 0
        override suspend fun cached() = CachedData(emptyMap(), null)
        override suspend fun weather(source: WeatherSource, place: Place, now: Instant): Forecast {
            places += place
            return Forecast(source, place, now, listOf(WeatherHour(now, 15.0)), emptyList())
        }
        override suspend fun prices(now: Instant): PriceData { priceCalls++; return PriceData(now, emptyList()) }
    }

    @Test fun `every foreground entry refreshes device coordinates and prices within fifteen minutes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            var current = espoo
            val saved = mutableListOf<Pair<Place, Boolean>>()
            val repo = Repo()
            val model = AppViewModel(repo, object : LocationProvider { override suspend fun locate() = current },
                clock = Clock.fixed(now, ZoneOffset.UTC), ioDispatcher = dispatcher, onDevicePlace = { p, ready -> saved += p to ready })
            model.start(true); runCurrent(); model.stop(); runCurrent()
            current = porvoo
            model.start(true); runCurrent()
            assertEquals(porvoo, model.state.value.devicePlace)
            assertEquals(porvoo, saved.last().first)
            assertTrue(saved.last().second)
            assertEquals(2, repo.priceCalls)
            assertEquals(2, repo.places.count { it == porvoo })
            model.stop()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `forecasts and saved coordinates precede slow name lookup`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val saved = mutableListOf<Pair<Place, Boolean>>()
            val repo = Repo()
            val model = AppViewModel(repo, object : LocationProvider {
                override suspend fun locate() = espoo.copy(name = "Nykyinen sijainti")
                override suspend fun describe(place: Place): Place { delay(6_000); return place.copy(name = "Espoo", nearbyName = "Nikunmäki") }
            }, ioDispatcher = dispatcher, onDevicePlace = { p, ready -> saved += p to ready })
            model.start(true); runCurrent()
            assertEquals(2, repo.places.size)
            assertEquals(espoo.latitude, saved.single().first.latitude, 0.0)
            assertFalse(saved.single().second)
            assertTrue(model.state.value.namingLocation)
            advanceTimeBy(6_001); runCurrent()
            assertTrue(saved.last().second)
            assertEquals("Nikunmäki", model.state.value.place?.nearbyName)
            assertEquals(2, repo.places.size)
            model.stop()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `closing during name lookup leaves fresh coordinates ready for background warnings`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val saved = mutableListOf<Pair<Place, Boolean>>()
            val model = AppViewModel(Repo(), object : LocationProvider {
                override suspend fun locate() = porvoo
                override suspend fun describe(place: Place): Place { delay(6_000); return place }
            }, ioDispatcher = dispatcher, onDevicePlace = { p, ready -> saved += p to ready })
            model.start(true); runCurrent()
            assertFalse(saved.last().second)
            model.stop(); runCurrent()
            assertEquals(porvoo to true, saved.last())
            advanceTimeBy(6_001); runCurrent()
            assertEquals(2, saved.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `browsed favorite remains independent from a later device fix and warning location`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            val saved = mutableListOf<Place>()
            val model = AppViewModel(Repo(), object : LocationProvider {
                override suspend fun locate(): Place { delay(1_000); return espoo }
            }, ioDispatcher = dispatcher, onDevicePlace = { place, _ -> saved += place })
            model.start(true); runCurrent(); model.selectPlace(favorite); runCurrent()
            advanceTimeBy(1_001); runCurrent()
            assertEquals("Porvoo", model.state.value.place?.name)
            assertEquals(espoo, model.state.value.devicePlace)
            assertTrue(saved.all { it == espoo })
            model.permissionChanged(false); model.refreshWeather(); runCurrent()
            assertEquals("Porvoo", model.state.value.place?.name)
            assertNotNull(model.state.value.weather.getValue(WeatherSource.FMI).forecast)
            model.stop()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `late name result cannot replace a newer device position`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            var current = espoo
            val saved = mutableListOf<Place>()
            val model = AppViewModel(Repo(), object : LocationProvider {
                override suspend fun locate() = current
                override suspend fun describe(place: Place): Place {
                    if (place.name == "Espoo") withContext(NonCancellable) { delay(3_000) }
                    return place
                }
            }, ioDispatcher = dispatcher, onDevicePlace = { place, _ -> saved += place })
            model.start(true); runCurrent(); current = porvoo; model.refreshWeather(); runCurrent()
            advanceTimeBy(3_001); runCurrent()
            assertEquals(porvoo, model.state.value.place)
            assertEquals(porvoo, saved.last())
            model.stop()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `superseded search result is ignored and favorite storage is independent`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            var persisted = listOf(favorite)
            val store = object : FavoritePlaces {
                override fun load() = persisted
                override fun save(places: List<PlaceResult>) { persisted = places }
            }
            val model = AppViewModel(Repo(), object : LocationProvider { override suspend fun locate() = espoo },
                ioDispatcher = dispatcher, favoriteStore = store, placeSearch = object : PlaceSearch {
                    override suspend fun search(query: String): List<PlaceResult> {
                        if (query == "Espoo") withContext(NonCancellable) { delay(2_000) }
                        return listOf(favorite.copy(id = query, name = query, municipality = query))
                    }
                })
            runCurrent(); assertEquals(listOf(favorite), model.state.value.favorites)
            model.searchPlaces("Espoo"); advanceTimeBy(351); runCurrent()
            model.searchPlaces("Porvoo"); advanceTimeBy(2_001); runCurrent()
            assertEquals("Porvoo", model.state.value.searchResults.single().name)
            model.toggleFavorite(favorite); assertTrue(persisted.isEmpty())
            model.toggleFavorite(favorite); assertEquals(listOf(favorite), persisted)
            model.stop()
        } finally { Dispatchers.resetMain() }
    }
}
