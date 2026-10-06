package tv.own.owntv.core.catalog

import org.junit.Assert.*
import org.junit.Test

class DiscoveryQueryTest {
    @Test fun `custom category remains profile scoped with filters and sort`() {
        val query = discoveryQuery(DiscoveryMedia.SERIES, listOf(2), DiscoveryFilter(minRating=5.0), "tr",
            profileId=7, scope=DiscoveryScope.Custom("private-list"), order=DiscoveryOrder.RANDOM)
        assertTrue(query.sql.contains("custom_category_members"))
        assertTrue(query.sql.contains("scope.profileId = ? AND scope.mediaType = ?"))
        assertFalse(query.sql.contains("private-list"))
        assertTrue(query.arguments.containsAll(listOf(7L, "SERIES", "private-list", 5.0)))
    }
    @Test fun `random ordering is seeded stable and does not affect count`() {
        fun query(seed: Long, count: Boolean = false) = discoveryQuery(DiscoveryMedia.MOVIE,
            listOf(2), DiscoveryFilter(), "", order=DiscoveryOrder.RANDOM, randomSeed=seed, countOnly=count)
        assertEquals(query(17), query(17))
        assertNotEquals(query(17).arguments, query(29).arguments)
        assertFalse(query(17).sql.contains("RANDOM()"))
        assertEquals(query(17, true), query(29, true))
        assertTrue(query(17).sql.endsWith("m.id ASC"))
    }
    @Test fun `popular ordering is scoped to the same source and media`() {
        val query = discoveryQuery(DiscoveryMedia.SERIES, listOf(2), DiscoveryFilter(), "",
            order=DiscoveryOrder.POPULAR)
        assertTrue(query.sql.contains("t.sourceId = m.sourceId"))
        assertTrue(query.sql.contains("t.providerItemId = m.id"))
        assertTrue(query.sql.contains("t.mediaType = ?"))
        assertFalse(query.sql.contains("EXISTS (SELECT 1 FROM trending_items"))
        assertEquals("SERIES", query.arguments.last())
        assertTrue(query.sql.contains("2147483647) ASC, m.sortOrder ASC, m.id ASC"))
        assertFalse(query.sql.contains("2147483647) ASC, m.name ASC, m.id ASC"))
    }
    @Test fun `watched filter binds profile and media and never interpolates search text`() {
        val query = discoveryQuery(DiscoveryMedia.SERIES, listOf(9), DiscoveryFilter(watched=false),
            "tr-TR", search="' OR 1=1 --", profileId=7)
        assertTrue(query.sql.contains("NOT EXISTS"))
        assertTrue(query.sql.contains("h.profileId = ? AND h.mediaType = ?"))
        assertFalse(query.sql.contains("' OR 1=1 --"))
        assertEquals(listOf("tv:tr-TR:", "tr", "TR", "tv:", "", "tv:tr-TR:", 7L, "SERIES", 9L, "' OR 1=1 --"), query.arguments)
    }
    @Test fun `count and rows share identical filter arguments`() {
        val filter = DiscoveryFilter(minYear=1990, maxYear=2000, minRating=5.0, maxRating=7.0, genre="Dram", watched=true)
        val rows = discoveryQuery(DiscoveryMedia.MOVIE, listOf(2), filter, "", categoryId=3, profileId=4)
        val count = discoveryQuery(DiscoveryMedia.MOVIE, listOf(2), filter, "", categoryId=3, profileId=4, countOnly=true)
        assertEquals(rows.arguments.drop(3), count.arguments)
        assertTrue(count.sql.startsWith("SELECT COUNT(*)"))
        val predicate = "WHERE EXISTS (SELECT 1 FROM watch_history"
        assertEquals(rows.sql.substringAfter(predicate).substringBeforeLast(" ORDER BY"), count.sql.substringAfter(predicate))
    }
    @Test fun `saved watched filters round trip and absent preferences mean no limits`() {
        val filter = DiscoveryFilter(watched=false, genre="Dram", minYear=1990, maxRating=7.0)
        assertEquals(filter, DiscoveryFilterCodec.decode(DiscoveryFilterCodec.encode(filter)))
        assertEquals(0, DiscoveryFilterCodec.decode(null).activeCount)
    }
}
