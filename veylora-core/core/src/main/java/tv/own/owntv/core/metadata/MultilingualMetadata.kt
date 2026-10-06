package tv.own.owntv.core.metadata

import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.entity.MetadataCacheEntity

/** One language-independent package per identity; artwork URLs only, never image downloads. */
object MultilingualMetadata {
    private fun JSONObject.text(key: String): String? =
        optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    fun key(type: String, id: Int): String = "bundle:$type:$id"

    /** Root details must be requested in English so missing translated fields have a stable fallback. */
    fun pack(root: JSONObject, unfilteredImages: JSONObject? = null): String? {
        val translations = root.optJSONObject("translations")?.optJSONArray("translations") ?: return null
        val images = unfilteredImages ?: root.optJSONObject("images") ?: return null
        val compact = JSONObject().put("version", if (validImages(unfilteredImages)) 2 else 1)
            .put("overview", root.text("overview"))
            .put("title", root.text("title") ?: root.text("name"))
        val texts = JSONArray()
        for (i in 0 until translations.length()) {
            val translation = translations.optJSONObject(i) ?: continue
            val data = translation.optJSONObject("data") ?: continue
            texts.put(JSONObject().put("language", translation.text("iso_639_1"))
                .put("region", translation.text("iso_3166_1"))
                .put("title", data.text("title") ?: data.text("name"))
                .put("overview", data.text("overview")))
        }
        compact.put("texts", texts)
        putArtwork(compact, images)
        return compact.toString()
    }

    private fun validImages(images: JSONObject?): Boolean = images != null &&
        listOf("posters", "backdrops", "logos").all { images.optJSONArray(it) != null }

    private fun putArtwork(compact: JSONObject, images: JSONObject) {
        // Keep the best artwork for every available language, rather than thousands of variants.
        for (kind in listOf("posters", "backdrops", "logos")) {
            val candidates = images.optJSONArray(kind) ?: JSONArray()
            val best = linkedMapOf<String, JSONObject>()
            for (i in 0 until candidates.length()) {
                val image = candidates.optJSONObject(i) ?: continue
                val path = image.text("file_path") ?: continue
                if (path.endsWith(".svg", ignoreCase = true)) continue
                val language = image.text("iso_639_1") ?: ""
                val old = best[language]
                if (old == null || image.optDouble("vote_average", 0.0) > old.optDouble("vote_average", 0.0)) best[language] = image
            }
            val paths = JSONObject()
            best.forEach { (language, image) -> paths.put(language, image.text("file_path")) }
            compact.put(kind, paths)
        }
    }

    fun localize(base: MetadataCacheEntity, payload: String, language: String): MetadataCacheEntity {
        val bundle = JSONObject(payload)
        val locale = java.util.Locale.forLanguageTag(language.ifBlank { "en-US" })
        val texts = bundle.optJSONArray("texts") ?: JSONArray()
        val allTexts = (0 until texts.length()).mapNotNull { texts.optJSONObject(it) }
        val candidates = allTexts
            .filter { it.text("language") == locale.language }
            .sortedBy { if (it.text("region") == locale.country) 0 else 1 }
        fun text(field: String): String? = candidates.firstNotNullOfOrNull { it.text(field) } ?: bundle.text(field)
            ?: allTexts.filter { it.text("language") == "en" }.firstNotNullOfOrNull { it.text(field) }
        fun image(kind: String): String? {
            val paths = bundle.optJSONObject(kind) ?: return null
            return listOfNotNull(paths.text(locale.language), paths.text("en"), paths.text(""))
                .firstOrNull { !it.endsWith(".svg", ignoreCase = true) }
        }
        return base.copy(
            title = text("title").orEmpty(),
            overview = text("overview"),
            posterPath = image("posters"),
            backdropPath = image("backdrops"),
            logoPath = image("logos"),
        )
    }

    /** Bundle rows use their own type, excluded from ordinary display/genre SQL joins. */
    fun stored(base: MetadataCacheEntity, payload: String): MetadataCacheEntity =
        base.copy(key = key(base.type, base.tmdbId), type = "bundle", overview = payload)

    fun isComplete(stored: MetadataCacheEntity): Boolean = runCatching {
        stored.type == "bundle" && JSONObject(stored.overview ?: return false).optInt("version") == 2
    }.getOrDefault(false)

    fun repairArtwork(stored: MetadataCacheEntity, images: JSONObject): MetadataCacheEntity? = runCatching {
        if (stored.type != "bundle" || !validImages(images)) return null
        val payload = JSONObject(stored.overview ?: return null)
        if (payload.optJSONArray("texts") == null) return null
        putArtwork(payload, images)
        payload.put("version", 2)
        stored.copy(overview = payload.toString())
    }.getOrNull()

    fun restore(stored: MetadataCacheEntity, language: String): MetadataCacheEntity? = runCatching {
        val payload = stored.overview ?: return null
        if (stored.type != "bundle" || JSONObject(payload).optInt("version") !in 1..2) return null
        val type = stored.key.split(':').getOrNull(1) ?: return null
        val key = if (type == "tv") MetadataRepository.tvCacheKey(stored.tmdbId, language)
            else MetadataRepository.cacheKey(stored.tmdbId, language)
        localize(stored.copy(key = key, type = type), payload, language)
    }.getOrNull()
}
