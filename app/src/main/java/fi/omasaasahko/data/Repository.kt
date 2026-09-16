package fi.omasaasahko.data

import android.content.Context
import android.util.AtomicFile
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CachedData(val weather: Map<WeatherSource, Forecast>, val prices: PriceData?)
interface DataRepository {
    suspend fun cached(): CachedData
    suspend fun weather(source: WeatherSource, place: Place, now: Instant): Forecast
    suspend fun prices(now: Instant): PriceData
}

class Repository(context: Context) : DataRepository {
    private val cache = File(context.filesDir, "forecast-cache").apply { mkdirs() }
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).build()

    override suspend fun cached(): CachedData = withContext(Dispatchers.IO) {
        val weather = WeatherSource.entries.mapNotNull { source ->
            read(source.name)?.let { stored -> runCatching {
                val p = stored.getJSONObject("place")
                val place = Place(p.getDouble("lat"), p.getDouble("lon"), p.getString("name"), Instant.parse(p.getString("at")))
                val at = Instant.parse(stored.getString("fetched"))
                val body = stored.getString("body")
                val result = if (source == WeatherSource.FMI) Parsers.fmi(body, place, at).copy(
                    observation = stored.optString("observation").takeIf { it.isNotBlank() }?.let { Parsers.observation(it, place) }
                ) else Parsers.openMeteo(body, place, at)
                source to result
            }.getOrNull() }
        }.toMap()
        val prices = read("prices")?.let { runCatching {
            Parsers.prices(it.getString("body"), Instant.parse(it.getString("fetched")))
        }.getOrNull() }
        CachedData(weather, prices)
    }

    override suspend fun weather(source: WeatherSource, place: Place, now: Instant): Forecast = coroutineScope {
        var observationBody: String? = null
        val result: Forecast
        val body: String
        if (source == WeatherSource.FMI) {
            val observation = async {
                try { get(fmiUrl(place, now, observations = true)) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            }
            body = get(fmiUrl(place, now))
            result = Parsers.fmi(body, place, now)
            observationBody = observation.await()
        } else {
            body = get("https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", place.latitude.toString())
                .addQueryParameter("longitude", place.longitude.toString())
                .addQueryParameter("hourly", "temperature_2m,apparent_temperature,wind_speed_10m,wind_direction_10m,precipitation,precipitation_probability,weather_code,is_day")
                .addQueryParameter("daily", "temperature_2m_min,temperature_2m_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max,weather_code,sunrise,sunset,uv_index_max")
                .addQueryParameter("wind_speed_unit", "ms").addQueryParameter("timezone", "auto")
                .addQueryParameter("temperature_unit", "celsius").addQueryParameter("precipitation_unit", "mm")
                .addQueryParameter("timeformat", "unixtime").addQueryParameter("forecast_days", "7").build())
            result = Parsers.openMeteo(body, place, now)
        }
        val obs = observationBody?.let { runCatching { Parsers.observation(it, place) }.getOrNull() }
        save(source.name, JSONObject().put("fetched", now.toString()).put("body", body)
            .put("observation", observationBody).put("place", JSONObject()
                .put("lat", place.latitude).put("lon", place.longitude).put("name", place.name).put("at", place.locatedAt.toString())))
        result.copy(observation = obs)
    }

    override suspend fun prices(now: Instant): PriceData {
        val date = now.atZone(HELSINKI).toLocalDate()
        val body = get("https://dashboard.elering.ee/api/nps/price".toHttpUrl().newBuilder()
            .addQueryParameter("start", date.atStartOfDay(HELSINKI).toInstant().toString())
            .addQueryParameter("end", date.plusDays(2).atStartOfDay(HELSINKI).toInstant().minusSeconds(1).toString()).build())
        val parsed = Parsers.prices(body, now)
        save("prices", JSONObject().put("fetched", now.toString()).put("body", body))
        return parsed
    }

    private fun fmiUrl(place: Place, now: Instant, observations: Boolean = false): HttpUrl {
        // WFS rejects fractional seconds, even though they are valid ISO-8601 instants.
        val wholeSecond = now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        val start = if (observations) wholeSecond.minusSeconds(90 * 60) else now.atZone(HELSINKI).toLocalDate().atStartOfDay(HELSINKI).toInstant()
        // Include the final midnight for the last day's preceding-hour rainfall.
        val end = if (observations) wholeSecond else now.atZone(HELSINKI).toLocalDate().plusDays(7).atStartOfDay(HELSINKI).toInstant()
        val url = "https://opendata.fmi.fi/wfs".toHttpUrl().newBuilder()
            .addQueryParameter("service", "WFS").addQueryParameter("version", "2.0.0").addQueryParameter("request", "getFeature")
            .addQueryParameter("storedquery_id", if (observations) "fmi::observations::weather::simple" else "fmi::forecast::edited::weather::scandinavia::point::simple")
            .addQueryParameter("parameters", if (observations) "t2m,ws_10min,wd_10min" else "Temperature,FeelsLike,WindSpeedMS,WindDirection,Precipitation1h,SmartSymbol,Sunrise,Sunset")
            .addQueryParameter("starttime", start.toString()).addQueryParameter("endtime", end.toString())
            .addQueryParameter("timestep", if (observations) "10" else "60")
        if (observations) {
            val west = (place.longitude - 0.6).coerceAtLeast(-180.0)
            val east = (place.longitude + 0.6).coerceAtMost(180.0)
            val south = (place.latitude - 0.3).coerceAtLeast(-90.0)
            val north = (place.latitude + 0.3).coerceAtMost(90.0)
            url.addQueryParameter("bbox", "$west,$south,$east,$north")
        } else url.addQueryParameter("latlon", "${place.latitude},${place.longitude}")
        return url.build()
    }

    private suspend fun get(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(url).header("User-Agent", "SaaJaSahko/0.1 (personal Android app)").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                    val bytes = it.body.byteStream().use { stream -> stream.readNBytes(4_000_001) }
                    require(bytes.size <= 4_000_000) { "Vastaus liian suuri" }
                    bytes.toString(Charsets.UTF_8)
                } }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    private fun read(name: String): JSONObject? = runCatching {
        JSONObject(AtomicFile(File(cache, "$name.json")).openRead().bufferedReader().use { it.readText() })
    }.getOrNull()
    private suspend fun save(name: String, value: JSONObject) = withContext(Dispatchers.IO) {
        ensureActive()
        // A full disk must not hide an otherwise successful network response.
        runCatching {
            val file = AtomicFile(File(cache, "$name.json"))
            val stream = file.startWrite()
            try { stream.write(value.toString().toByteArray()); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
        }
        Unit
    }
}
