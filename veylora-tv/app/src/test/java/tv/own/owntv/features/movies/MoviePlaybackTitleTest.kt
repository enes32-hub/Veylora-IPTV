package tv.own.owntv.features.movies

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.metadata.MetadataConfig
import tv.own.owntv.core.metadata.MetadataMode

class MoviePlaybackTitleTest {
    private val arabic = MetadataConfig(language = "ar")
    private val translated = MetadataCacheEntity("movie:ar:460458", 460458, null, "movie",
        "الشر المقيم: مرحبًا بكم في مدينة راكون", 2021, null, null, null, null, "[]", "[]", null, null, 1L)
    private val provider = "Resident Evil: Raccoon Şehri"

    @Test fun playbackUsesTheSelectedLanguageTitle() = runBlocking {
        assertEquals("الشر المقيم: مرحبًا بكم في مدينة راكون",
            moviePlaybackTitle(provider, { arabic }, { translated }))
    }

    @Test fun previousLanguageMetadataCannotLeakIntoPlayback() = runBlocking {
        assertEquals(provider, moviePlaybackTitle(provider, { arabic }, {
            translated.copy(key = "movie:tr-TR:460458", title = "Eski Türkçe başlık")
        }))
    }

    @Test fun changedConfigurationCancelsThePendingTitle() = runBlocking {
        var config = arabic.copy(language = "tr-TR")
        assertNull(moviePlaybackTitle(provider, { config }, {
            config = arabic
            translated.copy(key = "movie:tr-TR:460458", title = "Eski Türkçe başlık")
        }))
    }

    @Test fun unavailableMetadataKeepsPlaybackAvailable() = runBlocking {
        assertEquals(provider, moviePlaybackTitle(provider, { arabic }, { throw IOException("offline") }))
        assertEquals(provider, moviePlaybackTitle(provider, { arabic }, { null }))
        assertEquals(provider, moviePlaybackTitle(provider, { arabic }, { translated.copy(title = " ") }))
    }

    @Test fun providerModeDoesNotResolveMetadata() = runBlocking {
        var requests = 0
        assertEquals(provider, moviePlaybackTitle(provider, { arabic.copy(mode = MetadataMode.PROVIDER) }, {
            requests++
            translated
        }))
        assertEquals(0, requests)
    }

    @Test fun cancellationDoesNotStartPlaybackWithFallback() = runBlocking {
        try {
            moviePlaybackTitle(provider, { arabic }, { throw CancellationException("cancelled") })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            // The cancelled play request must not continue with a provider title.
        }
    }
}
