package tv.own.owntv.core.trending

import android.os.SystemClock
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.TrendingDao
import tv.own.owntv.core.database.entity.TrendingAttemptStatus
import tv.own.owntv.core.database.entity.TrendingItemEntity
import tv.own.owntv.core.database.entity.TrendingSnapshotEntity
import tv.own.owntv.core.database.entity.TrendingSnapshotStatus
import tv.own.owntv.core.metadata.MetadataProvider
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.core.metadata.TrendingCandidate
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.SeriesRepository
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.settings.SettingsRepository

class TrendingRepository(
    private val sourceDao: SourceDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val trendingDao: TrendingDao,
    private val metadataProvider: MetadataProvider,
    private val metadataRepository: MetadataRepository,
    private val settings: SettingsRepository,
    private val seriesRepository: SeriesRepository,
    private val schedule: TrendingScheduleStore,
) {
    suspend fun recordUnexpectedFailure(sourceId: Long): TrendingRefreshOutcome.PreservedFailure =
        preserveFailure(
            sourceId = sourceId,
            stage = "unexpected error",
            language = settings.metadataConfig().resolvedLanguage,
            startedAt = SystemClock.elapsedRealtime(),
        )

    /**
     * Rebuilds the Now Trending row for one playlist.
     *
     * Runs after every catalog sync, but only the *matching* stage runs every time — the TMDB fetch is
     * gated by [TrendingScheduleStore]. Adding 500 films therefore shows up in the row the same day,
     * while the popular list itself is re-downloaded at most once every 7 days. [force] is the
     * maintainer-only override behind `BuildConfig.DEV_TOOLS`.
     */
    suspend fun refresh(
        sourceId: Long,
        force: Boolean = false,
        allowNetworkRefresh: Boolean = true,
        onProgress: (TrendingRefreshProgress) -> Unit = {},
    ): TrendingRefreshOutcome = refreshMutex.withLock { refreshLocked(sourceId, force, allowNetworkRefresh, onProgress) }

    private suspend fun refreshLocked(sourceId: Long, force: Boolean, allowNetworkRefresh: Boolean, onProgress: (TrendingRefreshProgress) -> Unit): TrendingRefreshOutcome {
        val totalStarted = SystemClock.elapsedRealtime()
        val config = settings.metadataConfig()
        if (!config.enabled) return TrendingRefreshOutcome.SkippedProviderMode
        val source = sourceDao.getById(sourceId) ?: return TrendingRefreshOutcome.SourceMissing
        val enabled = SyncContentTypes.enabledFor(source)
        if (!enabled.movies && !enabled.series) {
            val completedAt = System.currentTimeMillis()
            trendingDao.writeBelowThreshold(
                TrendingSnapshotEntity(
                    sourceId = sourceId,
                    status = TrendingSnapshotStatus.BELOW_THRESHOLD,
                    metadataLanguage = config.resolvedLanguage,
                    refreshedAt = completedAt,
                    candidateFetchedAt = 0,
                    generationId = UUID.randomUUID().toString(),
                    itemCount = 0,
                    matchedItemCount = 0,
                    lastAttemptAt = completedAt,
                    lastAttemptStatus = TrendingAttemptStatus.BELOW_THRESHOLD,
                    failureStage = "no VOD content",
                ),
            )
            return TrendingRefreshOutcome.NoVodScope
        }

        val language = config.resolvedLanguage
        val now = System.currentTimeMillis()
        val stored = schedule.candidates()
        val state = trendingDao.getState(sourceId)
        val useNetwork = force || PopularFeedLoader.needsRefresh(
            stored?.takeIf { it.feedVersion == 2 }?.fetchedAt, now, allowNetworkRefresh,
        )
        if (!useNetwork && stored == null) return TrendingRefreshOutcome.PreservedFailure("popular candidates")
        // A failed refresh keeps the last complete snapshot and avoids repeated opening bursts.
        if (!force && useNetwork && state?.lastAttemptStatus == TrendingAttemptStatus.FAILED &&
            now >= state.lastAttemptAt && now - state.lastAttemptAt < 15 * 60_000L) {
            return TrendingRefreshOutcome.PreservedFailure("retry cooldown")
        }
        onProgress(TrendingRefreshProgress.Fetching)
        val fetchStarted = SystemClock.elapsedRealtime()
        val candidates = if (useNetwork) {
            val batch = PopularFeedLoader.load { type, page ->
                delay(1_200L)
                metadataProvider.trendingPage(type, page)
            } ?: return preserveFailure(sourceId, "popular candidates", language, totalStarted)
            TrendingScheduleStore.Candidates(
                language = language, fetchedAt = System.currentTimeMillis(),
                movies = batch.movies, series = batch.series,
                movieTotalPages = 5, seriesTotalPages = 5,
                moviePagesLoaded = 5, seriesPagesLoaded = 5, feedVersion = 2,
            ).also { schedule.storeCandidates(it) }
        } else checkNotNull(stored)
        val fetchMs = SystemClock.elapsedRealtime() - fetchStarted
        val movieCandidates = candidates.movies
        val seriesCandidates = candidates.series
        val candidateFetchedAt = candidates.fetchedAt
        val candidateLanguage = candidates.language
        onProgress(TrendingRefreshProgress.CandidatesReceived(movieCandidates.size, seriesCandidates.size))

        val movieBackfillTotal = if (enabled.movies) movieDao.trendingMetadataBackfillCount(sourceId) else 0
        val seriesBackfillTotal = if (enabled.series) seriesDao.trendingMetadataBackfillCount(sourceId) else 0
        val backfillTotal = movieBackfillTotal + seriesBackfillTotal
        var backfillProcessed = 0
        onProgress(TrendingRefreshProgress.PreparingCatalog(0, backfillTotal))
        val preparationStarted = SystemClock.elapsedRealtime()
        val backfilledMovies = if (enabled.movies) backfillMovies(sourceId) { processed ->
            backfillProcessed = processed
            onProgress(TrendingRefreshProgress.PreparingCatalog(backfillProcessed, backfillTotal))
        } else 0
        val backfilledSeries = if (enabled.series) backfillSeries(sourceId) { processed ->
            onProgress(TrendingRefreshProgress.PreparingCatalog(backfillProcessed + processed, backfillTotal))
        } else 0
        val preparationMs = SystemClock.elapsedRealtime() - preparationStarted
        if (backfilledMovies + backfilledSeries > 0) {
            Log.i(TAG, "sourceId=$sourceId provider metadata backfilled movies=$backfilledMovies series=$backfilledSeries ms=$preparationMs")
        }

        var movieMatchCount = 0
        var seriesMatchCount = 0

        suspend fun matchMovies(candidates: List<TrendingCandidate>): TrendingMatchResult {
            movieMatchCount = 0
            onProgress(TrendingRefreshProgress.MatchingMovies(0, 0, candidates.size, TrendingMatcher.MAX_PER_MEDIA_TYPE))
            return TrendingMatcher.matchMedia(
                candidates = candidates,
                mediaType = MediaType.MOVIE,
                preferredLanguage = config.resolvedLanguage,
                limit = TrendingMatcher.MAX_PER_MEDIA_TYPE,
                exactLookup = { movieDao.trendingExact(sourceId, it) },
                ftsLookup = { query, limit -> movieDao.trendingFts(sourceId, query, limit) },
            ) { checked, match ->
                if (match != null) movieMatchCount++
                onProgress(
                    TrendingRefreshProgress.MatchingMovies(
                        checked = checked,
                        matched = movieMatchCount,
                        candidates = candidates.size,
                        target = TrendingMatcher.MAX_PER_MEDIA_TYPE,
                    ),
                )
            }
        }

        suspend fun matchSeries(candidates: List<TrendingCandidate>, target: Int): TrendingMatchResult {
            seriesMatchCount = 0
            onProgress(TrendingRefreshProgress.MatchingSeries(0, 0, candidates.size, target))
            return TrendingMatcher.matchMedia(
                candidates = candidates,
                mediaType = MediaType.SERIES,
                preferredLanguage = config.resolvedLanguage,
                limit = target,
                exactLookup = { seriesDao.trendingExact(sourceId, it) },
                ftsLookup = { query, limit -> seriesDao.trendingFts(sourceId, query, limit) },
            ) { checked, match ->
                if (match != null) seriesMatchCount++
                onProgress(
                    TrendingRefreshProgress.MatchingSeries(
                        checked = checked,
                        matched = seriesMatchCount,
                        candidates = candidates.size,
                        target = target,
                    ),
                )
            }
        }

        val movieResult =
            if (enabled.movies) matchMovies(movieCandidates) else TrendingMatchResult(emptyList(), 0, 0, 0)
        val seriesResult =
            if (enabled.series) matchSeries(seriesCandidates, TrendingMatcher.MAX_PER_MEDIA_TYPE)
            else TrendingMatchResult(emptyList(), 0, 0, 0)

        val selections = TrendingMatcher.assemble(movieResult.selections, seriesResult.selections)
        val completedAt = System.currentTimeMillis()
        val generationId = UUID.randomUUID().toString()
        if (selections.size < TrendingDao.MIN_ELIGIBLE_ITEMS) {
            val writeStarted = SystemClock.elapsedRealtime()
            trendingDao.writeBelowThreshold(
                TrendingSnapshotEntity(
                    sourceId = sourceId,
                    status = TrendingSnapshotStatus.BELOW_THRESHOLD,
                    metadataLanguage = candidateLanguage,
                    refreshedAt = completedAt,
                    candidateFetchedAt = candidateFetchedAt,
                    generationId = generationId,
                    itemCount = 0,
                    matchedItemCount = selections.size,
                    lastAttemptAt = completedAt,
                    lastAttemptStatus = TrendingAttemptStatus.BELOW_THRESHOLD,
                ),
            )
            // Short retry rather than a full span: too few matches usually means the catalog is still
            // filling in, and matching re-runs for free on every sync in between anyway.
            schedule.setRetry(sourceId, candidateFetchedAt + PopularFeedLoader.REFRESH_INTERVAL_MS)
            logResult(sourceId, movieCandidates.size, seriesCandidates.size, movieResult, seriesResult, selections.size, "below-threshold", fetchMs, preparationMs, 0, SystemClock.elapsedRealtime() - writeStarted, totalStarted)
            return TrendingRefreshOutcome.Replaced(itemCount = selections.size, eligible = false)
        }

        // List responses already contain card fields. Do not fetch seasons or one detail per match.
        // Full details stay on-demand when the user opens a title.
        val enrichmentStarted = SystemClock.elapsedRealtime()
        val details = selections.map { selection ->
            selection to metadataRepository.cachedDetails(
                selection.candidate.tmdbId, selection.candidate.type, allowNetwork = false,
            )
        }
        val enrichmentMs = SystemClock.elapsedRealtime() - enrichmentStarted

        val items = details.mapIndexed { position, (selection, detail) ->
            val candidate = selection.candidate
            val variant = selection.variant
            TrendingItemEntity(
                sourceId = sourceId,
                position = position,
                tmdbId = candidate.tmdbId,
                mediaType = variant.item.mediaType,
                trendingRank = candidate.trendingRank,
                providerItemId = variant.item.id,
                providerRemoteId = variant.item.remoteId,
                providerStableKey = variant.stableKey,
                providerRawName = variant.item.name,
                canonicalTitle = variant.canonicalTitle,
                providerLanguage = variant.language,
                advertisedQuality = variant.quality.label,
                advertisedCapabilities = variant.capabilities.takeIf { it.isNotEmpty() }?.joinToString(" • "),
                localizedTitle = candidate.localizedTitle,
                originalTitle = candidate.originalTitle,
                year = detail?.year ?: candidate.year,
                overview = detail?.overview ?: candidate.overview,
                posterPath = detail?.posterPath ?: candidate.posterPath,
                backdropPath = detail?.backdropPath ?: candidate.backdropPath,
                rating = detail?.rating ?: candidate.rating,
                trailerKey = detail?.trailerKey,
                generationId = generationId,
                refreshedAt = completedAt,
            )
        }
        onProgress(TrendingRefreshProgress.Publishing(items.size))
        val writeStarted = SystemClock.elapsedRealtime()
        trendingDao.replaceSnapshot(
            TrendingSnapshotEntity(
                sourceId = sourceId,
                status = TrendingSnapshotStatus.ELIGIBLE,
                metadataLanguage = candidateLanguage,
                refreshedAt = completedAt,
                candidateFetchedAt = candidateFetchedAt,
                generationId = generationId,
                itemCount = items.size,
                matchedItemCount = items.size,
                lastAttemptAt = completedAt,
                lastAttemptStatus = TrendingAttemptStatus.SUCCESS,
            ),
            items,
        )
        val writeMs = SystemClock.elapsedRealtime() - writeStarted
        // Only a run that was allowed through the gate books the next one; the free re-match runs in
        // between must not keep pushing the deadline away.
        val nextFetchAt = candidateFetchedAt + PopularFeedLoader.REFRESH_INTERVAL_MS
        schedule.setRetry(sourceId, nextFetchAt)
        logResult(sourceId, movieCandidates.size, seriesCandidates.size, movieResult, seriesResult, items.size, "published", fetchMs, preparationMs, enrichmentMs, writeMs, totalStarted)
        Log.i(TAG, "sourceId=$sourceId nextFetchInDays=${((nextFetchAt - completedAt).coerceAtLeast(0)) / TrendingScheduleStore.DAY_MS}")
        return TrendingRefreshOutcome.Replaced(itemCount = items.size, eligible = true)
    }

    private suspend fun backfillMovies(sourceId: Long, onProgress: (Int) -> Unit): Int {
        var count = 0
        while (true) {
            val rows = movieDao.trendingMetadataBackfill(sourceId, BACKFILL_BATCH)
            if (rows.isEmpty()) return count
            movieDao.updateAll(rows)
            count += rows.size
            onProgress(count)
        }
    }

    private suspend fun backfillSeries(sourceId: Long, onProgress: (Int) -> Unit): Int {
        var count = 0
        while (true) {
            val rows = seriesDao.trendingMetadataBackfill(sourceId, BACKFILL_BATCH)
            if (rows.isEmpty()) return count
            seriesDao.updateSeries(rows)
            count += rows.size
            onProgress(count)
        }
    }

    private suspend fun preserveFailure(sourceId: Long, stage: String, language: String, startedAt: Long): TrendingRefreshOutcome.PreservedFailure {
        val attemptAt = System.currentTimeMillis()
        trendingDao.recordFailure(
            TrendingSnapshotEntity(
                sourceId = sourceId,
                status = TrendingSnapshotStatus.NEVER_BUILT,
                metadataLanguage = language,
                refreshedAt = 0,
                candidateFetchedAt = 0,
                generationId = "",
                itemCount = 0,
                lastAttemptAt = attemptAt,
                lastAttemptStatus = TrendingAttemptStatus.FAILED,
                failureStage = stage,
            ),
            stage,
        )
        Log.w(TAG, "Preserve old snapshot sourceId=$sourceId failedStage=$stage totalMs=${SystemClock.elapsedRealtime() - startedAt}")
        return TrendingRefreshOutcome.PreservedFailure(stage)
    }

    private fun logResult(
        sourceId: Long,
        movieCandidates: Int,
        seriesCandidates: Int,
        movies: TrendingMatchResult,
        series: TrendingMatchResult,
        finalCount: Int,
        decision: String,
        fetchMs: Long,
        preparationMs: Long,
        enrichmentMs: Long,
        writeMs: Long,
        startedAt: Long,
    ) {
        Log.i(TAG, "sourceId=$sourceId candidates=$movieCandidates/$seriesCandidates matches=${movies.selections.size}/${series.selections.size} final=$finalCount decision=$decision")
        Log.i(TAG, "sourceId=$sourceId timing fetchMs=$fetchMs preparationMs=$preparationMs exactMs=${movies.exactLookupMs + series.exactLookupMs} ftsMs=${movies.ftsLookupMs + series.ftsLookupMs} ftsFallbacks=${movies.ftsFallbacks + series.ftsFallbacks} enrichmentMs=$enrichmentMs snapshotWriteMs=$writeMs totalMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    companion object {
        private const val TAG = "TrendingRepository"
        private val refreshMutex = Mutex()
        private const val BACKFILL_BATCH = 1_000
    }
}

sealed interface TrendingRefreshOutcome {
    data object SkippedProviderMode : TrendingRefreshOutcome
    data object SkippedHidden : TrendingRefreshOutcome
    data object SourceMissing : TrendingRefreshOutcome
    data object NoVodScope : TrendingRefreshOutcome
    data class PreservedFailure(val stage: String) : TrendingRefreshOutcome
    data class Replaced(val itemCount: Int, val eligible: Boolean) : TrendingRefreshOutcome
}

sealed interface TrendingRefreshProgress {
    data object Fetching : TrendingRefreshProgress
    data class CandidatesReceived(val movies: Int, val series: Int) : TrendingRefreshProgress {
        val total: Int get() = movies + series
    }
    data class PreparingCatalog(val processed: Int, val total: Int) : TrendingRefreshProgress
    data class MatchingMovies(val checked: Int, val matched: Int, val candidates: Int, val target: Int) : TrendingRefreshProgress
    data class MatchingSeries(val checked: Int, val matched: Int, val candidates: Int, val target: Int) : TrendingRefreshProgress
    data class LoadingProviderSeasons(val processed: Int, val total: Int) : TrendingRefreshProgress
    data class Enriching(val itemCount: Int) : TrendingRefreshProgress
    data class Publishing(val itemCount: Int) : TrendingRefreshProgress
}
