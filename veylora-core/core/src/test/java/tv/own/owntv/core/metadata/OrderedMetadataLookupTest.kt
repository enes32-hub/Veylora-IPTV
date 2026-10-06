package tv.own.owntv.core.metadata

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OrderedMetadataLookupTest {
    private fun hit(id: Int, year: Int, type: MetadataType = MetadataType.MOVIE) =
        MetadataSearchResult(id, type, "Guguk Kuşu", null, year, null, null, 0.0)
    private val details = MovieDetails(510, null, "One Flew Over the Cuckoo’s Nest", 1975,
        null, null, null, null, emptyList(), emptyList(), null, null)

    @Test fun `valid identity performs exactly one detail request and no search`() = runBlocking {
        var calls = 0
        val result = orderedMetadataLookup(510, 1975, MetadataType.MOVIE,
            identity = { id -> calls++; assertEquals(510, id); IdentityDetails.Found(details) },
            search = { error("Identity success must not search") })
        assertEquals(1, calls)
        assertSame(details, (result as MetadataLookup.Found).details)
    }
    @Test fun `missing identity searches once and chooses provider year within response`() = runBlocking {
        var searches = 0
        val result = orderedMetadataLookup(null, 1975, MetadataType.MOVIE,
            identity = { error("Absent identity must not fetch") },
            search = { searches++; listOf(hit(818252,2014), hit(510,1975)) })
        assertEquals(1, searches)
        assertEquals(510, (result as MetadataLookup.Found).tmdbId)
    }
    @Test fun `invalid identity falls back once but unavailable identity never searches`() = runBlocking {
        var searches = 0
        val result = orderedMetadataLookup(999, 1990, MetadataType.MOVIE,
            identity = { IdentityDetails.Missing }, search = { searches++; listOf(hit(510,1975)) })
        assertEquals(510, (result as MetadataLookup.Found).tmdbId)
        assertEquals(1, searches)
        assertEquals(MetadataLookup.Unavailable, orderedMetadataLookup(510, null, MetadataType.MOVIE,
            identity = { IdentityDetails.Unavailable }, search = { error("No fallback during outage") }))
    }
    @Test fun `empty response and outage remain distinct and type is respected`() = runBlocking {
        assertEquals(MetadataLookup.Missing, orderedMetadataLookup(null, null, MetadataType.TV,
            identity = { error("Unused") }, search = { listOf(hit(510,1975)) }))
        assertEquals(MetadataLookup.Unavailable, orderedMetadataLookup(null, null, MetadataType.TV,
            identity = { error("Unused") }, search = { null }))
    }
}
