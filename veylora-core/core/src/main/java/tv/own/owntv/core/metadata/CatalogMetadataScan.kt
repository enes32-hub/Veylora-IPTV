package tv.own.owntv.core.metadata

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tv.own.owntv.core.CoreBuildInfo
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.settings.SettingsRepository

data class CatalogScanProgress(val total: Int = 0, val completed: Int = 0, val missing: Int = 0, val retrying: Int = 0, val running: Boolean = false) {
    val fraction: Float get() = if (total == 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    val showBanner: Boolean get() = running && total > 0 && completed < total
}

object CatalogScanStatus {
    internal val mutable = MutableStateFlow(CatalogScanProgress())
    val state = mutable.asStateFlow()
}

/** Owned by the visible Activity; cancellation stops dispatch and preserves durable completion rows. */
class CatalogMetadataScan(private val db: OwnTVDatabase, private val metadata: MetadataRepository, private val settings: SettingsRepository) {
    private val passCompleted = java.util.concurrent.atomic.AtomicInteger()
    private val passRetrying = java.util.concurrent.atomic.AtomicInteger()
    private var startedAt = 0L
    suspend fun run(profileId: Long) = withContext(Dispatchers.IO) {
        if (profileId < 0 || CoreBuildInfo.personalTmdbToken.isBlank()) return@withContext
        CatalogScanStatus.mutable.update { it.copy(running = false) }
        startedAt = System.nanoTime()
        try {
            while (currentCoroutineContext().isActive) {
                if (!settings.metadataConfig().enabled) { delay(30_000); continue }
                val sources = db.sourceDao().sourceIdsForProfile(profileId)
                val total = db.movieDao().scanCount(sources) + db.seriesDao().scanCount(sources)
                // Inventory persisted completion first. No network work or zero-progress banner
                // while an Activity is recreated (including a display-language switch).
                val completedKeys = mutableSetOf<String>()
                var completedRows = 0
                var inventoryAfter = 0L
                while (true) {
                    val page = db.movieDao().scanPage(sources, inventoryAfter)
                    if (page.isEmpty()) break
                    val keys = page.map { MetadataRepository.movieLocalKey(it) }
                    val done = metadata.completedScanKeys(keys)
                    completedRows += keys.count { it in done }
                    completedKeys += done
                    inventoryAfter = page.last().id
                }
                inventoryAfter = 0L
                while (true) {
                    val page = db.seriesDao().scanPage(sources, inventoryAfter)
                    if (page.isEmpty()) break
                    val keys = page.map { MetadataRepository.seriesLocalKey(it) }
                    val done = metadata.completedScanKeys(keys)
                    completedRows += keys.count { it in done }
                    completedKeys += done
                    inventoryAfter = page.last().id
                }
                passCompleted.set(completedRows.coerceAtMost(total))
                passRetrying.set(0)
                CatalogScanStatus.mutable.value = CatalogScanProgress(total = total, completed = passCompleted.get())
                var after = 0L
                while (true) {
                    val page = db.movieDao().scanPage(sources, after)
                    if (page.isEmpty()) break
                    forEachMetadataItem(page) { movie ->
                        if (MetadataRepository.movieLocalKey(movie) !in completedKeys)
                            attempt(false) { metadata.scanMovie(movie) }
                    }
                    after = page.last().id
                }
                after = 0L
                while (true) {
                    val page = db.seriesDao().scanPage(sources, after)
                    if (page.isEmpty()) break
                    forEachMetadataItem(page) { series ->
                        if (MetadataRepository.seriesLocalKey(series) !in completedKeys)
                            attempt(false) { metadata.scanSeries(series) }
                    }
                    after = page.last().id
                }
                CatalogScanStatus.mutable.update { it.copy(completed = passCompleted.get(), retrying = passRetrying.get(), running = false) }
                reportProgress()
                // Only local completion checks for old items; new rows are found on the next pass.
                delay(60_000)
            }
        } finally {
            CatalogScanStatus.mutable.update { it.copy(running = false) }
        }
    }

    private suspend fun attempt(alreadyComplete: Boolean, block: suspend () -> Boolean) {
        if (!alreadyComplete) CatalogScanStatus.mutable.update { it.copy(running = true) }
        val complete = alreadyComplete || try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
        if (complete) passCompleted.incrementAndGet() else passRetrying.incrementAndGet()
        CatalogScanStatus.mutable.update { it.copy(completed = maxOf(it.completed, passCompleted.get()).coerceAtMost(it.total), retrying = passRetrying.get()) }
        if (!alreadyComplete && (passCompleted.get() + passRetrying.get()) % 100 == 0) reportProgress()
    }

    private fun reportProgress() {
        android.util.Log.i("VeyloraScan", "completed=${passCompleted.get()} total=${CatalogScanStatus.state.value.total} retry=${passRetrying.get()} elapsedMs=${(System.nanoTime() - startedAt) / 1_000_000}")
    }
}
