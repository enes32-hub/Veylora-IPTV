package tv.own.owntv.core.database

import androidx.room.useReaderConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.catalog.*
import tv.own.owntv.core.metadata.MetadataMode

/** Opt-in read-only diagnosis of the installed catalogue; never prints URLs or credentials. */
@RunWith(AndroidJUnit4::class)
class CatalogLoadingAuditTest {
    @Test fun launcherPublishingIsDisabledAndExistingAppRowsAreRemoved() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("catalogAudit") == "true")
        val koin = org.koin.core.context.GlobalContext.get()
        org.junit.Assert.assertFalse(tv.own.owntv.core.CoreBuildInfo.tvHome)
        koin.get<tv.own.owntv.core.tv.TvHomeRepository>().refreshProfile(-1)
        val db = koin.get<OwnTVDatabase>()
        db.useReaderConnection { conn ->
            conn.usePrepared("SELECT COUNT(*) FROM tv_provider_programs") { s ->
                org.junit.Assert.assertTrue(s.step())
                org.junit.Assert.assertEquals(0L, s.getLong(0))
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tables = listOf(androidx.tvprovider.media.tv.TvContractCompat.Channels.CONTENT_URI,
            androidx.tvprovider.media.tv.TvContractCompat.PreviewPrograms.CONTENT_URI,
            androidx.tvprovider.media.tv.TvContractCompat.WatchNextPrograms.CONTENT_URI)
        for (uri in tables) {
            context.contentResolver.query(uri, arrayOf("package_name"), null, null, null)?.use { c ->
                var own = 0
                while (c.moveToNext()) if (c.getString(0) == context.packageName) own++
                org.junit.Assert.assertEquals("App-owned launcher rows at $uri", 0, own)
            }
        }
        report("Launcher: publication disabled; app-owned channels, preview and Watch Next rows absent.")
    }
    @Test fun explainInstalledCatalogueQueries() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("catalogAudit") == "true")
        val db = org.koin.core.context.GlobalContext.get().get<OwnTVDatabase>()
        db.useReaderConnection { conn ->
            val ids = conn.usePrepared("SELECT DISTINCT sourceId FROM movies") { s ->
                buildList { while (s.step()) add(s.getLong(0)) }
            }
            for (mode in MetadataMode.selectable) {
                val q = discoveryQuery(DiscoveryMedia.MOVIE, ids, DiscoveryFilter(), "tr",
                    order = DiscoveryOrder.RANDOM, countOnly = true, metadataMode = mode)
                conn.usePrepared("EXPLAIN QUERY PLAN " + q.sql) { s ->
                    q.arguments.forEachIndexed { i, a ->
                        when (a) {
                            is Number -> s.bindLong(i + 1, a.toLong())
                            else -> s.bindText(i + 1, a.toString())
                        }
                    }
                    while (s.step()) report("$mode: ${s.getText(3)}")
                }
                val start = android.os.SystemClock.elapsedRealtime()
                conn.usePrepared(q.sql) { s ->
                    q.arguments.forEachIndexed { i, a ->
                        if (a is Number) s.bindLong(i + 1, a.toLong()) else s.bindText(i + 1, a.toString())
                    }
                    org.junit.Assert.assertTrue(s.step())
                    org.junit.Assert.assertTrue(s.getLong(0) > 0)
                    report("$mode count=${s.getLong(0)} elapsedMs=${android.os.SystemClock.elapsedRealtime() - start}")
                }
                org.junit.Assert.assertTrue(android.os.SystemClock.elapsedRealtime() - start < 5000)
            }
        }
    }
    private fun report(text: String) = InstrumentationRegistry.getInstrumentation().sendStatus(0,
        android.os.Bundle().apply { putString("stream", text + "\n") })
}
