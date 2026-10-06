package tv.own.owntv.core.settings

import org.junit.Assert.*
import org.junit.Test

class PlaylistRefreshSelectionTest {
    @Test fun `summary only includes current profile sources and treats absent settings as off`() {
        val daily = PlaylistRefresh(PlaylistAutoRefresh.MANUAL, 1)
        assertEquals(PlaylistRefresh.OFF, playlistRefreshSelection(emptyList(), mapOf(3L to daily)))
        assertEquals(daily, playlistRefreshSelection(listOf(1, 2), mapOf(1L to daily, 2L to daily, 3L to PlaylistRefresh.OFF)))
        assertNull(playlistRefreshSelection(listOf(1, 2), mapOf(1L to daily)))
        assertEquals(PlaylistRefresh.OFF, playlistRefreshSelection(listOf(1, 2), emptyMap()))
    }
}
