package tv.own.owntv.core.metadata

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MetadataWorkersTest {
    @Test fun `each item processed once with at most ten active worker identities`() = runBlocking {
        val seen = mutableListOf<Int>()
        val lanes = mutableSetOf<Int>()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        forEachMetadataItem((0..99).toList()) { item ->
            val count = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, count) }
            lanes.add(currentCoroutineContext()[MetadataWorker]!!.id)
            delay(2)
            seen.add(item)
            active.decrementAndGet()
        }
        assertEquals((0..99).toList(), seen.sorted())
        assertEquals((0..9).toSet(), lanes)
        assertEquals(10, peak.get())
    }
    @Test fun `cancelling scan stops all worker loops`() = runBlocking {
        val entered = AtomicInteger()
        var finished = 0
        val job = launch { forEachMetadataItem((0..99).toList()) {
            entered.incrementAndGet(); delay(10000); finished++
        } }
        while (entered.get() < 10) yield()
        job.cancelAndJoin()
        assertEquals(10, entered.get())
        assertEquals(0, finished)
    }
}
