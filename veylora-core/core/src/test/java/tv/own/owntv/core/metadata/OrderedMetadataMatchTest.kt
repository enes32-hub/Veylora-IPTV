package tv.own.owntv.core.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderedMetadataMatchTest {
    private fun hit(id: Int, year: Int?, type: MetadataType = MetadataType.MOVIE) =
        MetadataSearchResult(id, type, "Title $id", null, year, null, null, 0.0)

    @Test fun `provider year selects correct film without a second query`() {
        val hits = listOf(hit(818252, 2014), hit(554, 2002), hit(510, 1975))
        assertEquals(510, OrderedMetadataMatch.select(hits, 1975, MetadataType.MOVIE)?.tmdbId)
    }

    @Test fun `missing or unmatched year preserves first result`() {
        val hits = listOf(hit(9, 2014), hit(3, 1975))
        assertEquals(9, OrderedMetadataMatch.select(hits, null, MetadataType.MOVIE)?.tmdbId)
        assertEquals(9, OrderedMetadataMatch.select(hits, 2000, MetadataType.MOVIE)?.tmdbId)
    }

    @Test fun `same year preserves provider result order`() {
        assertEquals(9, OrderedMetadataMatch.select(listOf(hit(9, 1975), hit(3, 1975)), 1975, MetadataType.MOVIE)?.tmdbId)
    }

    @Test fun `series and films never cross match namespaces`() {
        val hits = listOf(hit(8, 2000), hit(4, 2000, MetadataType.TV))
        assertEquals(4, OrderedMetadataMatch.select(hits, 2000, MetadataType.TV)?.tmdbId)
        assertNull(OrderedMetadataMatch.select(emptyList(), 2000, MetadataType.TV))
    }
}
