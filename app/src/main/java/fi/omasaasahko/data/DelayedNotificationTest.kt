package fi.omasaasahko.data

import kotlinx.coroutines.*

/** Gives the user time to lock the phone: Pebble can filter notifications while its screen is on. */
internal class DelayedNotificationTest(
    private val scope: CoroutineScope,
    private val pending: (Boolean) -> Unit,
    private val send: () -> Unit,
) {
    private var job: Job? = null
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            pending(true)
            try {
                delay(10_000)
                send()
            } catch (_: SecurityException) {
                // Android permission may have been revoked during the countdown.
            } finally { pending(false) }
        }
    }
    fun cancel() { job?.cancel() }
}
