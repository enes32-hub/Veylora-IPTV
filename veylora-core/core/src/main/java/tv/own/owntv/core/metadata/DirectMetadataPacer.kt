package tv.own.owntv.core.metadata

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.nanoseconds

/** Shared by foreground details and catalogue work; no bursts after an idle period. */
class DirectMetadataPacer(private val workerCount: Int = 10, private val nowNanos: () -> Long = System::nanoTime) {
    init { require(workerCount in 1..10) }
    private val lock = Mutex()
    private val nextWorker = LongArray(workerCount)
    private var nextGlobal = 0L
    private var cooldown = 0L
    private var nextLane = 0
    suspend fun acquire(workerId: Int? = null): Int {
        val lane = lock.withLock {
            if (workerId == null) (nextLane++ % workerCount) else {
                require(workerId in 0 until workerCount)
                workerId
            }
        }
        while (true) {
            val wait = lock.withLock {
                val now = nowNanos()
                val remaining = maxOf(nextGlobal, nextWorker[lane], cooldown) - now
                if (remaining <= 0) {
                    nextGlobal = now + if (workerCount == 1) 29_000_000L else 2_500_000L
                    nextWorker[lane] = now + 25_000_000L
                }
                remaining
            }
            if (wait <= 0) return lane
            // Sleeping outside the lock lets a 429 immediately postpone all queued callers.
            delay(wait.nanoseconds)
        }
    }
    suspend fun backOff(seconds: Long) = lock.withLock {
        val boundedSeconds = seconds.coerceIn(1, Long.MAX_VALUE / 1_000_000_000L / 2)
        cooldown = maxOf(cooldown, nowNanos() + boundedSeconds * 1_000_000_000L)
    }
}
