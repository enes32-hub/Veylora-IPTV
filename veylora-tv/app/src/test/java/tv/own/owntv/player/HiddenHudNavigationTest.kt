package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class HiddenHudNavigationTest {
    @Test fun liveDownIsNotConsumedBeforePreviousChannel() {
        assertFalse(downRevealsHiddenControls(isLive = true, canZap = true))
    }
    @Test fun vodDownStillOpensTimelineWithoutPausing() {
        assertTrue(downRevealsHiddenControls(isLive = false, canZap = false))
        assertTrue(downRevealsHiddenControls(isLive = false, canZap = true))
    }
    @Test fun singleLiveStreamWithoutChannelListStillHasControls() {
        assertTrue(downRevealsHiddenControls(isLive = true, canZap = false))
    }
}
