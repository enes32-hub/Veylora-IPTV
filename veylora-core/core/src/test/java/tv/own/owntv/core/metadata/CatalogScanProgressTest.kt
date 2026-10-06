package tv.own.owntv.core.metadata

import org.junit.Assert.*
import org.junit.Test

class CatalogScanProgressTest {
    @Test fun `banner appears only during unfinished active work`() {
        assertFalse(CatalogScanProgress().showBanner)
        assertFalse(CatalogScanProgress(total = 10, completed = 2).showBanner)
        assertTrue(CatalogScanProgress(total = 10, completed = 2, running = true).showBanner)
        assertFalse(CatalogScanProgress(total = 10, completed = 10, running = true).showBanner)
        assertFalse(CatalogScanProgress(total = 10, completed = 11, running = true).showBanner)
        assertFalse(CatalogScanProgress(total = 10, completed = 9, retrying = 1, running = false).showBanner)
        assertTrue(CatalogScanProgress(total = 11, completed = 10, running = true).showBanner)
    }
}
