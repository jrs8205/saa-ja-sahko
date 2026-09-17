package fi.omasaasahko

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryRecoveryTest {
    private val now = Instant.parse("2026-09-17T12:00:00Z")
    private val device = Place(60.27, 24.75, "Espoo", now, origin = PlaceOrigin.DEVICE)
    private val favorite = PlaceResult("porvoo", "Porvoo", "Porvoo", 60.39, 25.66).place(now.plusSeconds(1))
    private val body = """{"timezone":"Europe/Helsinki","hourly":{"time":[${now.epochSecond}],"temperature_2m":[15]},"daily":{"time":[]}}"""
    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
    }.build()

    @Test fun `favorite forecast survives separately without overwriting offline device weather`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        WarningService(app).savePlace(device)
        val repo = Repository(app, http = http)
        val expected = repo.weather(WeatherSource.OPEN_METEO, device, now)
        repo.weather(WeatherSource.OPEN_METEO, favorite, now.plusSeconds(1))
        val cached = Repository(app).cached()
        assertEquals(device, cached.devicePlace)
        assertEquals(expected, cached.weather[WeatherSource.OPEN_METEO])
        assertTrue(File(app.filesDir, "forecast-cache/OPEN_METEO-SELECTED.json").isFile)
    }

    @Test fun `legacy forecast migrates only when its coordinates and fix time match the authoritative device`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val service = WarningService(app); service.savePlace(device)
        app.getSharedPreferences("weather-place-name", Context.MODE_PRIVATE).edit().putString("place", favorite.toJson().toString()).commit()
        val directory = File(app.filesDir, "forecast-cache").apply { mkdirs() }
        val file = File(directory, "OPEN_METEO.json")
        fun store(place: Place) { file.writeText(JSONObject().put("fetched", now.toString()).put("body", body)
            .put("place", place.toJson().apply { remove("origin") }).toString()) }
        store(device)
        val cache = Repository(app).cached()
        assertEquals(service.place(), cache.devicePlace)
        assertEquals(device, cache.weather[WeatherSource.OPEN_METEO]?.place)
        store(favorite)
        assertTrue(Repository(app).cached().weather.isEmpty())
        store(device.copy(locatedAt = now.minusSeconds(1)))
        assertTrue(Repository(app).cached().weather.isEmpty())
    }

    @Test fun `unclassified places cannot be saved as notification locations`() {
        val unknown = Place(60.27, 24.75, "Espoo", now)
        assertEquals(PlaceOrigin.UNKNOWN, unknown.origin)
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(runCatching { WarningService(app).savePlace(unknown) }.exceptionOrNull() is IllegalArgumentException)
    }
}
