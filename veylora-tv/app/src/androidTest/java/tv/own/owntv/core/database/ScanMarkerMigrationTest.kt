package tv.own.owntv.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.entity.MetadataMatchEntity

@RunWith(AndroidJUnit4::class)
class ScanMarkerMigrationTest {
    @Test fun completionSurvivesLanguageChangeWithoutMixingMaturityOrMedia() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OwnTVDatabase::class.java).build()
        try {
            val dao = db.metadataDao()
            for (key in listOf("scan:v2:tr-TR:movie:1:10", "scan:v2:en-US:movie:1:10", "scan:v2::tv:1:10:kids"))
                dao.upsertMatch(MetadataMatchEntity(key, "scan", null, 1.0, 123))
            dao.migrateLanguageScopedScans()
            dao.migrateLanguageScopedScans()
            assertNotNull(dao.getMatch("scan:v3:movie:1:10"))
            assertNotNull(dao.getMatch("scan:v3:tv:1:10:kids"))
            assertNull(dao.getMatch("scan:v3:tv:1:10"))
            assertNull(dao.getMatch("scan:v3:movie:1:10:kids"))
            assertEquals(123L, dao.getMatch("scan:v3:movie:1:10")!!.updatedAt)
        } finally { db.close() }
    }
}
