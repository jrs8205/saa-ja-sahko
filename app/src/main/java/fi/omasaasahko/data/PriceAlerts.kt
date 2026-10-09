package fi.omasaasahko.data

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import fi.omasaasahko.MainActivity
import fi.omasaasahko.R
import fi.omasaasahko.of
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.UUID

data class PriceAlertState(val enabled: Boolean = false, val allowed: Boolean = false, val testPending: Boolean = false)

/** Background requests may start only during 14:00 <= time < 16:00 in Finland. */
internal fun priceBackgroundCheckAllowed(now: Instant): Boolean = now.atZone(HELSINKI).hour in 14..15

internal fun nextPriceCheck(now: Instant, needed: Boolean, afterAttempt: Boolean = false): Instant {
    val today = now.atZone(HELSINKI)
    if (today.hour < 14) return today.toLocalDate().atTime(14, 0).atZone(HELSINKI).toInstant()
    val candidate = if (afterAttempt) now.plusSeconds(15 * 60) else now
    val local = candidate.atZone(HELSINKI)
    if (needed && local.toLocalDate() == now.atZone(HELSINKI).toLocalDate()) {
        if (local.hour < 14) return local.toLocalDate().atTime(14, 0).atZone(HELSINKI).toInstant()
        if (local.hour < 16) return candidate
    }
    return now.atZone(HELSINKI).toLocalDate().plusDays(1).atTime(14, 0).atZone(HELSINKI).toInstant()
}

internal fun priceWork(next: Instant, id: UUID? = null): PeriodicWorkRequest =
    PeriodicWorkRequestBuilder<PriceAlertWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setNextScheduleTimeOverride(next.toEpochMilli())
        .apply { if (id != null) setId(id) }.build()

/** Notification only after every quarter of the Finnish next day is available (92/96/100). */
fun tomorrowPriceMessage(data: PriceData, now: Instant, includeVat: Boolean, language: AppLanguage): String? {
    val tomorrow = now.atZone(HELSINKI).toLocalDate().plusDays(1)
    val hours = Prices.slots(data.quarters,tomorrow,Resolution.HOUR,includeVat)
    val average = Prices.average(hours) ?: return null
    val cheapest = hours.minBy { it.centsPerKwh!! }; val highest = hours.maxBy { it.centsPerKwh!! }
    fun interval(slot: PriceSlot) = "${clockLabel(slot.start, language)}–${clockLabel(slot.end, language)}"
    return "${language.weekday(tomorrow)} ${language.shortDate(tomorrow)} · ${if (includeVat) "ALV 25,5 %" else "ALV 0 %"}\n" +
        "Keskihinta ${Prices.format(average, language)} snt/kWh\n" +
        "Halvin tunti ${interval(cheapest)}: ${Prices.format(cheapest.centsPerKwh, language)} snt/kWh\n" +
        "Kallein tunti ${interval(highest)}: ${Prices.format(highest.centsPerKwh, language)} snt/kWh"
}

