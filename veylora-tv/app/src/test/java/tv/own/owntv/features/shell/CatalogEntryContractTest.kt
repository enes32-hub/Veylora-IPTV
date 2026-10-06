package tv.own.owntv.features.shell

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wiring contracts; provider ordering itself is exercised by the core lookup tests. */
class CatalogEntryContractTest {
    @Test fun homeUsesTheSameIdentityResolutionAsCatalog() {
        val home = File("src/main/java/tv/own/owntv/features/home/HomeViewModel.kt").readText()
        assertFalse(home.contains("metadata.resolveKnown"))
        assertTrue(home.contains("metadata.resolveMovie(item.movie)"))
        assertTrue(home.contains("metadata.resolveSeries(item.series)"))
    }

    @Test fun pinnedCatalogKeepsFiltersAndToolbarFocus() {
        for (screen in listOf("movies/MoviesScreen", "series/SeriesScreen")) {
            val source = File("src/main/java/tv/own/owntv/features/$screen.kt").readText()
            val pinned = source.substringAfter("Column(Modifier.padding(start = listX")
                .substringBefore("// The lists clip")
            assertTrue(screen, pinned.contains("toolbarFocused = it.hasFocus"))
            assertTrue(screen, pinned.contains("filtersOpen = true"))
            assertTrue(screen, pinned.contains("discoveryFilter.activeCount"))
        }
    }
}
