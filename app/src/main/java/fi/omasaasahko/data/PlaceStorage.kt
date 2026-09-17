package fi.omasaasahko.data

import android.content.Context
import androidx.core.content.edit
import fi.omasaasahko.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal fun Place.toJson(): JSONObject = JSONObject().put("lat", latitude).put("lon", longitude)
    .put("name", name).put("at", locatedAt.toString()).put("nearbyName", nearbyName)
    .put("accuracy", accuracyMeters).put("nearbyDistance", nearbyDistanceMeters)
    .put("origin", origin.name).put("nameResolved", nameResolved)
    .put("nameAnchor", nameAnchor?.let { JSONObject().put("lat", it.latitude).put("lon", it.longitude).put("accuracy", it.accuracyMeters) })

internal fun placeFromJson(p: JSONObject, defaultOrigin: PlaceOrigin = PlaceOrigin.UNKNOWN) = Place(p.getDouble("lat"), p.getDouble("lon"), p.getString("name"),
    Instant.parse(p.getString("at")), p.optString("nearbyName").takeIf(String::isNotBlank),
    p.optDouble("accuracy").takeIf { it.isFinite() && it >= 0 }?.toFloat(),
    p.optDouble("nearbyDistance").takeIf { it.isFinite() && it >= 0 },
    runCatching { PlaceOrigin.valueOf(p.getString("origin")) }.getOrDefault(defaultOrigin),
    // Migration of 0.2.2 caches only. Runtime behavior uses the explicit flag.
    p.optBoolean("nameResolved", p.getString("name") != CURRENT_LOCATION_NAME),
    p.optJSONObject("nameAnchor")?.let { runCatching { NameAnchor(it.getDouble("lat"), it.getDouble("lon"), it.getDouble("accuracy").toFloat()) }.getOrNull() })

interface FavoritePlaces {
    fun load(): List<PlaceResult>
    fun save(places: List<PlaceResult>)
}

class PlaceStorage(context: Context) : FavoritePlaces {
    private val prefs = context.applicationContext.getSharedPreferences("favorite-places", Context.MODE_PRIVATE)
    override fun load(): List<PlaceResult> = runCatching {
        val array = JSONArray(prefs.getString("places", "[]"))
        (0 until array.length()).mapNotNull { i -> runCatching {
            val p = array.getJSONObject(i)
            PlaceResult(p.getString("id"), p.getString("name"), p.getString("municipality"),
                p.getDouble("lat"), p.getDouble("lon"), p.optString("kind")).also { it.place(Instant.EPOCH) }
        }.getOrNull() }.distinctBy { it.id }.take(50)
    }.getOrDefault(emptyList())
    override fun save(places: List<PlaceResult>) {
        val array = JSONArray()
        places.distinctBy { it.id }.take(50).forEach { p -> array.put(JSONObject().put("id", p.id).put("name", p.name)
            .put("municipality", p.municipality).put("lat", p.latitude).put("lon", p.longitude).put("kind", p.kind)) }
        prefs.edit { putString("places", array.toString()) }
    }
}
