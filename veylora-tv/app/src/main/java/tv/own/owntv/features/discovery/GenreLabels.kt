package tv.own.owntv.features.discovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import java.util.Locale

/** TMDB genre names captured for all packaged locales; filtering keeps canonical DB values. */
internal class GenreLabels(json: String) {
    private val root = JSONObject(json)
    private val locales = root.keys().asSequence().toList()
    private val english = root.optJSONObject("en-US") ?: JSONObject()
    private val ids = buildMap<String, String> {
        for (tag in locales) {
            val labels = root.getJSONObject(tag)
            for (id in labels.keys()) putIfAbsent(labels.getString(id), id)
        }
        for (id in english.keys()) put(english.getString(id), id)
    }
    fun label(canonical: String, language: String): String {
        val id = ids[canonical] ?: return canonical
        val locale = Locale.forLanguageTag(language)
        val tag = locales.firstOrNull { it == language }
            ?: locales.firstOrNull { Locale.forLanguageTag(it).language == locale.language }
        return tag?.let { root.getJSONObject(it).optString(id).takeIf(String::isNotBlank) }
            ?: english.optString(id).takeIf(String::isNotBlank) ?: canonical
    }
}

private object PackagedGenres {
    @Volatile private var cached: GenreLabels? = null
    fun get(context: android.content.Context): GenreLabels = cached ?: synchronized(this) {
        cached ?: GenreLabels(context.assets.open("tmdb_genres.json").bufferedReader().use { it.readText() }).also { cached = it }
    }
}

@Composable
internal fun genreLabel(canonical: String): String {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val labels = remember { PackagedGenres.get(context) }
    return labels.label(canonical, language)
}

@Composable
internal fun genreLabels(canonical: List<String>): List<String> = canonical.map { genreLabel(it) }
