package tv.own.owntv.core.trending

import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.metadata.TrendingCandidate
import tv.own.owntv.core.metadata.TrendingFeedPage

/** One bounded batch. Never publish a mixture of old and partially downloaded pages. */
internal object PopularFeedLoader {
    const val REFRESH_INTERVAL_MS = 604_800_000L
    data class Batch(val movies: List<TrendingCandidate>, val series: List<TrendingCandidate>)

    fun needsRefresh(fetchedAt: Long?, now: Long, allowNetwork: Boolean = true): Boolean =
        allowNetwork && (fetchedAt == null || (now >= fetchedAt && now - fetchedAt >= REFRESH_INTERVAL_MS))

    suspend fun load(fetch: suspend (MetadataType, Int) -> TrendingFeedPage?): Batch? {
        suspend fun pages(type: MetadataType): List<TrendingCandidate>? {
            val pages = mutableListOf<TrendingFeedPage>()
            for (number in 1..5) {
                val page = fetch(type, number) ?: return null
                if (page.page != number) return null
                pages += page
                if (number >= page.totalPages) break
            }
            return TrendingFeedPage.merge(pages, limit = 100)
        }
        val movies = pages(MetadataType.MOVIE) ?: return null
        val series = pages(MetadataType.TV) ?: return null
        return Batch(movies, series)
    }
}
