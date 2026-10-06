package tv.own.owntv.core.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataLanguageDefaultTest {
    @Test fun `new install follows device language`() {
        assertEquals(MetadataConfig.LANGUAGE_AUTO, metadataLanguageOrDevice(null))
        assertEquals(MetadataConfig.LANGUAGE_AUTO, MetadataConfig().language)
    }
    @Test fun `explicit language including legacy English remains unchanged`() {
        assertEquals("tr-TR", metadataLanguageOrDevice("tr-TR"))
        assertEquals("", metadataLanguageOrDevice(""))
    }
}
