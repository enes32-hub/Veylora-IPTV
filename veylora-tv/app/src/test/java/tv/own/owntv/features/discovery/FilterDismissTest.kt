package tv.own.owntv.features.discovery

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.catalog.DiscoveryFilter
import tv.own.owntv.features.shell.components.dismissDiscoveryFilter

class FilterDismissTest {
    @Test fun closingPanelSavesAllDraftFieldsBeforeClosing() {
        val draft = DiscoveryFilter(genre = "Drama", minYear = 1998, maxYear = 2002, minRating = 5.0)
        var saved: DiscoveryFilter? = null
        var closed = false
        dismissDiscoveryFilter(false, draft, { saved = it }, { fail("Unexpected submenu") }, {
            assertEquals(draft, saved)
            closed = true
        })
        assertTrue(closed)
    }
    @Test fun backFromGenreListKeepsDraftWithoutClosingOrSavingPanel() {
        var returned = false
        dismissDiscoveryFilter(true, DiscoveryFilter(minYear = 2000), { fail("Premature save") },
            { returned = true }, { fail("Premature close") })
        assertTrue(returned)
    }
}
