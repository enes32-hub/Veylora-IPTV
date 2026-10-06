package tv.own.owntv.core.database

import android.os.Bundle
import androidx.room.useReaderConnection
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.catalog.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.settings.SettingsRepository

/** Explicit opt-in, aggregate-only check. Never exports source URLs, tokens or profile data. */
class InstalledCatalogDiagnosticTest {
    @Test fun genreCoverageWhenExplicitlyRequested() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("genreAudit") == "true")
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsRepository>()
        val config = settings.metadataConfig()
        val profile = settings.activeProfileIdNow()
        val sources = koin.get<tv.own.owntv.core.database.dao.SourceDao>().observeForProfile(profile).first().filter { it.syncMovies }.map { it.id }
        val filter = settings.discoveryFilter(profile, DiscoveryMedia.MOVIE).first()
        val out = StringBuilder("filter=$filter; language=${config.resolvedLanguage}; mode=${config.mode}\n")
        koin.get<OwnTVDatabase>().useReaderConnection { connection ->
            suspend fun read(label: String, query: DiscoveryQuery) {
                connection.usePrepared(query.sql) { c ->
                    query.arguments.forEachIndexed { i, arg -> when(arg) {
                        is Double -> c.bindDouble(i+1,arg)
                        is Number -> c.bindLong(i+1,arg.toLong())
                        else -> c.bindText(i+1,arg.toString())
                    } }
                    c.step(); out.append(label).append('=').append(c.getLong(0)).append('\n')
                }
            }
            val base = discoveryQuery(DiscoveryMedia.MOVIE,sources,DiscoveryFilter(),config.resolvedLanguage,countOnly=true,metadataMode=config.mode)
            read("catalogTotal",base)
            read("withMatchingTmdbId",base.copy(sql=base.sql.replace("COUNT(*)","COUNT(match.tmdbId)")))
            read("withGenresForSelectedLanguage",base.copy(sql=base.sql.replace("COUNT(*)","SUM(CASE WHEN meta.genresJson IS NOT NULL AND meta.genresJson NOT IN ('','[]') THEN 1 ELSE 0 END)")))
            read("activeFilterCount",discoveryQuery(DiscoveryMedia.MOVIE,sources,filter,config.resolvedLanguage,countOnly=true,metadataMode=config.mode,profileId=profile))
            val sourceSql = sources.joinToString(",")
            connection.usePrepared("SELECT name,year FROM movies WHERE sourceId IN ($sourceSql) AND (lower(name) LIKE '%conjuring%' OR lower(name) LIKE '%siccin%' OR lower(name) LIKE '%scream%' OR lower(name) LIKE '%korku%' OR lower(name) LIKE '%ruhlar%' OR lower(name) LIKE '%testere%') ORDER BY name LIMIT 20") { c ->
                while(c.step()) out.append("catalogExample=").append(c.getText(0)).append(" year=").append(if(c.isNull(1)) "?" else c.getLong(1)).append('\n')
            }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {putString("stream",out.toString())})
    }
    @Test fun refreshInstalledMovieCatalogWhenExplicitlyRequested() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("refreshCatalog") == "true")
        val koin = GlobalContext.get()
        val db = koin.get<OwnTVDatabase>()
        val sourceId = db.useReaderConnection { c -> c.usePrepared("SELECT sourceId FROM movies LIMIT 1") { it.step(); it.getLong(0) } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = tv.own.owntv.core.sync.work.CatalogSyncScheduler(context)
        val wm = androidx.work.WorkManager.getInstance(context)
        val workName = tv.own.owntv.core.sync.work.CatalogSyncScheduler.workName(sourceId)
        val previousIds = wm.getWorkInfosForUniqueWork(workName).get().map { it.id }.toSet()
        scheduler.enqueueSync(sourceId, contentTypes = tv.own.owntv.core.sync.SyncContentTypes(live=false,movies=true,series=false))
        kotlinx.coroutines.withTimeout(120_000) {
            kotlinx.coroutines.delay(1000)
            while(true) {
                val info = wm.getWorkInfosForUniqueWork(workName).get().firstOrNull { it.id !in previousIds }
                if (info != null && info.state.isFinished) {
                    org.junit.Assert.assertEquals(androidx.work.WorkInfo.State.SUCCEEDED, info.state)
                    break
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    @Test fun installedCatalogCounts() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveCatalog") == "true")
        val koin = GlobalContext.get()
        val db = koin.get<OwnTVDatabase>()
        val config = koin.get<SettingsRepository>().metadataConfig()
        val output = StringBuilder("metadataMode=${config.mode}; language=${config.resolvedLanguage}\n")
        for (table in listOf("movies", "series", "trending_items", "metadata_match", "metadata_cache")) {
            db.useReaderConnection { connection ->
                connection.usePrepared("SELECT COUNT(*) FROM $table") { cursor ->
                    cursor.step()
                    output.append(table).append('=').append(cursor.getLong(0)).append('\n')
                }
            }
        }
        val settings = koin.get<SettingsRepository>()
        output.append("ownKeyConfigured=").append(config.tmdbApiKey.isNotBlank()).append('\n')
        output.append("activeMovieFilter=").append(settings.discoveryFilter(settings.activeProfileIdNow(), DiscoveryMedia.MOVIE).first()).append('\n')
        db.useReaderConnection { connection ->
            for ((label, sql) in linkedMapOf(
                "providerYearKnown" to "SELECT COUNT(*) FROM movies WHERE year IS NOT NULL",
                "parsedYearKnown" to "SELECT COUNT(*) FROM movies WHERE parsedYear IS NOT NULL",
                "noYear" to "SELECT COUNT(*) FROM movies WHERE COALESCE(year,parsedYear) IS NULL",
                "year1998to2002" to "SELECT COUNT(*) FROM movies WHERE COALESCE(year,parsedYear) BETWEEN 1998 AND 2002",
                "year1998to2002Rating5" to "SELECT COUNT(*) FROM movies WHERE COALESCE(year,parsedYear) BETWEEN 1998 AND 2002 AND rating >= 5"
            )) {
                connection.usePrepared(sql) { cursor -> cursor.step(); output.append(label).append('=').append(cursor.getLong(0)).append('\n') }
            }
            val sources = mutableListOf<Long>()
            connection.usePrepared("SELECT DISTINCT sourceId FROM movies") { cursor -> while(cursor.step()) sources.add(cursor.getLong(0)) }
            val query = discoveryQuery(DiscoveryMedia.MOVIE, sources, DiscoveryFilter(minYear=1998,maxYear=2002), config.resolvedLanguage, countOnly=true, metadataMode=config.mode)
            connection.usePrepared(query.sql) { cursor ->
                query.arguments.forEachIndexed { index, arg -> when(arg) {
                    is Number -> cursor.bindLong(index+1,arg.toLong())
                    else -> cursor.bindText(index+1,arg.toString())
                } }
                cursor.step(); output.append("appYearQuery=").append(cursor.getLong(0)).append('\n')
            }
            connection.usePrepared("SELECT name,year,parsedYear FROM movies WHERE COALESCE(year,parsedYear) BETWEEN 1998 AND 2002 ORDER BY name LIMIT 30") { cursor ->
                while(cursor.step()) output.append("match=").append(cursor.getText(0)).append(" year=").append(if(cursor.isNull(1)) "missing" else cursor.getLong(1)).append(" parsed=").append(if(cursor.isNull(2)) "missing" else cursor.getLong(2)).append('\n')
            }
        }
        if (InstrumentationRegistry.getArguments().getString("providerAudit") == "true") {
            val sourceId = db.useReaderConnection { c -> c.usePrepared("SELECT sourceId FROM movies LIMIT 1") { it.step(); it.getLong(0) } }
            val source = requireNotNull(koin.get<tv.own.owntv.core.database.dao.SourceDao>().getById(sourceId))
            val client = koin.get<tv.own.owntv.core.parser.XtreamClient>()
            var total = 0
            var withYear = 0
            var inRange = 0
            val sample = mutableListOf<Pair<String,String>>()
            val complete = client.streamVod(source, onItem = { item ->
                total++
                if (item.year != null) withYear++
                if (item.year in 1998..2002) inRange++
                if (item.name.contains("2001") || item.name.contains("Dracula 2000") || item.name == "101 Reykjavik") sample.add(item.name to item.streamId)
            })
            output.append("providerList complete=$complete total=$total withYear=$withYear inRange=$inRange\n")
            for ((name,id) in sample.take(5)) {
                val identity = client.metadataIdentity(source,id,false)
                output.append("providerDetail=").append(name).append(" year=").append(identity.year).append(" tmdbIdPresent=").append(identity.tmdbId != null).append('\n')
                kotlinx.coroutines.delay(250)
            }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", output.toString()) })
    }
}
