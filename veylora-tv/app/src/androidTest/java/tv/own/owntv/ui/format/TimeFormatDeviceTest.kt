package tv.own.owntv.ui.format

import android.text.format.DateFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TimeFormatDeviceTest {
    @Test
    fun errorLogTimestampFormatsWithEitherClockAcrossLanguages() {
        for (language in listOf("tr", "en-US", "da", "ar", "zh-CN", "ja", "ru")) {
            for (twentyFour in listOf(true, false)) {
                val locale = Locale.forLanguageTag(language)
                val skeleton = combinedDateTimeSkeleton("dMMMjm", twentyFour)
                val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
                val text = SimpleDateFormat(pattern, locale).format(Date(1_704_110_400_000L))
                assertTrue("$language/$twentyFour must render a timestamp", text.isNotBlank())
            }
        }
    }
}
