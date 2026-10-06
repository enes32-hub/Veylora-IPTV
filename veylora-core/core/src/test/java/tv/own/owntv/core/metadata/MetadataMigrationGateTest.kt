package tv.own.owntv.core.metadata

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MetadataMigrationGateTest {
    @Test fun `concurrent resolves clear stale mappings once before any resolve proceeds`() = runBlocking {
        val gate = MetadataMigrationGate()
        var clears = 0
        var savedVersion = 0
        val jobs = List(20) { async {
            gate.ensure {
                if (savedVersion < 2) {
                    delay(10)
                    clears++
                    yield()
                    savedVersion = 2
                }
            }
            assertEquals(2, savedVersion)
        } }
        jobs.awaitAll()
        assertEquals(1, clears)
    }
    @Test fun `failure is retried and cancellation is not swallowed`() = runBlocking {
        val gate = MetadataMigrationGate()
        try { gate.ensure { throw CancellationException() }; fail() } catch (_: CancellationException) { }
        var retries = 0
        gate.ensure { retries++ }
        gate.ensure { error("Already migrated") }
        assertEquals(1, retries)
    }
}
