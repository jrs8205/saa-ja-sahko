package fi.omasaasahko

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fi.omasaasahko.data.WarningService
import fi.omasaasahko.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class WarningsState(val snapshot: WarningSnapshot? = null, val loading: Boolean = false, val error: String? = null,
    val enabled: Boolean = false, val allowed: Boolean = false, val intervalMinutes: Int = 30)

class WarningsViewModel(application: Application) : AndroidViewModel(application) {
    private val service = WarningService(application)
    private val mutable = MutableStateFlow(WarningsState(enabled = service.enabled, allowed = service.allowed(), intervalMinutes = service.intervalMinutes))
    val state = mutable.asStateFlow()
    private var loop: Job? = null
    private var fetch: Job? = null
    init { viewModelScope.launch { val cache = service.cached(); mutable.update { it.copy(snapshot = it.snapshot ?: cache) } } }
    fun place(place: Place?) {
        if (place == null) return
        val old = service.place()
        service.savePlace(place)
        if (old == null || old.latitude != place.latitude || old.longitude != place.longitude) refresh()
    }
    fun start() {
        mutable.update { it.copy(enabled = service.enabled, allowed = service.allowed()) }
        if (service.enabled) service.setEnabled(true)
        if (loop?.isActive == true) return
        loop = viewModelScope.launch { while (isActive) { refresh(); delay(15 * 60_000L) } }
    }
    fun stop() { loop?.cancel(); loop = null; fetch?.cancel(); mutable.update { it.copy(loading = false) } }
    fun enable(value: Boolean) { service.setEnabled(value); mutable.update { it.copy(enabled = service.enabled, allowed = service.allowed()) }; if (value) refresh() }
    fun test() = service.testNotification()
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
