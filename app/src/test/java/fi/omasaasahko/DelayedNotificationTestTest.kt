package fi.omasaasahko

import fi.omasaasahko.data.DelayedNotificationTest
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DelayedNotificationTestTest {
    @Test fun `test waits for screen locking and repeated taps do not duplicate it`() = runTest {
        var sent=0; var pending=false
        val sender=DelayedNotificationTest(this,{ pending=it },{ sent++ })
        sender.start(); sender.start(); runCurrent()
        assertTrue(pending)
        advanceTimeBy(9_999); runCurrent(); assertEquals(0,sent)
        advanceTimeBy(1); runCurrent(); assertEquals(1,sent); assertFalse(pending)
    }
    @Test fun `disabling during countdown cancels and permission revocation clears pending state`() = runTest {
        var pending=false; var sent=0
        val sender=DelayedNotificationTest(this,{ pending=it },{ sent++; throw SecurityException() })
        sender.start();runCurrent();sender.cancel();runCurrent()
        advanceTimeBy(10_000);runCurrent();assertEquals(0,sent);assertFalse(pending)
        sender.start();runCurrent();advanceTimeBy(10_000);runCurrent()
        assertEquals(1,sent);assertFalse(pending)
    }
}
