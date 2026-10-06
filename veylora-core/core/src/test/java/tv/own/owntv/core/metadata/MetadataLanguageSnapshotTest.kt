package tv.own.owntv.core.metadata

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class MetadataLanguageSnapshotTest {
    @Test fun automaticLanguageIsAnImmutableScopeAndChangesInvalidateEquality() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val german = MetadataConfig()
            assertEquals("de-DE", german.resolvedLanguage)
            Locale.setDefault(Locale.forLanguageTag("ar-SA"))
            val arabic = MetadataConfig()
            assertEquals("de-DE", german.resolvedLanguage)
            assertEquals("ar-SA", arabic.resolvedLanguage)
            assertNotEquals(german, arabic)
        } finally { Locale.setDefault(original) }
    }
}
