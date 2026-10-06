package tv.own.owntv.core.metadata

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.catalog.discoveryValues

class MetadataModePriorityTest {
    @Test fun legacyTmdbOnlyBecomesCombinedAndIsNotSelectable() {
        assertEquals(MetadataMode.PROVIDER_PLUS_TMDB, MetadataMode.fromSaved("TMDB_ONLY", false))
        assertEquals(MetadataMode.PROVIDER, MetadataMode.fromSaved("PROVIDER"))
        assertEquals(listOf(MetadataMode.PROVIDER, MetadataMode.PROVIDER_PLUS_TMDB), MetadataMode.selectable)
    }
    @Test fun combinedModeUsesMatchedTmdbInsteadOfConflictingProviderValues() {
        val values = discoveryValues(1, 2014, null, 5.0, 1975, 8.7, MetadataMode.PROVIDER_PLUS_TMDB)
        assertEquals(1975, values.year)
        assertEquals(8.7, values.rating!!, 0.001)
        assertTrue(MetadataMode.PROVIDER_PLUS_TMDB.tmdbWins)
    }
    @Test fun combinedModeWithoutTmdbFallsBackToProvider() {
        val values = discoveryValues(1, 1975, null, 8.7, null, null, MetadataMode.PROVIDER_PLUS_TMDB)
        assertEquals(1975, values.year)
        assertEquals(8.7, values.rating!!, 0.001)
    }
    @Test fun providerModeIgnoresAvailableTmdbValues() {
        val values = discoveryValues(1, 2014, null, 5.0, 1975, 8.7, MetadataMode.PROVIDER)
        assertEquals(2014, values.year)
        assertEquals(5.0, values.rating!!, 0.001)
    }
}
