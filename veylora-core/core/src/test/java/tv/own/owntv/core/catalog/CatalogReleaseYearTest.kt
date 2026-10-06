package tv.own.owntv.core.catalog

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.computeContentHash

class CatalogReleaseYearTest {
    @Test fun correctedDerivedYearTriggersCatalogUpdate() {
        val old = MovieEntity(sourceId=1, name="2012", streamUrl="https://example.test/movie", parsedYear=2012)
        assertNotEquals(old.computeContentHash(), old.copy(parsedYear=null).computeContentHash())
    }
    @Test fun bareNumbersInTitlesAreNotReleaseYears() {
        for (title in listOf("2001: Uzay Yolu Macerası", "Dracula 2000", "Yıl 2000", "2012")) {
            assertNull(title, ProviderCatalogMetadataParser.parse(title, null).parsedYear)
        }
    }

    @Test fun declaredProviderYearWinsOverTitleNumber() {
        assertEquals(1968, ProviderCatalogMetadataParser.parse("2001: Uzay Yolu Macerası", 1968).parsedYear)
    }

    @Test fun explicitBracketedReleaseTagIsStillUsable() {
        assertEquals(1968, ProviderCatalogMetadataParser.parse("2001: A Space Odyssey (1968) [HD]", null).parsedYear)
    }
}
