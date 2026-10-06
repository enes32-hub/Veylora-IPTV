package tv.own.owntv.features.discovery

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.MetadataCacheEntity

class LocalizedPresentationTest {
    @Test fun episodeAndRecentTitleRejectPreviousLanguageAndRespectProviderMode() {
        val episode = german.copy(key="tv:ar:214032:s1e2", type="episode", tmdbId=214032, title="الحرب")
        val config = tv.own.owntv.core.metadata.MetadataConfig(language="ar")
        assertEquals("الحرب", localizedContentTitle("Savaş", episode, config))
        assertEquals("Savaş", localizedContentTitle("Savaş", episode.copy(key="tv:tr-TR:214032:s1e2"),config))
        assertEquals("Savaş", localizedContentTitle("Savaş", episode, config.copy(mode=tv.own.owntv.core.metadata.MetadataMode.PROVIDER)))
        assertEquals("War", localizedContentTitle("Savaş", episode.copy(title="War"),config))
        assertEquals("Provider", localizedContentTitle("Provider", null, config))
        assertEquals("Deutsch", localizedContentTitle("Provider", german,config.copy(language="de-DE")))
    }
    @Test fun pendingLanguageDoesNotFlashProviderButConfirmedMissUsesIt() {
        val snapshot = LocalizedPresentation("de-DE", mapOf(7L to german), setOf(7L, 8L))
        assertEquals("Deutsch", snapshot.title(7, "de-DE", true, "Provider"))
        assertEquals("Provider", snapshot.title(8, "de-DE", true, "Provider"))
        assertEquals("", snapshot.title(9, "de-DE", true, "Provider"))
        assertEquals("", snapshot.title(7, "ar", true, "Provider"))
        assertEquals("Provider", snapshot.title(7, "ar", false, "Provider"))
    }
    private val german = MetadataCacheEntity("movie:de-DE:671",671,null,"movie","Deutsch",2001,
        "German plot","/de.jpg",null,8.0,"[]","[]",null,null,1L)
    @Test fun lateGermanSnapshotIsNeverShownInArabic() {
        val snapshot = LocalizedPresentation("de-DE", mapOf(7L to german))
        assertEquals(1, snapshot.forLanguage("de-DE").size)
        assertTrue(snapshot.forLanguage("ar").isEmpty())
        assertTrue(german.inLanguage("de-DE"))
        assertFalse(german.inLanguage("ar"))
    }
}