class PriceAlerts(context: Context, private val scheduleClock: Clock = Clock.systemUTC()) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("price-alerts",Context.MODE_PRIVATE)
    val enabled: Boolean get() = prefs.getBoolean("enabled",false)
    fun allowed() = ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    private fun tomorrow(now: Instant) = now.atZone(HELSINKI).toLocalDate().plusDays(1).toString()
    fun needsCheck(now: Instant) = enabled && allowed() && prefs.getString("lastDay",null) != tomorrow(now)
    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean("enabled",value) }
        if (value) {
            channel()
            schedule(ExistingPeriodicWorkPolicy.UPDATE)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
            NotificationManagerCompat.from(context).cancel("tomorrow-prices",3)
            NotificationManagerCompat.from(context).cancel("price-test",4)
        }
    }
    fun ensureScheduled() { if (enabled) { channel(); schedule(ExistingPeriodicWorkPolicy.KEEP) } }
    private fun schedule(policy: ExistingPeriodicWorkPolicy) {
        val now = scheduleClock.instant()
        // Foreground already checks prices. Give it time to finish before the next worker.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, policy,
            priceWork(nextPriceCheck(now, needsCheck(now), afterAttempt = true)))
    }
    suspend fun check(clock: Clock = Clock.systemUTC(),
                      loadPrices: suspend (Instant) -> PriceData = { Repository(context, cachePrices=false).prices(it) }) = check(false, clock, loadPrices)
    private suspend fun check(background: Boolean, clock: Clock, loadPrices: suspend (Instant) -> PriceData) = checkLock.withLock {
        val now = clock.instant()
        if (background && !priceBackgroundCheckAllowed(now)) return@withLock
        if (!needsCheck(now)) return@withLock
        // No cache write here: foreground Repository owns its price-cache file.
        val prices = loadPrices(now)
        consider(prices,clock.instant())
    }
    suspend fun checkBackground(clock: Clock = Clock.systemUTC(),
                                loadPrices: suspend (Instant) -> PriceData = { Repository(context, cachePrices=false).prices(it) }) {
        // A delayed worker must not turn an afternoon request into a nighttime request.
        check(true, clock, loadPrices)
    }
    @android.annotation.SuppressLint("MissingPermission")
    fun consider(prices: PriceData, now: Instant) = synchronized(notificationLock) {
        if (!needsCheck(now)) return@synchronized
        val vat = context.getSharedPreferences("preferences",Context.MODE_PRIVATE).getBoolean("includeVat",true)
        val text = tomorrowPriceMessage(prices,now,vat,AppLanguage.of(context)) ?: return@synchronized
        if (!needsCheck(now)) return@synchronized
        val expiry = now.atZone(HELSINKI).toLocalDate().plusDays(2).atStartOfDay(HELSINKI).toInstant()
        val notification = notification("Huomisen sähköhinnat julkaistu", text, tomorrow(now))
            .setTimeoutAfter(expiry.toEpochMilli()-now.toEpochMilli()).build()
        NotificationManagerCompat.from(context).notify("tomorrow-prices",3,notification)
        prefs.edit(commit=true) { putString("lastDay",tomorrow(now)) }
    }
    @android.annotation.SuppressLint("MissingPermission")
    fun testNotification() {
        if (!allowed()) return
        NotificationManagerCompat.from(context).notify("price-test",4,
            notification("Sähköhintojen testi", "Tämä on testi, ei hintatieto. Jos näet tämän kellossa, sähköhintakanavan välitys toimii.")
                .setTimeoutAfter(60_000).build())
    }
    private fun notification(title: String, text: String, day: String? = null): NotificationCompat.Builder {
        channel()
        val intent = Intent(context,MainActivity::class.java).putExtra("showPrices",true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (day != null) intent.putExtra("priceDate",day)
        // The test must not overwrite a real notification's delivery day or consume lastDay.
        val pending = PendingIntent.getActivity(context,if (day == null) 12 else 11,intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_electricity_notification)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending).setAutoCancel(true).setLocalOnly(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    }
    fun channel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"Huomisen sähköhinnat",NotificationManager.IMPORTANCE_DEFAULT))
    }
    companion object {
        const val CHANNEL = "tomorrow-electricity"
        const val WORK = "tomorrow-electricity-check"
        private val notificationLock = Any()
        private val checkLock = Mutex()
    }
}
class PriceAlertWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val service = PriceAlerts(applicationContext)
        if (!service.enabled) return Result.success()
        try { service.checkBackground() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* The next request follows the same afternoon schedule. */ }
        return try {
            val now = Instant.now()
            val request = priceWork(nextPriceCheck(now, service.needsCheck(now), afterAttempt = true), id)
            // Updating this id cannot resurrect a worker cancelled by the user's switch.
            runInterruptible(Dispatchers.IO) { WorkManager.getInstance(applicationContext).updateWork(request).get() }
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
}
class PriceAlertViewModel(application: Application) : AndroidViewModel(application) {
    private val service = PriceAlerts(application)
    private val mutable = MutableStateFlow(PriceAlertState(service.enabled,service.allowed()))
    val state = mutable.asStateFlow()
    private val notificationTest = DelayedNotificationTest(viewModelScope,
        { pending -> mutable.update { it.copy(testPending = pending) } },
        { if (service.enabled) service.testNotification() })
    private var latestPrices: PriceData? = null
    fun test() = notificationTest.start()
    fun resume() { mutable.update { it.copy(enabled=service.enabled,allowed=service.allowed()) }; service.ensureScheduled() }
    fun enable(value: Boolean) {
        if (!value) notificationTest.cancel()
        service.setEnabled(value)
        mutable.update { it.copy(enabled=service.enabled,allowed=service.allowed()) }
        if (value) viewModelScope.launch { try {
            latestPrices?.let { service.consider(it, Instant.now()) }
            service.check()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Scheduled worker retries. */ } }
    }
    fun prices(data: PriceData?) { latestPrices = data; if (data != null) viewModelScope.launch(Dispatchers.IO) {
        try { service.consider(data,Instant.now()) } catch (_: SecurityException) { /* Permission changed while posting; do not consume the date. */ }
    } }
}
