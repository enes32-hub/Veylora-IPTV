package tv.own.owntv.core.metadata

import org.junit.Assert.*
import org.junit.Test

class EpisodeLanguageFallbackTest {
    @Test fun `missing translated fields use English without replacing translated title`() {
        val english = EpisodeDetails("War", "English plot", "/still.jpg", "2024-10-10", 7.0)
        val arabic = EpisodeDetails("الحرب", " ", null, null, null)
        val result = arabic.withEnglishFallback(english)
        assertEquals("الحرب",result.name)
        assertEquals("English plot",result.overview)
        assertEquals("/still.jpg",result.stillPath)
        assertEquals("War",arabic.copy(name="").withEnglishFallback(english).name)
    }
}
