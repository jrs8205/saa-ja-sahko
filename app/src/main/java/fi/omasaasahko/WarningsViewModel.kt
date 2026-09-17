package fi.omasaasahko

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fi.omasaasahko.data.WarningService
import fi.omasaasahko.data.DelayedNotificationTest
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class WarningsState(val snapshot: WarningSnapshot? = null, val loading: Boolean = false, val error: String? = null,
    val enabled: Boolean = false, val allowed: Boolean = false, val intervalMinutes: Int = 30, val testPending: Boolean = false)

class WarningsViewModel(application: Application) : AndroidViewModel(application) {
    private val service = WarningService(application)
    private val mutable = MutableStateFlow(WarningsState(enabled = service.enabled, allowed = service.allowed(), intervalMinutes = service.intervalMinutes))
    val state = mutable.asStateFlow()
    private val notificationTest = DelayedNotificationTest(viewModelScope,
        { pending -> mutable.update { it.copy(testPending = pending) } },
        { if (service.enabled) service.testNotification() })
    private var loop: Job? = null
    private var fetch: Job? = null
    init { viewModelScope.launch { val cache = service.cached(); mutable.update { it.copy(snapshot = it.snapshot ?: cache) } } }
    fun place(place: Place?) {
        if (place == null) return
        // AppViewModel persists the device position synchronously, independently of the browsed place.
        viewModelScope.launch { service.reevaluate() }
        refresh()
    }
    fun start() {
        mutable.update { it.copy(enabled = service.enabled, allowed = service.allowed()) }
        if (service.enabled) service.setEnabled(true)
        if (loop?.isActive == true) return
        loop = viewModelScope.launch { while (isActive) { refresh(); delay(15 * 60_000L) } }
    }
    fun stop() { loop?.cancel(); loop = null; fetch?.cancel(); mutable.update { it.copy(loading = false) } }
    fun enable(value: Boolean) { if (!value) notificationTest.cancel(); service.setEnabled(value); mutable.update { it.copy(enabled = service.enabled, allowed = service.allowed()) }; if (value) refresh() }
    fun test() = notificationTest.start()
    fun interval(minutes: Int) { service.interval(minutes); mutable.update { it.copy(intervalMinutes = minutes) } }
    fun refresh() {
        if (fetch?.isActive == true) return
        fetch = viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try { val data = service.refresh(); ensureActive(); mutable.update { it.copy(snapshot = data, loading = false) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(loading = false, error = "Varoitusten päivitys epäonnistui. Aiemmat tiedot voivat olla vanhentuneita.") } }
        }
    }
}
