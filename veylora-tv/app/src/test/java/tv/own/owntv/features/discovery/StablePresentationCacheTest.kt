package tv.own.owntv.features.discovery

import org.junit.Assert.*
import org.junit.Test

class StablePresentationCacheTest {
    @Test fun knownButUnlocalizedItemWaitsAndFocusResultSurvivesScrolling() {
        val cache = StablePresentationCache<String>()
        cache.selectScope("de")
        cache.update(listOf(1L,2L), emptyMap(), pending=setOf(1L))
        assertFalse(1L in cache.checkedIds())
        assertTrue(2L in cache.checkedIds())
        cache.update(listOf(1L), mapOf(1L to "Deutsch"))
        cache.update(listOf(3L), emptyMap())
        assertEquals("Deutsch",cache.snapshot()[1L])
        assertTrue(1L in cache.checkedIds())
        cache.selectScope("ar")
        assertTrue(cache.checkedIds().isEmpty())
    }
    @Test fun scrollingRetainsExistingPostersUntilNewRowsArrive() {
        val cache = StablePresentationCache<String>(3)
        cache.selectScope("de")
        cache.update(listOf(1L, 2L), mapOf(1L to "de1", 2L to "de2"))
        cache.selectScope("de")
        assertEquals("de2", cache.snapshot()[2L])
        cache.update(listOf(2L, 3L), mapOf(2L to "de2", 3L to "de3"))
        assertEquals(mapOf(1L to "de1", 2L to "de2", 3L to "de3"), cache.snapshot())
        cache.update(listOf(3L, 4L), mapOf(3L to "de3", 4L to "de4"))
        assertEquals(mapOf(2L to "de2", 3L to "de3", 4L to "de4"), cache.snapshot())
    }
    @Test fun languageOrProfileChangeClearsOldPresentationButMissingRowsDoNotPersist() {
        val cache = StablePresentationCache<String>()
        cache.selectScope("de:profile1")
        cache.update(listOf(1L, 2L), mapOf(1L to "old", 2L to "keep"))
        cache.update(listOf(1L), emptyMap())
        assertEquals(mapOf(2L to "keep"), cache.snapshot())
        cache.selectScope("tr:profile1")
        assertTrue(cache.snapshot().isEmpty())
        cache.update(listOf(1L), mapOf(1L to "turkish"))
        cache.selectScope("tr:profile2")
        assertTrue(cache.snapshot().isEmpty())
    }
}
