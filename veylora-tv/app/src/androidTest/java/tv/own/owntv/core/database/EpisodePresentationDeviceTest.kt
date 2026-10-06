package tv.own.owntv.core.database

import androidx.room.useReaderConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.discovery.localizedContentTitle

@RunWith(AndroidJUnit4::class)
class EpisodePresentationDeviceTest {
    @Test fun cachedProviderEpisodeUsesArabicMetadataWithoutStartingPlayback() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("catalogAudit") == "true")
        val koin = org.koin.core.context.GlobalContext.get()
        val settings = koin.get<SettingsRepository>()
        val config = settings.metadataConfig()
        assertTrue(config.enabled)
        assertEquals("ar", java.util.Locale.forLanguageTag(config.resolvedLanguage).language)
        val db = koin.get<OwnTVDatabase>()
        val showId = db.useReaderConnection { c -> c.usePrepared("SELECT id FROM series WHERE name LIKE '%Citadel%Diana%' LIMIT 1") { s ->
            check(s.step()) { "Pilot series missing" }; s.getLong(0)
        } }
        val show = db.seriesDao().getSeriesById(showId)!!
        val episode = db.seriesDao().episodesBySeries(showId).first().first { it.seasonNumber == 1 && it.episodeNumber == 2 }
        val metadata = koin.get<MetadataRepository>()
        val row = metadata.resolveEpisode(show, episode)
        assertNotNull(row)
        assertEquals("الحرب", localizedContentTitle(episode.name, row, config))
        assertEquals(episode.name, localizedContentTitle(episode.name, row,
            config.copy(mode=tv.own.owntv.core.metadata.MetadataMode.PROVIDER)))
    }
}
