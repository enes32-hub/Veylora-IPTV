package tv.own.owntv.core.trending

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.metadata.*

class PopularFeedLoaderTest {
    @Test fun backgroundMatchingNeverDownloadsEvenWhenCacheIsMissingOrStale() {
        assertFalse(PopularFeedLoader.needsRefresh(null, 100000000L, allowNetwork = false))
        assertFalse(PopularFeedLoader.needsRefresh(1L, 100000000L, allowNetwork = false))
    }
    @Test fun fivePagesPerTypeAreLoadedOnceWithoutDetailRequests() = runBlocking {
        val calls = mutableListOf<Pair<MetadataType, Int>>()
        val result = PopularFeedLoader.load { type, page ->
            calls += type to page
            TrendingFeedPage(page, 500, (1..20).map { candidate(type, (page - 1) * 20 + it) })
        }!!
        assertEquals(10, calls.size)
        assertEquals((1..5).toList(), calls.filter { it.first == MetadataType.TV }.map { it.second })
        assertEquals((1..100).toList(), result.movies.map { it.tmdbId })
        assertEquals((1..100).toList(), result.series.map { it.tmdbId })
    }
    @Test fun failedPageDoesNotPublishPartialBatch() = runBlocking {
        var calls = 0
        val result = PopularFeedLoader.load { type, page ->
            calls++
            if (page == 3) null else TrendingFeedPage(page, 500, listOf(candidate(type, page)))
        }
        assertNull(result)
        assertEquals(3, calls)
    }
    @Test fun shortAndEmptyFeedsDoNotFetchNonexistentPages() = runBlocking {
        var calls = 0
        val result = PopularFeedLoader.load { _, page -> calls++; TrendingFeedPage(page, 1, emptyList()) }!!
        assertEquals(2, calls)
        assertTrue(result.movies.isEmpty())
        assertTrue(result.series.isEmpty())
    }
    @Test fun reopeningThroughoutTheWeekDoesNotRequestAnotherBatch() {
        val at = 100000000L
        for (day in 0..6) assertFalse(PopularFeedLoader.needsRefresh(at, at + day * 86400000L))
        assertFalse(PopularFeedLoader.needsRefresh(at, at + 604799999L))
        assertTrue(PopularFeedLoader.needsRefresh(at, at + 604800000L))
        assertTrue(PopularFeedLoader.needsRefresh(null, at))
        assertFalse(PopularFeedLoader.needsRefresh(at, at - 1))
    }
    private fun candidate(type: MetadataType, id: Int) = TrendingCandidate(
        id, type, "Title $id", null, 2024, null, null, null, null, 100.0, id,
    )
}
