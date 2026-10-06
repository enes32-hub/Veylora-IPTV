package tv.own.owntv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataServiceVersionTest {
    @Test fun `authorized service release is independent of application version`() {
        val oldVersion = CoreBuildInfo.versionName
        val oldService = CoreBuildInfo.metadataServiceVersion
        try {
            CoreBuildInfo.versionName = "99.99.99"
            CoreBuildInfo.metadataServiceVersion = "5.1.0"
            assertEquals("5.1.0", CoreBuildInfo.effectiveMetadataServiceVersion)
            assertEquals("99.99.99", CoreBuildInfo.versionName)
            CoreBuildInfo.metadataServiceVersion = ""
            assertEquals("99.99.99", CoreBuildInfo.effectiveMetadataServiceVersion)
        } finally {
            CoreBuildInfo.versionName = oldVersion
            CoreBuildInfo.metadataServiceVersion = oldService
        }
    }
}
