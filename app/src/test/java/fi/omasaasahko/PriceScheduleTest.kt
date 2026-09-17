package fi.omasaasahko

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.impl.WorkManagerImpl
import fi.omasaasahko.data.*
import fi.omasaasahko.domain.HELSINKI
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.Clock
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PriceScheduleTest {
    @Suppress("DEPRECATION")
    @Test fun `persisted work skips overnight and updating a cancelled id cannot restart it`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val previous = WorkManagerImpl.getInstance()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        try {
            val manager = WorkManager.getInstance(app)
            val next = Instant.now().atZone(HELSINKI).toLocalDate().plusDays(1).atTime(14, 0).atZone(HELSINKI).toInstant()
            val request = priceWork(next)
            manager.enqueueUniquePeriodicWork(PriceAlerts.WORK, ExistingPeriodicWorkPolicy.UPDATE, request).result.get(5, TimeUnit.SECONDS)
            assertEquals(next.toEpochMilli(), manager.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)!!.nextScheduleTimeMillis)
            val later = next.plusSeconds(15 * 60)
            manager.updateWork(priceWork(later, request.id)).get(5, TimeUnit.SECONDS)
            assertEquals(later.toEpochMilli(), manager.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)!!.nextScheduleTimeMillis)
            manager.cancelUniqueWork(PriceAlerts.WORK).result.get(5, TimeUnit.SECONDS)
            assertEquals(WorkManager.UpdateResult.NOT_APPLIED, manager.updateWork(priceWork(later, request.id)).get(5, TimeUnit.SECONDS))
            assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)!!.state)
        } finally {
            try { WorkManagerTestInitHelper.closeWorkDatabase() }
            finally { WorkManagerImpl.setDelegate(previous) }
        }
        assertSame(previous, WorkManagerImpl.getInstance())
    }

    @Suppress("DEPRECATION")
    @Test fun `foreground scheduling defers the worker and resume preserves its existing time`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val previous = WorkManagerImpl.getInstance()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        try {
            val at = Instant.now().atZone(HELSINKI).toLocalDate().plusDays(1).atTime(14, 0).atZone(HELSINKI).toInstant()
            org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            val service = PriceAlerts(app, Clock.fixed(at, ZoneOffset.UTC))
            val manager = WorkManager.getInstance(app)
            service.setEnabled(true)
            val work = manager.getWorkInfosForUniqueWork(PriceAlerts.WORK).get(5, TimeUnit.SECONDS).single()
            assertEquals(at.plusSeconds(15 * 60).toEpochMilli(), work.nextScheduleTimeMillis)
            repeat(3) { service.ensureScheduled() }
            val kept = manager.getWorkInfosForUniqueWork(PriceAlerts.WORK).get(5, TimeUnit.SECONDS).single()
            assertEquals(work.id, kept.id)
            assertEquals(work.nextScheduleTimeMillis, kept.nextScheduleTimeMillis)
            manager.cancelUniqueWork(PriceAlerts.WORK).result.get(5, TimeUnit.SECONDS)
        } finally {
            try { WorkManagerTestInitHelper.closeWorkDatabase() }
            finally { WorkManagerImpl.setDelegate(previous) }
        }
        assertSame(previous, WorkManagerImpl.getInstance())
    }
}
