package fi.omasaasahko

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fi.omasaasahko.data.WarningService
import fi.omasaasahko.data.WarningFeed
import fi.omasaasahko.data.DelayedNotificationTest
import fi.omasaasahko.domain.*
import fi.omasaasahko.of
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class WarningsState(val snapshot: WarningSnapshot? = null, val loading: Boolean = false, val error: AppMessage? = null,
    val enabled: Boolean = false, val allowed: Boolean = false, val intervalMinutes: Int = 30, val testPending: Boolean = false)

class WarningsViewModel(application: Application, private val feed: WarningFeed) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, WarningService(application))
    private val mutable = MutableStateFlow(WarningsState(enabled = feed.enabled, allowed = feed.allowed(), intervalMinutes = feed.intervalMinutes))
    val state = mutable.asStateFlow()
    private val notificationTest = DelayedNotificationTest(viewModelScope,
        { pending -> mutable.update { it.copy(testPending = pending) } },
        { if (feed.enabled) feed.testNotification() })
    private var loop: Job? = null
    private var fetch: Job? = null
    private var refreshError: AppMessage? = null
    private var evaluationError: AppMessage? = null
    private var cachedLanguage = AppLanguage.of(application)
    init { viewModelScope.launch { val cache = feed.cached(); mutable.update { it.copy(snapshot = it.snapshot ?: cache) } } }
    fun place(place: Place?) {
        if (place == null) return
        // AppViewModel persists the device position synchronously, independently of the browsed place.
        viewModelScope.launch {
            try {
                val fresh = feed.reevaluate()
                evaluationError = null
                mutable.update { it.copy(error = refreshError) }
                if (!fresh) refresh()
            }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                evaluationError = AppMessage.WARNINGS_TARGETING_FAILED
                mutable.update { it.copy(error = refreshError ?: evaluationError) }
            }
        }
    }
    fun start() {
        mutable.update { it.copy(enabled = feed.enabled, allowed = feed.allowed()) }
        // The ViewModel outlives an app language change; the cached feed carries every language.
        val language = AppLanguage.of(getApplication())
        if (language != cachedLanguage) {
            cachedLanguage = language
            viewModelScope.launch { feed.cached()?.let { cache -> mutable.update { it.copy(snapshot = cache) } } }
        }
        if (feed.enabled) feed.setEnabled(true)
        if (loop?.isActive == true) return
        loop = viewModelScope.launch { while (isActive) { refresh(); delay(15 * 60_000L) } }
    }
    fun stop() { loop?.cancel(); loop = null; fetch?.cancel(); mutable.update { it.copy(loading = false) } }
    fun enable(value: Boolean) { if (!value) notificationTest.cancel(); feed.setEnabled(value); mutable.update { it.copy(enabled = feed.enabled, allowed = feed.allowed()) }; if (value) refresh() }
    fun test() = notificationTest.start()
    fun interval(minutes: Int) { feed.interval(minutes); mutable.update { it.copy(intervalMinutes = minutes) } }
    fun refresh() {
        if (fetch?.isActive == true) return
        fetch = viewModelScope.launch {
            refreshError = null
            mutable.update { it.copy(loading = true, error = evaluationError) }
            try { val data = feed.refresh(); ensureActive(); evaluationError = null; mutable.update { it.copy(snapshot = data, loading = false, error = null) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                refreshError = AppMessage.WARNINGS_REFRESH_FAILED
                mutable.update { it.copy(loading = false, error = refreshError) }
            }
        }
    }
}
