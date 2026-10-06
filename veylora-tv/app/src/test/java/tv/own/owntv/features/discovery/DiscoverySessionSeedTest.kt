package tv.own.owntv.features.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DiscoverySessionSeedTest {
    @Test fun `seed is generated once and reused for every catalog section in the session`() {
        var generated = 0
        val seed = DiscoverySessionSeed {
            generated += 1
            731L
        }

        val movies = seed.value
        val series = seed.value

        assertEquals(731L, movies)
        assertEquals(movies, series)
        assertEquals(1, generated)
    }

    @Test fun `new app session can receive a different seed`() {
        val first = DiscoverySessionSeed { 11L }
        val next = DiscoverySessionSeed { 12L }

        assertNotEquals(first.value, next.value)
    }

    @Test fun `returning to app starts exactly one new shuffle session`() {
        var generated = 0
        val seed = DiscoverySessionSeed { (++generated).toLong() }

        assertEquals(1L, seed.value)
        seed.beginSession()

        assertEquals(2L, seed.value)
        assertEquals(2L, seed.value)
        assertEquals(2, generated)
    }
}
