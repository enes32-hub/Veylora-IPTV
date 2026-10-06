package tv.own.owntv.features.discovery

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import tv.own.owntv.core.metadata.MetadataConfig
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.core.database.entity.MetadataCacheEntity

data class LocalizedPresentation(
    val language: String = "",
    val rows: Map<Long, MetadataCacheEntity> = emptyMap(),
    val checkedIds: Set<Long> = rows.keys,
) {
    fun forLanguage(requested: String): Map<Long, MetadataCacheEntity> =
        if (language == requested) rows else emptyMap()

    fun ready(id: Long, requested: String, enabled: Boolean): Boolean =
        !enabled || (language == requested && id in checkedIds)

    fun title(id: Long, requested: String, enabled: Boolean, provider: String): String = when {
        !enabled -> provider
        !ready(id, requested, enabled) -> ""
        else -> rows[id]?.title?.takeIf { it.isNotBlank() } ?: provider
    }

    fun poster(id: Long, requested: String, enabled: Boolean, provider: String?): String? = when {
        !enabled -> provider
        !ready(id, requested, enabled) -> null
        rows[id] == null -> provider
        else -> tv.own.owntv.core.metadata.MetadataImages.poster(rows[id]?.posterPath)
    }
}

fun MetadataCacheEntity.inLanguage(language: String): Boolean = when (type) {
    "episode" -> key.startsWith(MetadataRepository.tvCacheKey(tmdbId, language) + ":s")
    "tv" -> key == MetadataRepository.tvCacheKey(tmdbId, language)
    else -> key == MetadataRepository.cacheKey(tmdbId, language)
}

fun localizedContentTitle(provider: String, cache: MetadataCacheEntity?, config: MetadataConfig): String =
    if (config.enabled) cache?.takeIf { it.inLanguage(config.resolvedLanguage) }?.title?.takeIf { it.isNotBlank() } ?: provider
    else provider

/** The UI knows its new locale before asynchronous repository collectors have delivered it. */
@Composable
fun presentationLanguage(config: MetadataConfig): String =
    if (config.language == MetadataConfig.LANGUAGE_AUTO) LocalConfiguration.current.locales[0].toLanguageTag()
    else config.resolvedLanguage
