package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.model.*

class LightHomeConfigTest {
    @Test fun `popular rows preserve original home sections and their visibility preferences`() {
        val original = HomeConfig(hidden = setOf(HomeRow.TRENDING, HomeRow.RECENT_CHANNELS))
        val config = lightHomeConfig(original)
        assertEquals(listOf(HomeRow.TRENDING, HomeRow.HERO, HomeRow.FAVORITE_CHANNELS, HomeRow.CONTINUE_MOVIES, HomeRow.CONTINUE_SERIES), config.visibleOrder)
        assertEquals(HomeTrendingStyle.POSTERS, config.trendingStyle)
        assertTrue(config.heroIncludeLive)
        assertEquals(original.order, config.order)
    }
}
