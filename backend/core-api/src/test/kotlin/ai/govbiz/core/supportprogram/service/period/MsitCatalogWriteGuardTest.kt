package ai.govbiz.core.supportprogram.service.period

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MsitCatalogWriteGuardTest {
    @Test
    fun snapshotPublicationWaitsUntilAnExtractionWriteFinishesInsteadOfInterleaving() {
        val guard = MsitCatalogWriteGuard()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val workerHoldsLock = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val worker = executor.submit {
                guard.withLock {
                    events += "worker-start"
                    workerHoldsLock.countDown()
                    releaseWorker.await(5, TimeUnit.SECONDS)
                    events += "worker-end"
                }
            }
            assertTrue(workerHoldsLock.await(5, TimeUnit.SECONDS))
            val publisher = executor.submit { guard.withLock { events += "publish" } }

            Thread.sleep(200)
            assertFalse(publisher.isDone, "publication must not enter while the worker holds the guard")
            releaseWorker.countDown()
            worker.get(5, TimeUnit.SECONDS)
            publisher.get(5, TimeUnit.SECONDS)

            assertEquals(listOf("worker-start", "worker-end", "publish"), events.toList())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun releasesTheGuardWhenAWriteFailsSoTheNextSyncCanPublish() {
        val guard = MsitCatalogWriteGuard()
        assertThrows(IllegalStateException::class.java) { guard.withLock { throw IllegalStateException("deadlock loser") } }
        assertEquals("published", guard.withLock { "published" })
    }
}
