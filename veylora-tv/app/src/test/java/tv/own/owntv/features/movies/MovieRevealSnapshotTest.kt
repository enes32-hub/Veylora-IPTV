package tv.own.owntv.features.movies

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.model.MediaType

class MovieRevealSnapshotTest {
    @Test fun coldRevealWaitsForStoredVisibilityAndOrderWithoutScreenSubscribers() = runBlocking {
        val hidden = CategoryEntity(10, 1, MediaType.MOVIE, "Hidden", "hidden")
        val adult = CategoryEntity(11, 1, MediaType.MOVIE, "XXX", "adult")
        val visible = CategoryEntity(12, 1, MediaType.MOVIE, "Visible", "visible")
        val folderKey = CustomizeKeys.category(visible)
        val saved = SectionCustomizations(hiddenCategories = setOf(CustomizeKeys.category(hidden)),
            hiddenItems = mapOf("1:hidden" to "Hidden movie"), movedFromOrigin = mapOf("1:moved" to folderKey))
        val snapshot = movieRevealSnapshot(
            flow { delay(10); emit(saved) },
            flow { delay(5); emit(listOf(hidden, adult, visible)) },
            flowOf(ProfileEntity(id = 7, name = "Child", avatarColor = 0, isKids = true)),
            flow { delay(15); emit(listOf(folderKey)) },
        )!!
        assertEquals(setOf(10L, 11L), snapshot.hiddenCategories)
        assertEquals("Hidden movie", snapshot.custom.hiddenItems["1:hidden"])
        assertEquals(folderKey, snapshot.custom.movedFromOrigin["1:moved"])
        assertEquals(folderKey, snapshot.folderKeys[12])
        assertEquals(setOf(folderKey), snapshot.orderedContexts)
        assertEquals(7L, snapshot.profile.id)
    }

    @Test fun missingProfileDoesNotBecomeAnAdultDefaultSnapshot() = runBlocking {
        assertNull(movieRevealSnapshot(flowOf(SectionCustomizations()), flowOf(emptyList()), flowOf(null), flowOf(emptyList())))
    }
}
