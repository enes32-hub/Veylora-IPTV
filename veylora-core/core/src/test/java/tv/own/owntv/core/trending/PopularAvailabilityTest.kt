package tv.own.owntv.core.trending

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.TrendingSnapshotEntity
import tv.own.owntv.core.database.entity.TrendingSnapshotStatus

class PopularAvailabilityTest {
    @Test fun statusDoesNotDescribeSeventyThreeMatchesAsTen() {
        val state = TrendingSnapshotEntity(1, TrendingSnapshotStatus.ELIGIBLE, "tr", 1, 1, "test", 73)
        assertEquals(TrendingAvailability.Showing(73, false), trendingAvailability(listOf(state), true, false))
    }
}
