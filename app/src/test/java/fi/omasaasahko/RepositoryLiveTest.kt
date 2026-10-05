package fi.omasaasahko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.omasaasahko.data.Repository
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Explicit opt-in integration check; ordinary unit tests do not depend on the internet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryLiveTest {
    @Test fun `real providers parse and round trip through the app cache`() {
        assumeTrue(System.getenv("SAA_LIVE_API_TESTS") == "1")
        runBlocking {
            val now = Instant.now()
            val repo = Repository(ApplicationProvider.getApplicationContext<Context>())
            val place = Place(60.2925, 25.0408, "Tikkurila, Vantaa", now, origin = PlaceOrigin.DEVICE)
            val fmi = async { repo.weather(WeatherSource.FMI, place, now) }
            val meteo = async { repo.weather(WeatherSource.OPEN_METEO, place, now) }
            val prices = async { repo.prices(now) }
            val a = fmi.await(); val b = meteo.await(); val c = prices.await()
            assertTrue(a.hours.size >= 24)
            assertEquals(7, b.days.size)
            assertTrue(c.quarters.size >= 92)
            assertNotNull(a.current(now))
            assertNotNull(b.current(now))
            assertNotNull("Finnish reference location should have a recent observation", a.observation)
            val solarDay = a.days.first { it.date == now.atZone(HELSINKI).toLocalDate() }
            assertNotNull("FMI sunrise", solarDay.sunrise)
            assertNotNull("FMI sunset", solarDay.sunset)
            println("FMI solar: ${clockLabel(solarDay.sunrise!!)} / ${clockLabel(solarDay.sunset!!)}")
            val cached = repo.cached()
            assertEquals(a, cached.weather[WeatherSource.FMI])
            assertEquals(b, cached.weather[WeatherSource.OPEN_METEO])
            assertEquals(c, cached.prices)
            println("Live APIs: FMI=${a.hours.size}h, observation=${a.observation != null}; Open-Meteo=${b.hours.size}h/${b.days.size}d; electricity=${c.quarters.size} quarters. Cache round trip OK.")
        }
    }
}
