package tv.own.owntv.core.catalog

import org.junit.Assert.*
import org.junit.Test

class DiscoveryFilterTest {
    @Test fun `unfiltered catalog keeps titles with missing metadata`() {
        assertTrue(DiscoveryFilter().accepts(null, null, emptyList()))
        assertEquals(0, DiscoveryFilter().activeCount)
    }
    @Test fun `inclusive ranges combine with exact genre and watched status`() {
        val f = DiscoveryFilter(genre = "Dram", minYear = 1990, maxYear = 2000,
            minRating = 5.0, maxRating = 7.0, watched = false)
        assertEquals(4, f.activeCount)
        assertTrue(f.accepts(1990, 5.0, listOf("Dram"), false))
        assertTrue(f.accepts(2000, 7.0, listOf("Dram"), false))
        assertFalse(f.accepts(1995, 6.0, listOf("Melodram"), false))
        assertFalse(f.accepts(1995, 6.0, listOf("Dram"), true))
        assertFalse(f.accepts(null, 6.0, listOf("Dram"), false))
    }
    @Test fun `invalid ranges are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DiscoveryFilter(minYear=2000, maxYear=1990) }
        assertThrows(IllegalArgumentException::class.java) { DiscoveryFilter(minRating=8.0, maxRating=5.0) }
    }
}
