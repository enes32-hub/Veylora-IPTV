package tv.own.owntv.features.shell

import androidx.paging.LoadState
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.features.shell.components.VodEmptyState
import tv.own.owntv.features.shell.components.vodEmptyState

class VodEmptyRefreshTest {
    @Test fun backgroundInvalidationDoesNotReplaceKnownEmptyResultWithSpinner() {
        assertEquals(VodEmptyState.EMPTY, vodEmptyState(LoadState.Loading, settledEmpty = true))
    }
    @Test fun newQueryStillLoadsAndFailuresRemainVisible() {
        assertEquals(VodEmptyState.LOADING, vodEmptyState(LoadState.Loading, settledEmpty = false))
        assertEquals(VodEmptyState.ERROR, vodEmptyState(LoadState.Error(Exception("offline")), settledEmpty = true))
    }
}
