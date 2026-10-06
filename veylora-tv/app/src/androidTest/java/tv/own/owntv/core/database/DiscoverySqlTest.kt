package tv.own.owntv.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import tv.own.owntv.core.catalog.*

/** Execute the production generated queries on the TV's SQLite, without network or user data. */
@RunWith(AndroidJUnit4::class)
class DiscoverySqlTest {
    private lateinit var db: SQLiteDatabase
    @Before fun setup() {
        db = SQLiteDatabase.create(null)
        db.execSQL("CREATE TABLE movies(id INTEGER PRIMARY KEY, sourceId INTEGER, categoryId INTEGER, remoteId TEXT, name TEXT, year INTEGER, parsedYear INTEGER, rating REAL, addedAt INTEGER, sortOrder INTEGER)")
        db.execSQL("CREATE TABLE metadata_match(localKey TEXT PRIMARY KEY, tmdbId INTEGER)")
        db.execSQL("CREATE TABLE metadata_cache(key TEXT PRIMARY KEY, year INTEGER, rating REAL, genresJson TEXT, tmdbId INTEGER, type TEXT, updatedAt INTEGER, title TEXT, overview TEXT)")
        db.execSQL("CREATE TABLE watch_history(profileId INTEGER, mediaType TEXT, itemId INTEGER)")
        db.execSQL("CREATE TABLE trending_items(sourceId INTEGER, providerItemId INTEGER, mediaType TEXT, trendingRank INTEGER)")
        db.execSQL("CREATE TABLE favorites(profileId INTEGER, mediaType TEXT, itemId INTEGER)")
        db.execSQL("CREATE TABLE content_order(profileId INTEGER, mediaType TEXT, contextKey TEXT, itemId INTEGER, position INTEGER)")
        for (id in 1..100) db.execSQL("INSERT INTO movies VALUES(?,1,10,?,?,1995,NULL,6.0,0,?)", arrayOf<Any>(id, id.toString(), "Film $id", id))
        db.execSQL("INSERT INTO movies VALUES(101,2,20,'101','Other source',1995,NULL,6.0,0,101)")
    }
    @After fun close() = db.close()
    @Test fun alphabeticalQueryUsesDisplayedLanguageInsteadOfProviderName() {
        db.execSQL("INSERT INTO metadata_match VALUES('movie:1:1',501),('movie:1:2',502)")
        db.execSQL("INSERT INTO metadata_cache(key,tmdbId,type,title) VALUES('movie:de-DE:501',501,'movie','Zulu'),('movie:de-DE:502',502,'movie','Alpha')")
        val q = discoveryQuery(DiscoveryMedia.MOVIE, listOf(1), DiscoveryFilter(), "de-DE", itemIds=listOf(1,2))
        execute(q).use { c ->
            val titles = buildMap { while(c.moveToNext()) put(c.getLong(0),c.getString(c.getColumnIndexOrThrow("metadataTitle"))) }
            assertEquals(mapOf(1L to "Zulu", 2L to "Alpha"), titles)
        }
    }
    @Test fun alphabeticalTitleUsesUnvisitedBundleLanguageAndEnglishFallback() {
        db.execSQL("INSERT INTO metadata_match VALUES('movie:1:1',501),('movie:1:2',502),('movie:1:3',503)")
        val payload = """{"title":"English fallback","texts":[{"language":"ja","region":"JP","title":"日本語"},{"language":"pt","region":"PT","title":"Portugal"},{"language":"pt","region":"BR","title":"Brasil"}]}"""
        db.execSQL("INSERT INTO metadata_cache(key,tmdbId,type,title,overview) VALUES('bundle:movie:501',501,'bundle','base',?)", arrayOf(payload))
        db.execSQL("INSERT INTO metadata_cache(key,tmdbId,type,title) VALUES('movie:de-DE:502',502,'movie','Stale German')")
        fun titles(language: String): Map<Long,String?> {
            val q = discoveryQuery(DiscoveryMedia.MOVIE,listOf(1),DiscoveryFilter(),language,itemIds=listOf(1,2,3))
            return execute(q).use { c -> buildMap { while(c.moveToNext()) put(c.getLong(0),c.getString(c.getColumnIndexOrThrow("metadataTitle"))) } }
        }
        assertEquals("日本語", titles("ja-JP")[1])
        assertEquals("Brasil", titles("pt-BR")[1])
        assertEquals("Portugal", titles("pt-PT")[1])
        assertEquals("English fallback", titles("ar")[1])
        assertNull(titles("ar")[2])
        assertNull(titles("ar")[3])
    }
    @Test fun providerListingDoesNotReadMetadataTables() {
        db.execSQL("DROP TABLE metadata_cache")
        db.execSQL("DROP TABLE metadata_match")
        val q = discoveryQuery(DiscoveryMedia.MOVIE, listOf(1), DiscoveryFilter(), "tr",
            metadataMode = tv.own.owntv.core.metadata.MetadataMode.PROVIDER)
        execute(q).use { assertEquals(100, it.count) }
    }
    @Test fun languageChangeRetainsFiltersAndPrefersLocalizedDetails() {
        db.execSQL("INSERT INTO metadata_match VALUES('movie:1:1',510)")
        db.execSQL("INSERT INTO metadata_cache(key,year,rating,genresJson,tmdbId,type,updatedAt) VALUES('movie:tr-TR:510',1975,8.4,'[\"Dram\"]',510,'movie',100)")
        val fallback = discoveryQuery(DiscoveryMedia.MOVIE, listOf(1), DiscoveryFilter(genre="Dram"), "de-DE")
        execute(fallback).use { assertEquals(1, it.count) }
        db.execSQL("INSERT INTO metadata_cache(key,year,rating,genresJson,tmdbId,type,updatedAt) VALUES('movie:de-DE:510',1975,8.4,'[\"Drama\"]',510,'movie',50)")
        execute(discoveryQuery(DiscoveryMedia.MOVIE, listOf(1), DiscoveryFilter(genre="Drama"), "de-DE")).use { assertEquals(1, it.count) }
        // A saved filter in the previous language must still select the same identity.
        execute(fallback).use { assertEquals(1, it.count) }
    }
    private fun execute(query: DiscoveryQuery, tail: String = "") = db.rawQueryWithFactory({ _, driver, table, statement ->
        query.arguments.forEachIndexed { index, value ->
            when (value) {
                is Double -> statement.bindDouble(index + 1, value)
                is Number -> statement.bindLong(index + 1, value.toLong())
                else -> statement.bindString(index + 1, value.toString())
            }
        }
        android.database.sqlite.SQLiteCursor(driver, table, statement)
    }, query.sql + tail, emptyArray(), "")
    private fun rows(order: DiscoveryOrder = DiscoveryOrder.TITLE, seed: Long = 1, filter: DiscoveryFilter = DiscoveryFilter(), profile: Long = 7, tail: String = "", excludedItemKeys: Set<String> = emptySet()): List<Long> {
        val query = discoveryQuery(DiscoveryMedia.MOVIE, listOf(1), filter, "", order=order, randomSeed=seed, profileId=profile,
            excludedItemKeys=excludedItemKeys)
        return execute(query, tail).use { cursor ->
            buildList { while(cursor.moveToNext()) add(cursor.getLong(cursor.getColumnIndexOrThrow("id"))) }
        }
    }
    @Test fun randomPagesAreDisjointStableAndReshuffle() {
        val seed = 918271L
        val all = rows(DiscoveryOrder.RANDOM, seed)
        assertEquals(100, all.distinct().size)
        assertEquals(all, rows(DiscoveryOrder.RANDOM, seed))
        assertNotEquals(all, rows(DiscoveryOrder.RANDOM, 721981921))
        assertEquals(all.take(20), rows(DiscoveryOrder.RANDOM, seed, tail=" LIMIT 20 OFFSET 0"))
        assertEquals(all.drop(20).take(20), rows(DiscoveryOrder.RANDOM, seed, tail=" LIMIT 20 OFFSET 20"))
        assertFalse(all.contains(101))
        assertNotEquals(rows(DiscoveryOrder.RANDOM, 1), rows(DiscoveryOrder.RANDOM, 2))
    }
    @Test fun randomOrderShufflesOnlyTheFilteredCatalogAndKeepsPagesStable() {
        db.execSQL("INSERT INTO movies VALUES(201,1,10,'201','Eligible drama',1998,NULL,7.5,0,201)")
        db.execSQL("INSERT INTO movies VALUES(202,1,10,'202','Eligible action',2002,NULL,8.0,0,202)")
        db.execSQL("INSERT INTO movies VALUES(203,1,10,'203','Too early',1997,NULL,9.0,0,203)")
        db.execSQL("INSERT INTO movies VALUES(204,1,10,'204','Rating too low',2000,NULL,6.9,0,204)")
        db.execSQL("INSERT INTO movies VALUES(205,1,10,'205','Too recent',2003,NULL,8.0,0,205)")
        db.execSQL("INSERT INTO movies VALUES(206,2,20,'206','Other source',2000,NULL,8.0,0,206)")
        db.execSQL("INSERT INTO metadata_match VALUES('movie:1:201',9001),('movie:1:202',9002)")
        db.execSQL("INSERT INTO metadata_cache(key,year,rating,genresJson) VALUES('movie:9001',1998,7.5,'[\"Drama\"]'),('movie:9002',2002,8.0,'[\"Action\"]')")

        val filter = DiscoveryFilter(minYear = 1998, maxYear = 2002, minRating = 7.0, maxRating = 8.5)
        val shuffled = rows(DiscoveryOrder.RANDOM, seed = 4711, filter = filter)
        assertEquals(setOf(201L, 202L), shuffled.toSet())
        assertEquals(shuffled, rows(DiscoveryOrder.RANDOM, seed = 4711, filter = filter))
        assertEquals(shuffled.take(1), rows(DiscoveryOrder.RANDOM, seed = 4711, filter = filter, tail = " LIMIT 1 OFFSET 0"))
        assertEquals(shuffled.drop(1), rows(DiscoveryOrder.RANDOM, seed = 4711, filter = filter, tail = " LIMIT 1 OFFSET 1"))

        val genreFiltered = rows(DiscoveryOrder.RANDOM, seed = 4711, filter = filter.copy(genre = "Drama"))
        assertEquals(listOf(201L), genreFiltered)
    }
    @Test fun randomizedCatalogHonorsItemsHiddenInCustomize() {
        val hidden = setOf("1:20", "1:33")
        val result = rows(DiscoveryOrder.RANDOM, seed = 77123, excludedItemKeys = hidden)
        assertEquals(98, result.size)
        assertTrue(hidden.none { key -> key.substringAfter(':').toLong() in result })
        assertEquals(result.take(12), rows(DiscoveryOrder.RANDOM, seed = 77123, tail = " LIMIT 12 OFFSET 0", excludedItemKeys = hidden))
    }
    @Test fun favoriteManualOrderSurvivesFiltering() {
        db.execSQL("INSERT INTO favorites VALUES(7,'MOVIE',20),(7,'MOVIE',30)")
        db.execSQL("INSERT INTO content_order VALUES(7,'MOVIE','fav',30,0),(7,'MOVIE','fav',20,1)")
        val query = discoveryQuery(DiscoveryMedia.MOVIE, listOf(1), DiscoveryFilter(minRating=5.0), "",
            order=DiscoveryOrder.PROVIDER, profileId=7, scope=DiscoveryScope.Favorites,
            manualOrder=DiscoveryManualOrder(7,"fav"))
        val ids = execute(query).use { c ->
            buildList { while(c.moveToNext()) add(c.getLong(c.getColumnIndexOrThrow("id"))) }
        }
        assertEquals(listOf(30L,20L), ids)
    }
    @Test fun watchedStateDoesNotLeakBetweenProfilesOrMediaTypes() {
        db.execSQL("INSERT INTO watch_history VALUES(7,'MOVIE',20),(8,'MOVIE',30),(7,'SERIES',40)")
        assertEquals(listOf(20L), rows(filter=DiscoveryFilter(watched=true)))
        assertEquals(99, rows(filter=DiscoveryFilter(watched=false)).size)
        assertEquals(listOf(30L), rows(filter=DiscoveryFilter(watched=true), profile=8))
        assertTrue(rows(filter=DiscoveryFilter(minRating=7.0)).isEmpty())
    }
    @Test fun popularLeadsThenRemainingCatalogUsesProviderOrder() {
        db.execSQL("INSERT INTO trending_items VALUES(1,20,'MOVIE',2),(1,30,'MOVIE',1),(2,40,'MOVIE',0),(1,50,'SERIES',0)")
        assertEquals(listOf(30L,20L) + (1L..100L).filter { it != 30L && it != 20L }, rows(DiscoveryOrder.POPULAR))
        val count = discoveryQuery(DiscoveryMedia.MOVIE, listOf(1), DiscoveryFilter(), "",
            order = DiscoveryOrder.POPULAR, countOnly = true)
        execute(count).use { c -> c.moveToFirst(); assertEquals(100, c.getInt(0)) }
        assertTrue(rows(DiscoveryOrder.POPULAR, filter = DiscoveryFilter(minYear = 2000)).isEmpty())
    }
    @Test fun popularSearchFindsNonPopularItems() {
        db.execSQL("INSERT INTO trending_items VALUES(1,20,'MOVIE',0)")
        val query = discoveryQuery(DiscoveryMedia.MOVIE, listOf(1), DiscoveryFilter(), "",
            search = "Film 99", order = DiscoveryOrder.POPULAR)
        execute(query).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(99L, c.getLong(c.getColumnIndexOrThrow("id")))
            assertFalse(c.moveToNext())
        }
    }
}
