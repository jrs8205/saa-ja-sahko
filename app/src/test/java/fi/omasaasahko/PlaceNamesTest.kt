package fi.omasaasahko

import android.location.Address
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.FINNISH
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
    private val lat = 60.2757625
    private val lon = 24.8304846
    // Public NLS name point, not a device location. Data: NLS Finland, CC BY 4.0, 17 Sep 2026.
    private fun fixture() = javaClass.getResource("/mml-kaivopuisto.json")!!.readText()

    @Test fun `official name point gives Kaivopuisto without replacing municipality or district`() {
        val nearby = PlaceNames.parse(fixture(), lat, lon)!!
        assertEquals("Kaivopuisto", nearby.name)
        assertEquals("Vantaa", nearby.municipality)
        assertEquals(0.0, nearby.distanceMeters, 0.01)
        val place = PlaceNames.resolve(lat, lon, Instant.EPOCH, AddressName("Vantaa", "Tikkurila"), nearby)
        assertEquals("Tikkurila, Vantaa", place.name)
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
        assertEquals("Kaivopuisto", PlaceNames.parse(root.toString(), lat, lon)?.name)
        assertNull(PlaceNames.parse(root.toString(), 61.5, 23.7))
    }

    @Test fun `failed sources deleted names and business names do not become a location`() {
        val root = JSONObject(fixture())
        root.getJSONObject("geocoding").getJSONObject("sources").getJSONObject("geographic-names").put("status", "failure")
        assertNull(PlaceNames.parse(root.toString(), lat, lon))
        val business = JSONObject(fixture())
        val features = business.getJSONArray("features")
        for (i in 0 until features.length()) features.getJSONObject(i).getJSONObject("properties").put("placeTypeCategory",7)
        assertNull(PlaceNames.parse(business.toString(), lat, lon))
        val deleted = JSONObject(fixture())
        val names = deleted.getJSONArray("features")
        for (i in 0 until names.length()) names.getJSONObject(i).getJSONObject("properties").put("placeDeletionTime","2026-09-17")
        assertNull(PlaceNames.parse(deleted.toString(),lat,lon))
    }

    @Test fun `fallback never invents district or mixes municipalities or duplicate names`() {
        val nearby = NearbyName("Kaivopuisto","Vantaa",10.0)
        assertNull(PlaceNames.resolve(lat,lon,Instant.EPOCH,AddressName("Espoo","Järvenperä"),nearby).nearbyName)
        val noAndroid = PlaceNames.resolve(lat,lon,Instant.EPOCH,null,nearby)
        assertEquals("Vantaa",noAndroid.name)
        assertEquals("Kaivopuisto",noAndroid.nearbyName)
        assertNull(PlaceNames.resolve(lat,lon,Instant.EPOCH,AddressName("Vantaa","Kaivopuisto"),nearby).nearbyName)
        assertEquals("Nykyinen sijainti",PlaceNames.resolve(lat,lon,Instant.EPOCH,null,null).name)
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
        assertNull(MmlPlaceNames("",client).reverse(lat,lon))
        assertTrue(requests.isEmpty())
        assertEquals("Kaivopuisto",MmlPlaceNames("test-key",client).reverse(lat,lon)?.name)
        assertNull(MmlPlaceNames("test-key",client).reverse(lat,lon))
        assertEquals(2, requests.size)
        requests.forEach { request ->
            assertEquals("geographic-names",request.url.queryParameter("sources"))
            assertEquals("750",request.url.queryParameter("boundary.circle.radius"))
            assertEquals(lat.toString(),request.url.queryParameter("point.lat"))
            assertEquals(lon.toString(),request.url.queryParameter("point.lon"))
            assertEquals("100",request.url.queryParameter("size"))
            assertEquals("fi",request.url.queryParameter("lang"))
            assertNull(request.url.queryParameter("api-key"))
            assertEquals(Credentials.basic("test-key",""),request.header("Authorization"))
        }
    }

    @Test fun `next eligible name survives boundary or duplicate rejection`() {
        val candidates = listOf(NearbyName("Lähin", "Espoo", 10.0), NearbyName("Tikkurila", "Vantaa", 20.0),
            NearbyName("Kaivopuisto", "Vantaa", 30.0))
        assertEquals("Kaivopuisto", PlaceNames.resolve(lat, lon, Instant.EPOCH, AddressName("Vantaa", "Tikkurila"), candidates).nearbyName)
    }

    @Test fun `Swedish-only spelling is usable`() {
        val root = JSONObject(fixture())
        val features = root.getJSONArray("features")
        for (i in 0 until features.length()) {
            val names = features.getJSONObject(i).getJSONObject("properties").getJSONArray("name")
            for (j in names.length() - 1 downTo 0) if (names.getJSONObject(j).optString("language") == "fin") names.remove(j)
        }
        assertEquals("Brunnsparken", PlaceNames.parse(root.toString(), lat, lon)?.name)
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
        val result=MmlPlaceNames().reverse(lat,lon)
        assertNotNull("Official reverse API did not return an eligible nearby name",result)
        assertEquals("Kaivopuisto",result!!.name)
        assertEquals("Vantaa",result.municipality)
    }
}
