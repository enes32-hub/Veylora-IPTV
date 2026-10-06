package tv.own.owntv.core.metadata

import org.junit.Assert.*
import org.junit.Test

class ProviderMetadataIdentityTest {
    @Test fun `movie detail identity and release year win over unrelated fields`() {
        val result = ProviderMetadataIdentity.parse("""{"info":{"tmdb_id":"510","releasedate":"1975-11-19"},"movie_data":{"year":2014},"episodes":{"tmdb_id":77}}""")
        assertEquals(510, result.tmdbId)
        assertEquals(1975, result.year)
    }
    @Test fun `series identity can come from info and no identity remains absent`() {
        assertEquals(1399, ProviderMetadataIdentity.parse("""{"info":{"tmdb":"1399","year":"2011"}}""").tmdbId)
        assertNull(ProviderMetadataIdentity.parse("""{"info":{"name":"Series"},"episodes":{"tmdb_id":77}}""").tmdbId)
    }
    @Test fun `invalid ids are not coerced and invalid year is ignored`() {
        for (id in listOf("0", "-1", "510.5", "tt0073486", "999999999999999")) {
            assertNull(ProviderMetadataIdentity.parse("""{"info":{"tmdb_id":"$id","year":"bad"}}""").tmdbId)
        }
        assertNull(ProviderMetadataIdentity.parse("""{"info":{"year":0}}""").year)
    }
}
