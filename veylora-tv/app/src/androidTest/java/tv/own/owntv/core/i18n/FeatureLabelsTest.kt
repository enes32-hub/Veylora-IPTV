package tv.own.owntv.core.i18n

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.R

/** Uses isolated resource contexts; never changes the television or profile language. */
class FeatureLabelsTest {
    @Test fun packagedLabelsResolveInDifferentScripts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cases = listOf(
            listOf("tr", "Popüler filmler", "Popüler diziler", "Fark etmez"),
            listOf("en", "Popular movies", "Popular series", "Any"),
            listOf("de", "Beliebte Filme", "Beliebte Serien", "Beliebig"),
            listOf("ar", "أفلام رائجة", "مسلسلات رائجة", "أيّ"),
            listOf("ja", "人気の映画", "人気のシリーズ", "指定なし"),
        )
        for ((tag, movies, series, any) in cases) {
            val config = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(tag)))
            }
            val localized = context.createConfigurationContext(config)
            assertEquals(tag, movies, localized.getString(R.string.veylora_popular_movies))
            assertEquals(tag, series, localized.getString(R.string.veylora_popular_series))
            assertEquals(tag, any, localized.getString(R.string.veylora_filter_any))
        }
    }
}
