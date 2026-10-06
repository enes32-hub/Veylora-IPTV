package tv.own.owntv.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class VodSortPreferenceTest {
    @Test fun `popular preference is available again without losing saved choice`() {
        assertEquals(SettingsRepository.SortMode.POPULAR, parseVodSortPreference("POPULAR"))
    }
    @Test fun `existing choices and unset defaults remain intact`() {
        assertEquals(SettingsRepository.SortMode.RANDOM, parseVodSortPreference("RANDOM"))
        assertEquals(SettingsRepository.SortMode.PLAYLIST, parseVodSortPreference("ALPHA"))
        assertEquals(SettingsRepository.SortMode.RATING, parseVodSortPreference("RATING"))
        assertEquals(SettingsRepository.SortMode.DATE_ADDED, parseVodSortPreference("DATE_ADDED"))
        assertEquals(SettingsRepository.SortMode.PLAYLIST, parseVodSortPreference(null))
        assertEquals(SettingsRepository.SortMode.PLAYLIST, parseVodSortPreference("unknown"))
    }
}
