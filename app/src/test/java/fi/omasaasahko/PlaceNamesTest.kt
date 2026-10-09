package fi.omasaasahko

import android.location.Address
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaceNamesTest {
    private val lat = 60.1579733
    private val lon = 24.9589938
    // Public NLS name point (Kaivopuisto park, Helsinki), not a device location. Data: NLS Finland, CC BY 4.0, 5 Oct 2026, trimmed to the four nearest features.
    private fun fixture() = javaClass.getResource("/mml-kaivopuisto.json")!!.readText()

    private fun nearest(body: String, latitude: Double, longitude: Double): NearbyName? =
        runCatching { PlaceNames.nearby(PlaceNames.points(body), latitude, longitude, PlaceNames.FALLBACK_RADIUS_METERS).firstOrNull() }.getOrNull()

    @Test fun `official MML point runs through production naming and warning storage`() = runBlocking {
        val names = PlaceNames.nearby(PlaceNames.points(fixture()), lat, lon, PlaceNames.FALLBACK_RADIUS_METERS)
        val fix = Place(lat, lon, CURRENT_LOCATION_NAME, Instant.EPOCH, accuracyMeters = 10f, nameResolved = false, origin = PlaceOrigin.DEVICE)
        val place = DevicePlaceName({ names }, { AddressName("Helsinki", "Ullanlinna") }, { true }).describe(fix)
        assertEquals("Helsinki", place.name)
        assertEquals("Kaivopuisto", place.nearbyName)
        assertEquals(lat, place.latitude, 0.0)
        assertEquals(lon, place.longitude, 0.0)
        val service = WarningService(ApplicationProvider.getApplicationContext<Application>())
        service.savePlace(place)
        assertEquals(place, service.place())
    }

    @Test fun `nearest eligible Finnish name wins independently of response order and reported distance`() {
        val root = JSONObject(fixture())
        val features = root.getJSONArray("features")
        val first = features.getJSONObject(0)
        features.put(0, features.getJSONObject(2)); features.put(2, first)
        first.getJSONObject("properties").put("distance", 9999)
        assertEquals("Kaivopuisto", nearest(root.toString(), lat, lon)?.name)
        assertNull(nearest(root.toString(), 61.5, 23.7))
    }

    @Test fun `failed sources deleted names and business names do not become a location`() {
        val root = JSONObject(fixture())
        root.getJSONObject("geocoding").getJSONObject("sources").getJSONObject("geographic-names").put("status", "failure")
        assertNull(nearest(root.toString(), lat, lon))
        val business = JSONObject(fixture())
        val features = business.getJSONArray("features")
        for (i in 0 until features.length()) features.getJSONObject(i).getJSONObject("properties").put("placeTypeCategory",7)
        assertNull(nearest(business.toString(), lat, lon))
        val deleted = JSONObject(fixture())
        val names = deleted.getJSONArray("features")
        for (i in 0 until names.length()) names.getJSONObject(i).getJSONObject("properties").put("placeDeletionTime","2026-09-17")
        assertNull(nearest(deleted.toString(),lat,lon))
    }

    @Test fun `Android district stays with first address and rural fallback is retained`() {
        fun address(city: String, district: String? = null) = Address(FINNISH).apply {
            locality=city; subLocality=district; featureName="Kadunnimi 123"
        }
        assertEquals("Vantaa",addressName(listOf(address("Vantaa"),address("Espoo","Leppävaara"),
            address("Vantaa","Tikkurila")))?.label)
        assertEquals("Vantaa",addressName(listOf(address("Vantaa")))?.label)
        assertEquals("Inari", addressName(listOf(Address(FINNISH).apply { subAdminArea = "Inari" }))?.label)
        assertEquals("Kylä", addressName(listOf(Address(FINNISH).apply { subLocality = "Kylä" }))?.label)
    }

    @Test fun `HTTP uses current MML parameters and fails back without leaking request failures`() = runBlocking {
        val requests = java.util.concurrent.CopyOnWriteArrayList<Request>()
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            val request=chain.request(); requests += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (requests.size == 1) 200 else 503)
                .message("test").body(fixture().toResponseBody()).build()
        }.build()
        assertTrue(MmlPlaceNames("",client).reverseCandidates(lat,lon).isEmpty())
        assertTrue(requests.isEmpty())
        assertEquals("Kaivopuisto",MmlPlaceNames("test-key",client).reverseCandidates(lat,lon).firstOrNull()?.name)
        assertTrue(runCatching { MmlPlaceNames("test-key",client).reverseCandidates(lat,lon) }.isFailure)
        assertEquals(2, requests.size)
        requests.forEach { request ->
            assertEquals("geographic-names",request.url.queryParameter("sources"))
            assertEquals("5000",request.url.queryParameter("boundary.circle.radius"))
            assertEquals(lat.toString(),request.url.queryParameter("point.lat"))
            assertEquals(lon.toString(),request.url.queryParameter("point.lon"))
            assertEquals("100",request.url.queryParameter("size"))
            assertEquals("fi",request.url.queryParameter("lang"))
            assertNull(request.url.queryParameter("api-key"))
            assertEquals(Credentials.basic("test-key",""),request.header("Authorization"))
        }
    }

    @Test fun `Swedish app language prefers Swedish spellings and asks MML in Swedish`() = runBlocking {
        assertEquals("Brunnsparken", PlaceNames.nearby(PlaceNames.points(fixture(), AppLanguage.SV), lat, lon, PlaceNames.FALLBACK_RADIUS_METERS).first().name)
        assertEquals("Kaivopuisto", PlaceNames.nearby(PlaceNames.points(fixture(), AppLanguage.EN), lat, lon, PlaceNames.FALLBACK_RADIUS_METERS).first().name)
        val requests = java.util.concurrent.CopyOnWriteArrayList<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(fixture().toResponseBody()).build()
        }.build()
        var language = AppLanguage.SV
        // The ViewModel keeps one instance across a language change, so the language is read per call.
        val mml = MmlPlaceNames("test-key", client, language = { language })
        assertEquals("Brunnsparken", mml.reverseCandidates(lat, lon).first().name)
        language = AppLanguage.FI
        assertEquals("Kaivopuisto", mml.reverseCandidates(lat, lon).first().name)
        assertEquals(listOf("sv", "fi"), requests.map { it.url.queryParameter("lang") })
    }

    @Test fun `Swedish-only spelling is usable`() {
        val root = JSONObject(fixture())
        val features = root.getJSONArray("features")
        for (i in 0 until features.length()) {
            val names = features.getJSONObject(i).getJSONObject("properties").getJSONArray("name")
            for (j in names.length() - 1 downTo 0) if (names.getJSONObject(j).optString("language") == "fin") names.remove(j)
        }
        assertEquals("Brunnsparken", nearest(root.toString(), lat, lon)?.name)
    }

    @Test fun `municipality search uses MML and favorite survives storage reload`() = runBlocking {
        val body = javaClass.getResource("/mml-search-porvoo.json")!!.readText()
        val captured = java.util.concurrent.atomic.AtomicReference<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            captured.set(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val results = MmlPlaceNames("test-key", client).search("Porvoo")
        assertEquals("Porvoo", results.first().label)
        assertEquals("Kunta", results.first().kind)
        assertEquals("/geocoding/v2/pelias/search", captured.get().url.encodedPath)
        assertEquals("Porvoo", captured.get().url.queryParameter("text"))
        assertEquals("geographic-names", captured.get().url.queryParameter("sources"))
        val context = ApplicationProvider.getApplicationContext<Application>()
        PlaceStorage(context).save(listOf(results.first()))
        assertEquals(listOf(results.first()), PlaceStorage(context).load())
    }

    @Test fun `live MML reverse resolves the public Kaivopuisto name point`() = runBlocking {
        assumeTrue(System.getenv("SAA_LOCATION_CHECK") == "1")
        assertTrue("MML_API_KEY must be configured for this integration test",BuildConfig.MML_API_KEY.isNotBlank())
        val result=MmlPlaceNames().reverseCandidates(lat,lon).firstOrNull()
        assertNotNull("Official reverse API did not return an eligible nearby name",result)
        assertEquals("Kaivopuisto",result!!.name)
        assertEquals("Helsinki",result.municipality)
    }
}
