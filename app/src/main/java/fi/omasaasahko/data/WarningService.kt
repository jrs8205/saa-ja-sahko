package fi.omasaasahko.data

import android.Manifest
import android.app.Notification
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
import fi.omasaasahko.of
import fi.omasaasahko.displayName
import fi.omasaasahko.labelRes
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface WarningFeed {
    val enabled: Boolean
    val intervalMinutes: Int
    fun allowed(): Boolean
    fun setEnabled(value: Boolean)
    fun interval(minutes: Int)
    fun testNotification()
    suspend fun cached(): WarningSnapshot?
    suspend fun refresh(): WarningSnapshot
    suspend fun reevaluate(): Boolean
}

class WarningService(context: Context, private val clock: Clock = Clock.systemUTC(),
                     private val evaluationScope: CoroutineScope = notificationScope) : WarningFeed {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("warnings", Context.MODE_PRIVATE)
    private val file = AtomicFile(File(this.context.filesDir, "warnings.json"))
    private val locationOwner = Any()
    override val enabled: Boolean get() = prefs.getBoolean("enabled", false)
    override val intervalMinutes: Int get() = prefs.getInt("interval", 30).takeIf { it in setOf(15, 30, 60) } ?: 30
    override fun interval(minutes: Int) {
        require(minutes in setOf(15, 30, 60))
        prefs.edit { putInt("interval", minutes) }
        if (enabled) setEnabled(true)
    }
    override fun allowed(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    val locationPending: Boolean get() = synchronized(placeLock) { pendingOwner != null }
    fun place(): Place? = synchronized(placeLock) { runCatching {
        // The notification store is authoritative. Import the older UI-only store only if absent.
        val value = prefs.getString("place", null) ?: context.getSharedPreferences("weather-place-name", Context.MODE_PRIVATE)
            .getString("place", null)?.also { legacy -> prefs.edit(commit = true) { putString("place", legacy) } }
        placeFromJson(JSONObject(requireNotNull(value)), PlaceOrigin.DEVICE).takeIf { it.origin == PlaceOrigin.DEVICE }
    }.getOrNull() }
    fun beginLocationUpdate() = synchronized(placeLock) {
        pendingOwner = locationOwner
    }
    fun endLocationUpdate() {
        val ended = synchronized(placeLock) {
            if (pendingOwner !== locationOwner) false else { pendingOwner = null; true }
        }
        // Outlive the closing Activity/ViewModel, but never outlive the process. No network request.
        if (ended) evaluationScope.launch {
            try { reevaluate() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* A later worker can retry the cached notification update. */ }
        }
    }
    fun savePlace(place: Place, ready: Boolean = true) {
        synchronized(placeLock) {
            require(place.origin == PlaceOrigin.DEVICE)
            if (pendingOwner != null && pendingOwner !== locationOwner) return
            val value = place.toJson().toString()
            if (prefs.getString("place", null) != value) prefs.edit(commit = true) { putString("place", value) }
            if (!ready) pendingOwner = locationOwner
        }
        if (ready) endLocationUpdate()
    }
    override suspend fun reevaluate(): Boolean = withContext(Dispatchers.IO) {
        val now = clock.instant()
        val snapshot = cached()?.takeIf { java.time.Duration.between(it.fetchedAt, now).seconds in 0..900 }
            ?: return@withContext false
        notifyNew(snapshot, now)
        true
    }
    override fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean("enabled", value) }
        if (value) {
            createChannel()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WarningWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter { it.notification.channelId == CHANNEL }.forEach { manager.cancel(it.tag,it.id) }
        }
    }
    override suspend fun cached(): WarningSnapshot? = withContext(Dispatchers.IO) { mutex.withLock {
        runCatching {
            val data = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            WarningParser.parse(data.getString("body"), Instant.parse(data.getString("fetched")), AppLanguage.of(context))
        }.getOrNull()
    } }
    override suspend fun refresh(): WarningSnapshot = mutex.withLock {
        val body = download()
        val now = Instant.now()
        val snapshot = withContext(Dispatchers.IO) { WarningParser.parse(body, now, AppLanguage.of(context)) }
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
    internal fun notifyNew(snapshot: WarningSnapshot, now: Instant) { synchronized(placeLock) {
        if (locationPending || !enabled || !allowed() || !DeviceLocation(context).permitted() || snapshot.partial) return
        val place = place() ?: return
        createChannel()
        val language = AppLanguage.of(context)
        val manager = NotificationManagerCompat.from(context)
        val local = snapshot.local(place, now)
        val active = local.associateBy { it.fingerprint(place) }
        val existing = context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.notification.channelId == CHANNEL }.associateBy { it.tag }
        val seen = runCatching { JSONObject(prefs.getString("seen", "{}")!!) }.getOrDefault(JSONObject())
        val posted = prefs.getStringSet("posted", emptySet()).orEmpty().toSet()
        // A full snapshot replaces the preceding one. Removed/cancelled/expired alerts disappear.
        (posted - active.keys).forEach { manager.cancel(it, 1) }
        val kept = JSONObject()
        seen.keys().forEach { key -> if (seen.optLong(key) > now.epochSecond) kept.put(key, seen.getLong(key)) }
        active.forEach { (key, warning) ->
            if (!enabled || place() != place || !DeviceLocation(context).permitted()) return
            val title = "${context.getString(warning.level.labelRes())}: ${warning.event}"
            val text = "${place.displayName(context)} · ${updatedLabel(warning.onset, language)}–${updatedLabel(warning.expires, language)}\n${warning.description}"
            val previous = existing[key]?.notification
            if (!kept.has(key) || (previous != null && previous.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() != text)) {
                manager.notify(key, 1, notification(title, text).setOnlyAlertOnce(kept.has(key))
                    .setTimeoutAfter((warning.expires.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(1)).build())
                kept.put(key, warning.expires.epochSecond)
            }
        }
        prefs.edit(commit = true) { putString("seen", kept.toString()); putStringSet("posted", active.keys) }
    } }
    @android.annotation.SuppressLint("MissingPermission")
    override fun testNotification() {
        if (!allowed()) return
        createChannel()
        NotificationManagerCompat.from(context).notify("test", 2,
            notification(context.getString(R.string.warning_test_title), context.getString(R.string.warning_test_text)).setTimeoutAfter(60_000).build())
    }
    private fun notification(title: String, text: String): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).putExtra("showWarnings", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_warning_notification)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setLocalOnly(false)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
    }
    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.warning_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.warning_channel_description)
            })
    }
    companion object {
        const val FEED = "https://alerts.fmi.fi/cap/feed/atom_fi-FI.xml"
        const val CHANNEL = "weather-warnings"
        const val WORK = "weather-warning-check"
        private var pendingOwner: Any? = null
        private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val placeLock = Any()
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
