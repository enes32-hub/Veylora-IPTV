package tv.own.owntv.features.discovery

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FocusedMetadataTest {
    @Test fun savedTitleAndLogoAreReturnedWithoutDelayOrNetwork() = runBlocking {
        val events = mutableListOf<String>()
        val actual = focusedMetadata({ events.add("cache"); "saved Arabic title and logo" },
            { events.add("delay") }, { events.add("network"); "new metadata" })
        assertEquals("saved Arabic title and logo", actual)
        assertEquals(listOf("cache"), events)
    }
    @Test fun missingCacheStillDebouncesNetworkRequests() = runBlocking {
        val events = mutableListOf<String>()
        assertEquals("resolved", focusedMetadata({ events.add("cache"); null },
            { events.add("delay") }, { events.add("network"); "resolved" }))
        assertEquals(listOf("cache", "delay", "network"), events)
    }
}
