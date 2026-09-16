package fi.omasaasahko.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.AtomicFile
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.*
import fi.omasaasahko.MainActivity
import fi.omasaasahko.R
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WarningService(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("warnings", Context.MODE_PRIVATE)
    private val file = AtomicFile(File(this.context.filesDir, "warnings.json"))
    val enabled: Boolean get() = prefs.getBoolean("enabled", false)
    val intervalMinutes: Int get() = prefs.getInt("interval", 30).takeIf { it in setOf(15, 30, 60) } ?: 30
    fun interval(minutes: Int) {
        require(minutes in setOf(15, 30, 60))
        prefs.edit { putInt("interval", minutes) }
        if (enabled) setEnabled(true)
    }
    fun allowed(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    fun place(): Place? = runCatching {
        val p = JSONObject(requireNotNull(prefs.getString("place", null)))
        Place(p.getDouble("lat"), p.getDouble("lon"), p.getString("name"), Instant.parse(p.getString("at")))
    }.getOrNull()
    fun savePlace(place: Place) {
        prefs.edit { putString("place", JSONObject().put("lat", place.latitude).put("lon", place.longitude)
            .put("name", place.name).put("at", place.locatedAt.toString()).toString()) }
    }
    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean("enabled", value) }
        if (value) {
            createChannel()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WarningWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
            NotificationManagerCompat.from(context).cancelAll()
        }
    }
    suspend fun cached(): WarningSnapshot? = withContext(Dispatchers.IO) { mutex.withLock {
        runCatching {
            val data = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            WarningParser.parse(data.getString("body"), Instant.parse(data.getString("fetched")))
        }.getOrNull()
    } }
    suspend fun refresh(): WarningSnapshot = mutex.withLock {
        val body = download()
        val now = Instant.now()
        val snapshot = withContext(Dispatchers.IO) { WarningParser.parse(body, now) }
        withContext(Dispatchers.IO) {
            // Reject an older server snapshot, instead of resurrecting a cancelled warning.
            val previous = runCatching { JSONObject(file.openRead().bufferedReader().use { it.readText() }) }.getOrNull()
            val previousPublished = previous?.optString("published")?.takeIf { it.isNotBlank() }?.let(Instant::parse)
            require(previousPublished == null || snapshot.publishedAt >= previousPublished) { "Vanha varoitussyöte" }
            require(snapshot.publishedAt <= now.plusSeconds(300)) { "Virheellinen julkaisuhetki" }
            val stream = file.startWrite()
            try {
                stream.write(JSONObject().put("fetched", now.toString()).put("published", snapshot.publishedAt.toString()).put("body", body).toString().toByteArray())
                file.finishWrite(stream)
            } catch (e: Exception) { file.failWrite(stream); throw e }
            currentCoroutineContext().ensureActive()
            notifyNew(snapshot, now)
        }
        snapshot
    }
    private suspend fun download(): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(FEED).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    check(it.isSuccessful) { "HTTP ${it.code}" }
                    val bytes = it.body.byteStream().readNBytes(16_000_001)
                    require(bytes.size <= 16_000_000)
                    bytes.toString(Charsets.UTF_8)
                } }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
    @android.annotation.SuppressLint("MissingPermission")
    internal fun notifyNew(snapshot: WarningSnapshot, now: Instant) {
        if (!enabled || !allowed() || !DeviceLocation(context).permitted() || snapshot.partial) return
        val place = place() ?: return
        createChannel()
        val manager = NotificationManagerCompat.from(context)
        val local = snapshot.local(place, now)
        val active = local.associateBy { it.fingerprint(place) }
        val seen = runCatching { JSONObject(prefs.getString("seen", "{}")!!) }.getOrDefault(JSONObject())
        val posted = prefs.getStringSet("posted", emptySet()).orEmpty().toSet()
        // A full snapshot replaces the preceding one. Removed/cancelled/expired alerts disappear.
        (posted - active.keys).forEach { manager.cancel(it, 1) }
        val kept = JSONObject()
        seen.keys().forEach { key -> if (seen.optLong(key) > now.epochSecond) kept.put(key, seen.getLong(key)) }
        active.forEach { (key, warning) ->
            if (!enabled || place() != place || !DeviceLocation(context).permitted()) return
            if (!kept.has(key)) {
                val title = "${warning.level.label}: ${warning.event}"
                val text = "${place.name} · ${updatedLabel(warning.onset)}–${updatedLabel(warning.expires)}\n${warning.description}"
                manager.notify(key, 1, notification(title, text).setTimeoutAfter((warning.expires.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(1)).build())
                kept.put(key, warning.expires.epochSecond)
            }
        }
        prefs.edit(commit = true) { putString("seen", kept.toString()); putStringSet("posted", active.keys) }
    }
    @android.annotation.SuppressLint("MissingPermission")
    fun testNotification() {
        if (!allowed()) return
        createChannel()
        NotificationManagerCompat.from(context).notify("test", 2,
            notification("Säävaroitusten testi", "Ilmoitukset toimivat. Tämä on testi, ei FMI:n säävaroitus.").setTimeoutAfter(60_000).build())
    }
    private fun notification(title: String, text: String): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).putExtra("showWarnings", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_warning_notification)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
    }
    private fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Oman alueen säävaroitukset", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "FMI:n keltaiset, oranssit ja punaiset varoitukset sekä tulevat päivät"
            })
    }
    companion object {
        const val FEED = "https://alerts.fmi.fi/cap/feed/atom_fi-FI.xml"
        const val CHANNEL = "weather-warnings"
        const val WORK = "weather-warning-check"
        private val mutex = Mutex()
        private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()
    }
}

class WarningWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val service = WarningService(applicationContext)
        if (!service.enabled || !service.allowed() || service.place() == null || !DeviceLocation(applicationContext).permitted()) return Result.success()
        return try { service.refresh(); Result.success() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
}
