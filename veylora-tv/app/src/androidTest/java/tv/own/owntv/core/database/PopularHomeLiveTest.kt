package tv.own.owntv.core.database

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.TrendingDao
import tv.own.owntv.core.home.HomeFeedReader
import tv.own.owntv.core.home.TrendingHomeItem
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.trending.TrendingRepository
import tv.own.owntv.core.trending.TrendingScheduleStore
import tv.own.owntv.core.metadata.MetadataBudget
import tv.own.owntv.core.trending.TrendingRefreshOutcome

/** Explicit live metadata refresh; never opens a stream or exports account data. */
class PopularHomeLiveTest {
    @Test fun refreshAndReadProviderMatchedMovies() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePopular") == "true")
        withTimeout(240_000) {
            val koin = GlobalContext.get()
            val settings = koin.get<SettingsRepository>()
            assertFalse("Do not use a personal TMDB key", settings.metadataConfig().tmdbApiKey.isNotBlank())
            val profile = settings.activeProfileId.first()
            val sourceIds = koin.get<SourceDao>().sourceIdsForProfile(profile)
            assertTrue(sourceIds.isNotEmpty())
            val results = sourceIds.map { koin.get<TrendingRepository>().refresh(it) }
            assertTrue("Refresh failed; preserved data is not proof of fresh success", results.all { it is TrendingRefreshOutcome.Replaced })
            val schedule = koin.get<TrendingScheduleStore>()
            val candidates = requireNotNull(schedule.candidates())
            assertEquals(2, candidates.feedVersion)
            assertTrue(candidates.movies.size <= 100 && candidates.series.size <= 100)
            val before = koin.get<MetadataBudget>().status().usedDay
            sourceIds.forEach { koin.get<TrendingRepository>().refresh(it) }
            assertEquals("Warm reopen spent metadata budget", before, koin.get<MetadataBudget>().status().usedDay)
            assertEquals("Warm reopen replaced daily candidates", candidates, schedule.candidates())
            val snapshots = koin.get<TrendingDao>().getItemsForSources(sourceIds)
            val feed = koin.get<HomeFeedReader>().load(profile)
            val movies = feed.trendingItems.filterIsInstance<TrendingHomeItem.Movie>()
            val series = feed.trendingItems.filterIsInstance<TrendingHomeItem.Series>()
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("stream", "\nrefresh=${results.map { it.javaClass.simpleName }}; candidates=${candidates.movies.size}/${candidates.series.size}; matched=${snapshots.size}; homeMovies=${movies.size}; homeSeries=${series.size}; warmReopenRequests=0\n")
            })
            assertTrue("No verified provider-matched movie in home feed", movies.isNotEmpty())
            assertTrue(movies.all { it.movie.sourceId in sourceIds })
        }
    }
}
