package tv.own.owntv.core.metadata

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class DirectMetadataPacerTest {
    @Test fun `one worker never gets forty one permits in a second`() = runBlocking {
        val pacer = DirectMetadataPacer()
        val times = (1..42).map { pacer.acquire(3); System.nanoTime() }
        assertTrue(times[40] - times[0] >= 1_000_000_000L)
    }
    @Test fun `all worker lanes share one aggregate limit`() = runBlocking {
        val pacer = DirectMetadataPacer()
        val times = Collections.synchronizedList(mutableListOf<Long>())
        coroutineScope { (0..400).map { i -> async { pacer.acquire(i % 10); times.add(System.nanoTime()) } }.awaitAll() }
        val sorted = times.sorted()
        assertTrue(sorted.last() - sorted.first() >= 1_000_000_000L)
    }
    @Test fun `new cooldown extends already waiting callers across all lanes`() = runBlocking {
        val pacer = DirectMetadataPacer()
        pacer.backOff(1)
        val times = Collections.synchronizedList(mutableListOf<Long>())
        val jobs = (0..9).map { w -> launch { pacer.acquire(w); times.add(System.nanoTime()) } }
        delay(100)
        val extended = System.nanoTime()
        pacer.backOff(1)
        jobs.joinAll()
        assertEquals(10, times.size)
        assertTrue(times.min() - extended >= 990_000_000L)
    }
    @Test fun `parallel workers do not share the old 35 request bottleneck`() = runBlocking {
        val pacer = DirectMetadataPacer()
        val times = Collections.synchronizedList(mutableListOf<Long>())
        coroutineScope { (1..40).map { async(Dispatchers.Default) { pacer.acquire(); times.add(System.nanoTime()/1_000_000) } }.awaitAll() }
        val sorted = times.sorted()
        assertTrue("Forty requests should be distributed over ten workers", sorted.last() - sorted.first() < 900)
    }
    @Test fun `server cooldown delays following requests`() = runBlocking {
        val pacer = DirectMetadataPacer()
        pacer.backOff(1)
        val started = System.nanoTime()
        pacer.acquire()
        assertTrue((System.nanoTime()-started)/1_000_000 >= 990)
    }
    @Test fun `cancellation removes a waiting request`() = runBlocking {
        val pacer = DirectMetadataPacer()
        pacer.backOff(1)
        var dispatched = false
        val job = launch { pacer.acquire(); dispatched = true }
        delay(20)
        job.cancelAndJoin()
        assertFalse(dispatched)
    }
}
