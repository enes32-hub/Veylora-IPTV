package tv.own.owntv.core.i18n

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.features.discovery.genreLabel
import tv.own.owntv.ui.components.OwnTVPopup

/** Debug-only host used to verify that Compose LocalContext preserves Activity semantics. */
class LocaleTestActivity : ComponentActivity() {
    @Volatile
    var localizedContext: Context? = null
    @Volatile var popupLanguage: String? = null
    @Volatile var popupGenre: String? = null
    @Volatile var popupHeading: String? = null
    lateinit var testLocaleStore: LocaleStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("localized_context_test", MODE_PRIVATE)
        prefs.edit().putString("ui_language", "en-US").commit()
        val store = LocaleStore.overPreferences(prefs)
        testLocaleStore = store
        setContent {
            LocalizedContent(store) {
                localizedContext = LocalContext.current
                Box {}
                if (intent.getBooleanExtra("testPopup", false)) {
                    OwnTVPopup(onDismissRequest = {}, stageLayout = true) {
                        val language = LocalConfiguration.current.locales[0].language
                        val genre = genreLabel("Horror")
                        val heading = stringResource(R.string.veylora_filters)
                        SideEffect {
                            popupLanguage = language
                            popupGenre = genre
                            popupHeading = heading
                        }
                        Box {}
                    }
                }
            }
        }
    }
}
