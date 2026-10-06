package tv.own.owntv.features.series

import org.junit.Assert.*
import org.junit.Test

class SeriesBrowseNavigationTest {
    @Test fun emptyPagingSnapshotCannotFinishReturnWhileCatalogStillHasShows() {
        assertFalse(seriesReturnComplete(itemCount = 0, catalogCount = 500, focused = false))
        assertFalse(seriesReturnComplete(itemCount = 500, catalogCount = 500, focused = false))
        assertTrue(seriesReturnComplete(itemCount = 500, catalogCount = 500, focused = true))
    }

    @Test fun genuinelyEmptyCatalogFinishesReturnWithoutRequestingFocus() {
        assertTrue(seriesReturnComplete(itemCount = 0, catalogCount = 0, focused = false))
    }

    @Test fun returningFromEpisodesKeepsTheSameCategoryPositionWhenRememberIsOff() {
        val navigation = SeriesBrowseNavigation<String>()
        navigation.shouldReset("Arabic", remember = false)
        assertFalse(navigation.shouldReset("Arabic", remember = false))
        assertTrue(navigation.shouldReset("Drama", remember = false))
    }

    @Test fun categoryMemoryDoesNotResetPositionsButDisablingItDoes() {
        val navigation = SeriesBrowseNavigation<String>()
        assertFalse(navigation.shouldReset("Arabic", remember = true))
        assertFalse(navigation.shouldReset("Drama", remember = true))
        assertTrue(navigation.shouldReset("Drama", remember = false))
        assertFalse(navigation.shouldReset("Drama", remember = false))
    }

    @Test fun unloadedReturnTargetKeepsItsAbsoluteIndexInsteadOfJumpingToFirst() {
        assertEquals(245, seriesReturnIndex(42L, 245, 1000, 0, listOf(1L, 2L)))
    }

    @Test fun loadedReturnTargetAccountsForLeadingPagingPlaceholders() {
        assertEquals(241, seriesReturnIndex(42L, 245, 1000, 240, listOf(41L, 42L)))
    }

    @Test fun removedReturnTargetUsesNearestSurvivingPosition() {
        assertEquals(3, seriesReturnIndex(42L, 245, 4, 0, listOf(1L, 2L, 3L, 4L)))
        assertNull(seriesReturnIndex(42L, 245, 0, 0, emptyList()))
    }
}
