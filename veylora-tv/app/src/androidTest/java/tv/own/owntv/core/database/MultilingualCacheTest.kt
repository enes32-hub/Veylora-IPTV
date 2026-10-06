package tv.own.owntv.core.database

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.metadata.MultilingualMetadata

/** Isolated SQLite only: no source credentials, network calls or changes to the user's database. */
@RunWith(AndroidJUnit4::class)
class MultilingualCacheTest {
    @Test fun installedLanguageSwitchUsesSameBundleAndPreservesScanMarkers() = runBlocking {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("localeAudit") == "true")
        val koin = org.koin.core.context.GlobalContext.get()
        val settings = koin.get<tv.own.owntv.core.settings.SettingsRepository>()
        val locale = koin.get<tv.own.owntv.core.i18n.LocaleStore>()
        val repo = koin.get<tv.own.owntv.core.metadata.MetadataRepository>()
        val db = koin.get<OwnTVDatabase>()
        val previousTag = locale.currentTag.value
        val previousMetadataLanguage = settings.metadataLanguage.first()
        val original = db.metadataDao().getCache("bundle:movie:674")!!
        assertTrue(MultilingualMetadata.isComplete(original))
        suspend fun markers(): Long = db.useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM metadata_match WHERE localKey LIKE 'scan:v5:%'") { c -> c.step(); c.getLong(0) }
        }
        val before = markers()
        try {
            settings.setMetadataLanguage("auto")
            for (tag in listOf("de", "ar", "tr", "en-US")) {
                locale.set(tag)
                val config = withTimeout(5000) { settings.metadataConfigFlow.first { it.language == "auto" && java.util.Locale.forLanguageTag(it.resolvedLanguage).language == java.util.Locale.forLanguageTag(tag).language } }
                val actual = repo.cachedDetails(674, tv.own.owntv.core.metadata.MetadataType.MOVIE, allowNetwork = false)!!
                val expected = MultilingualMetadata.restore(original, config.resolvedLanguage)!!
                assertEquals(expected.title, actual.title)
                assertEquals(expected.overview, actual.overview)
                assertEquals(expected.posterPath, actual.posterPath)
                if (tag == "ar") {
                    assertFalse(actual.overview.isNullOrBlank())
                    assertNotEquals(MultilingualMetadata.restore(original,"de")!!.overview, actual.overview)
                }
            }
            assertEquals(original.overview, db.metadataDao().getCache(original.key)!!.overview)
            assertEquals(before, markers())
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                putString("stream", "Locale audit: de/ar/tr/en use the same movie 674 bundle without network; scan markers unchanged.\n")
            })
        } finally {
            locale.set(previousTag)
            settings.setMetadataLanguage(previousMetadataLanguage)
        }
    }
    @Test fun failedArtworkRepairKeepsTextAndConcurrentRetriesShareOneRequest() = runBlocking {
        val koin = org.koin.core.context.GlobalContext.get()
        val settings = koin.get<tv.own.owntv.core.settings.SettingsRepository>()
        org.junit.Assume.assumeTrue(settings.metadataConfig().enabled)
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OwnTVDatabase::class.java).build()
        try {
            var requests = 0
            var offline = true
            val provider = object : tv.own.owntv.core.metadata.MetadataProvider by koin.get<tv.own.owntv.core.metadata.MetadataProvider>() {
                override suspend fun allImages(tmdbId: Int, type: tv.own.owntv.core.metadata.MetadataType): JSONObject? {
                    requests++
                    kotlinx.coroutines.delay(30)
                    return if (offline) null else JSONObject("""{"posters":[{"iso_639_1":"de","file_path":"/de.jpg"}],"backdrops":[],"logos":[]}""")
                }
                override suspend fun identityDetails(tmdbId: Int, type: tv.own.owntv.core.metadata.MetadataType): tv.own.owntv.core.metadata.IdentityDetails =
                    error("Known complete identity must not trigger full detail lookup")
                override suspend fun movieDetails(tmdbId: Int): tv.own.owntv.core.metadata.MovieDetails? = error("Duplicate full detail request")
            }
            val repo = tv.own.owntv.core.metadata.MetadataRepository(provider, db.metadataDao(), settings,
                koin.get(), db.profileDao(), tv.own.owntv.core.metadata.ProviderIdentityLookup(db.sourceDao(), koin.get()))
            val base = MetadataCacheEntity("movie:en:42",42,null,"movie","English title",1975,"English plot",
                "/en.jpg",null,8.0,"[]","[]",null,null,System.currentTimeMillis())
            val payload = MultilingualMetadata.pack(JSONObject("""{"title":"English title","overview":"English plot",
                "translations":{"translations":[]},"images":{"posters":[],"backdrops":[],"logos":[]}}"""))!!
            db.metadataDao().upsertCache(MultilingualMetadata.stored(base,payload))
            val movies = (1L..2L).map { tv.own.owntv.core.database.entity.MovieEntity(id=it, sourceId=it, name="Film", streamUrl="", remoteId="42") }
            for (movie in movies) db.metadataDao().upsertMatch(tv.own.owntv.core.database.entity.MetadataMatchEntity(
                tv.own.owntv.core.metadata.MetadataRepository.movieLocalKey(movie),"movie",42,1.0,System.currentTimeMillis()))
            assertFalse(repo.scanMovie(movies.first()))
            assertEquals(1,requests)
            assertEquals("English title",repo.cachedDetails(42,tv.own.owntv.core.metadata.MetadataType.MOVIE,false)!!.title)
            assertEquals(1,requests)
            offline = false
            assertTrue(movies.map { async { repo.scanMovie(it) } }.awaitAll().all { it })
            assertEquals(2,requests)
            assertTrue(MultilingualMetadata.isComplete(db.metadataDao().getCache("bundle:movie:42")!!))
        } finally { db.close() }
    }
    @Test fun installedBundleAuditWhenExplicitlyRequested() = runBlocking {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("bundleAudit") == "true")
        val db = org.koin.core.context.GlobalContext.get().get<OwnTVDatabase>()
        val rows = db.metadataDao().getCaches(listOf("bundle:movie:550","bundle:movie:510","bundle:movie:671"))
        val result = StringBuilder()
        db.useReaderConnection { connection ->
            for ((label,query) in listOf(
                "completed" to "SELECT COUNT(*) FROM metadata_match WHERE localKey LIKE 'scan:v5:%'",
                "bundles" to "SELECT COUNT(*) FROM metadata_cache WHERE type='bundle'",
                "catalog" to "SELECT (SELECT COUNT(*) FROM movies)+(SELECT COUNT(*) FROM series)"
            )) connection.usePrepared(query) { c -> c.step(); result.append(label).append('=').append(c.getLong(0)).append('\n') }
        }
        for (row in rows) {
            val tr = MultilingualMetadata.restore(row,"tr-TR")!!
            val en = MultilingualMetadata.restore(row,"en-US")!!
            result.append("sampleId=").append(row.tmdbId).append(" tr=").append(tr.title).append(" en=").append(en.title).append('\n')
            val de = MultilingualMetadata.restore(row,"de-DE")!!
            result.append("complete=").append(MultilingualMetadata.isComplete(row))
                .append(" dePoster=").append(de.posterPath).append(" deBackdrop=").append(de.backdropPath)
                .append(" deLogo=").append(de.logoPath).append('\n')
            assertEquals(row.tmdbId,tr.tmdbId)
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply { putString("stream",result.toString()) })
    }

    @Test fun liveArtworkRepairByIdentityWhenExplicitlyRequested() = runBlocking {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("repairArtwork") == "true")
        val koin = org.koin.core.context.GlobalContext.get()
        val repo = koin.get<tv.own.owntv.core.metadata.MetadataRepository>()
        val db = koin.get<OwnTVDatabase>()
        // Only existing metadata is repaired; no playback, source changes or account exports.
        repo.cachedDetails(671, tv.own.owntv.core.metadata.MetadataType.MOVIE, allowNetwork = true)
        val saved = db.metadataDao().getCache("bundle:movie:671")!!
        assertTrue(MultilingualMetadata.isComplete(saved))
        val payload = JSONObject(saved.overview!!)
        val de = MultilingualMetadata.restore(saved,"de-DE")!!
        assertEquals(payload.getJSONObject("posters").getString("de"), de.posterPath)
        assertEquals(payload.getJSONObject("backdrops").getString("de"), de.backdropPath)
        assertEquals(payload.getJSONObject("logos").getString("de"), de.logoPath)
        assertNotEquals(de.posterPath, MultilingualMetadata.restore(saved,"en-US")!!.posterPath)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {
            putString("stream", "Harry Potter 671: German poster/backdrop/logo selected from complete bundle; English distinct.\n")
        })
    }
    @Test fun savedBundleProjectsDifferentLanguagesWithoutAdditionalRequests() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OwnTVDatabase::class.java).build()
        try {
            val base = MetadataCacheEntity("movie:en:42",42,null,"movie","English title",1975,"English plot",
                "/en.jpg",null,8.0,"[\"Drama\"]","[]",null,null,1L)
            val payload = MultilingualMetadata.pack(JSONObject("""{
              "title":"English title","overview":"English plot",
              "translations":{"translations":[{"iso_639_1":"tr","iso_3166_1":"TR","data":{"title":"Türkçe ad"}}]},
              "images":{"posters":[{"iso_639_1":"en","file_path":"/en.jpg"},{"iso_639_1":"tr","file_path":"/tr.jpg"}]}}
            """))!!
            val dao = db.metadataDao()
            dao.upsertCache(MultilingualMetadata.stored(base,payload))
            dao.evictCacheOlderThan(Long.MAX_VALUE)
            val saved = dao.getCache("bundle:movie:42")!!
            val turkish = MultilingualMetadata.restore(saved,"tr-TR")!!
            val german = MultilingualMetadata.restore(saved,"de-DE")!!
            dao.upsertCaches(listOf(turkish,german))
            assertEquals("Türkçe ad",dao.getCache("movie:tr-TR:42")!!.title)
            assertEquals("English plot",dao.getCache("movie:tr-TR:42")!!.overview)
            assertEquals("/tr.jpg",dao.getCache("movie:tr-TR:42")!!.posterPath)
            assertEquals("English title",dao.getCache("movie:de-DE:42")!!.title)
            assertEquals("/en.jpg",dao.getCache("movie:de-DE:42")!!.posterPath)
            assertNull(dao.getCache("bundle:tv:42"))
        } finally { db.close() }
    }
}
