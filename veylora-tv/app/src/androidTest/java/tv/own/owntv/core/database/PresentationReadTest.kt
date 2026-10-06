package tv.own.owntv.core.database

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.dao.MetadataDao
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.metadata.*

class PresentationReadTest {
    @Test fun screenshotSeriesSourceAudit() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("presentationAudit") == "true")
        val koin = org.koin.core.context.GlobalContext.get()
        val db = koin.get<OwnTVDatabase>()
        val ids = db.useReaderConnection { connection ->
            connection.usePrepared("SELECT id FROM series WHERE name LIKE '%Maymun%' OR name LIKE '%Bin Ad%' LIMIT 10") { c ->
                buildList { while(c.step()) add(c.getLong(0)) }
            }
        }
        val out = StringBuilder()
        for (id in ids) {
            val show = db.seriesDao().getSeriesById(id) ?: continue
            val match = db.metadataDao().getMatch(MetadataRepository.seriesLocalKey(show))
            val bundle = match?.tmdbId?.let { db.metadataDao().getCache(MultilingualMetadata.key("tv",it)) }
            out.append("provider=").append(show.name).append(" tmdb=").append(match?.tmdbId)
            for (lang in listOf("ar","en")) out.append(" ").append(lang).append('=').append(bundle?.let { MultilingualMetadata.restore(it,lang)?.title })
            out.append('\n')
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply { putString("stream",out.toString()) })
    }
    @Test fun displayingSavedBundleDoesNotInvalidateCatalog() = runBlocking {
        val koin = org.koin.core.context.GlobalContext.get()
        val settings = koin.get<tv.own.owntv.core.settings.SettingsRepository>()
        org.junit.Assume.assumeTrue(settings.metadataConfig().enabled)
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OwnTVDatabase::class.java).build()
        try {
            val base = MetadataCacheEntity("movie:en:42",42,null,"movie","English title",2001,"English plot",null,null,8.0,"[]","[]",null,null,1L)
            val payload = """{"version":2,"title":"English title","overview":"English plot","texts":[],"posters":{"en":"/poster.jpg"},"backdrops":{},"logos":{"en":"/logo.png"}}"""
            db.metadataDao().upsertCache(MultilingualMetadata.stored(base, payload))
            val movie = MovieEntity(id=1, sourceId=1, name="Provider title", streamUrl="", remoteId="42")
            db.metadataDao().upsertMatch(MetadataMatchEntity(MetadataRepository.movieLocalKey(movie),"movie",42,1.0,1L))
            var writes = 0
            val dao = object : MetadataDao by db.metadataDao() {
                override suspend fun upsertCaches(entities: List<MetadataCacheEntity>) { writes++; db.metadataDao().upsertCaches(entities) }
                override suspend fun upsertCache(entity: MetadataCacheEntity) { writes++; db.metadataDao().upsertCache(entity) }
            }
            val repo = MetadataRepository(koin.get(), dao, settings, koin.get(), db.profileDao(), ProviderIdentityLookup(db.sourceDao(), koin.get()))
            repeat(2) {
                val actual = repo.cachedMoviePresentation(listOf(movie))[1L]!!
                assertEquals("English title", actual.title)
                assertEquals("/logo.png", actual.logoPath)
                assertEquals("/logo.png", repo.cachedDetails(42, MetadataType.MOVIE, allowNetwork=false)!!.logoPath)
            }
            assertEquals("Reading a saved translation must not restart Room paging", 0, writes)
        } finally { db.close() }
    }

    @Test fun installedBundleCoverage() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("presentationAudit") == "true")
        val db = org.koin.core.context.GlobalContext.get().get<OwnTVDatabase>()
        val out = StringBuilder()
        db.useReaderConnection { connection ->
            for ((label, sql) in listOf(
                "movies" to "SELECT COUNT(*) FROM movies",
                "series" to "SELECT COUNT(*) FROM series",
                "completedScans" to "SELECT COUNT(*) FROM metadata_match WHERE localKey LIKE 'scan:v5:%'",
                "bundles" to "SELECT COUNT(*) FROM metadata_cache WHERE type='bundle'",
                "completeBundles" to "SELECT COUNT(*) FROM metadata_cache WHERE type='bundle' AND json_extract(overview,'$.version')=2",
                "arabicTextBundles" to "SELECT COUNT(*) FROM metadata_cache b WHERE b.type='bundle' AND EXISTS (SELECT 1 FROM json_each(b.overview,'$.texts') t WHERE json_extract(t.value,'$.language')='ar')",
                "englishLogoBundles" to "SELECT COUNT(*) FROM metadata_cache WHERE type='bundle' AND json_extract(overview,'$.logos.en') IS NOT NULL"
            )) connection.usePrepared(sql) { c -> c.step(); out.append(label).append('=').append(c.getLong(0)).append('\n') }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", out.toString()) })
    }
}
