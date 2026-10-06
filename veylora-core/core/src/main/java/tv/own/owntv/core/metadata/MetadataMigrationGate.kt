package tv.own.owntv.core.metadata

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All first resolves await migration; a failed/cancelled migration can be retried. */
internal class MetadataMigrationGate {
    private val mutex = Mutex()
    private var complete = false
    suspend fun ensure(migrate: suspend () -> Unit) = mutex.withLock {
        if (!complete) {
            migrate()
            complete = true
        }
    }
}
