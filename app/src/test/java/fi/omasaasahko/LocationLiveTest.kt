package fi.omasaasahko

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationLiveTest {
    @Test fun `MML searches public municipalities and names beyond the home reference point`() = runBlocking {
        assumeTrue(System.getenv("SAA_LOCATION_CHECK") == "1")
        val mml = MmlPlaceNames()
        listOf("Porvoo", "Espoo", "Inari").forEach { query ->
            val result = mml.search(query).first()
            assertEquals(query, result.name)
            assertEquals("Kunta", result.kind)
        }
        assertTrue(mml.search("Borgå").any { it.municipality == "Porvoo" })
        val point = mml.search("Nikunmäki").first { it.municipality == "Espoo" }
        val result = mml.reverseCandidates(point.latitude, point.longitude, PlaceNames.FALLBACK_RADIUS_METERS).first()
        assertEquals("Nikunmäki", result.name)
        assertEquals("Espoo", result.municipality)
    }
    @Test fun `MML answers Swedish and English requests with translated labels`() = runBlocking {
        assumeTrue(System.getenv("SAA_LOCATION_CHECK") == "1")
        val swedish = MmlPlaceNames(language = { AppLanguage.SV }).search("Porvoo").first { it.kind == "Kommun" }
        assertEquals("Borgå", swedish.name)
        val english = MmlPlaceNames(language = { AppLanguage.EN }).search("Porvoo").first { it.kind == "Municipality" }
        assertEquals("Porvoo", english.name)
    }
    @Test fun `FMI observations identify nearby official stations for Porvoo and Inari`() = runBlocking {
        assumeTrue(System.getenv("SAA_LIVE_API_TESTS") == "1")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = Instant.now()
        // Public municipality reference points, not private phone coordinates.
        listOf(Place(60.3953719, 25.6665595, "Porvoo", now, origin = PlaceOrigin.DEVICE), Place(68.90, 27.03, "Inari", now, origin = PlaceOrigin.DEVICE)).forEach { place ->
            // Independent disk round trips: java.io.File.renameTo cannot replace an existing
            // AtomicFile target under the Windows host JVM as it does on Android/Linux.
            val repo = Repository(object : ContextWrapper(context) {
                override fun getFilesDir(): File = File(context.filesDir, place.name).apply { mkdirs() }
            })
            val result = repo.weather(WeatherSource.FMI, place, now)
            assertNotNull("${place.name}: station", result.observationStation)
            assertNotNull("${place.name}: observation", result.recentObservation(now))
            assertTrue(result.observationStation!!.distanceMeters <= 300_000)
            assertEquals(place.latitude, result.place.latitude, 0.0)
            assertEquals(place.longitude, result.place.longitude, 0.0)
            assertEquals(result, repo.cached().weather[WeatherSource.FMI])
            println("${place.name}: ${result.observationStation.name}, ${decimal(result.observationStation.distanceMeters / 1000, AppLanguage.FI)} km; ${result.observation!!.time}")
        }
    }
}
