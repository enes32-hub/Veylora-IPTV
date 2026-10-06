package tv.own.owntv.core.database

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.dao.TvProviderProgramDao
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.core.metadata.MetadataBudget
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.tv.TvHomeRepository
import tv.own.owntv.core.tv.TvProviderSurface

class DailyLauncherLiveTest {
    @Test fun fourMovieCardsOpenDetailsWithoutMetadataQueries() = runBlocking {
        val koin = GlobalContext.get()
        val profile = koin.get<SettingsRepository>().activeProfileId.first()
        assertTrue(profile >= 0)
        val before = koin.get<MetadataBudget>().status().usedDay
        val repository = koin.get<TvHomeRepository>()
        repository.refreshProfile(profile)
        val dao = koin.get<TvProviderProgramDao>()
        val rows = dao.getForSurface(profile, TvProviderSurface.RECENT_LIVE).filter { it.groupId != 0L }
        assertEquals(4, rows.size)
        assertEquals(4, rows.map { it.targetItemId }.distinct().size)
        if (InstrumentationRegistry.getArguments().getString("checkArt") == "true") {
            rows.forEach { row ->
                val movie = koin.get<tv.own.owntv.core.database.dao.MovieDao>().getById(row.targetItemId)!!
                val address = movie.backdropUrl?.takeIf { it.startsWith("http") } ?: movie.posterUrl!!
                val result = runCatching {
                    koin.get<okhttp3.OkHttpClient>().newBuilder().callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                        .newCall(okhttp3.Request.Builder().url(address).build()).execute().use { response ->
                            "status=${response.code}; bytes=${response.body.bytes().size}"
                        }
                }.getOrElse { it.javaClass.simpleName }
                InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                    putString("stream", "\nartHost=${android.net.Uri.parse(address).host}; $result\n")
                })
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rows.forEach { row ->
            assertEquals(MediaType.MOVIE, row.mediaType)
            context.contentResolver.query(TvContractCompat.buildPreviewProgramUri(requireNotNull(row.providerProgramId)),
                arrayOf("intent_uri", "poster_art_uri"), null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                val intent = Intent.parseUri(cursor.getString(0), Intent.URI_INTENT_SCHEME)
                assertTrue((LauncherDeepLink.parse(intent.data) as LauncherDeepLink.Movie).detailsOnly)
                assertFalse(cursor.getString(1).isNullOrBlank())
            }
        }
        repository.refreshProfile(profile)
        assertEquals(rows.map { it.targetItemId }, dao.getForSurface(profile, TvProviderSurface.RECENT_LIVE).filter { it.groupId != 0L }.map { it.targetItemId })
        assertEquals(before, koin.get<MetadataBudget>().status().usedDay)
    }
}
