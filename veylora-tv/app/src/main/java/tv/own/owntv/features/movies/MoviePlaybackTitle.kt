package tv.own.owntv.features.movies

import kotlinx.coroutines.CancellationException
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.metadata.MetadataConfig
import tv.own.owntv.features.discovery.localizedContentTitle

/** Null discards a play request whose metadata settings changed during resolution. */
internal suspend fun moviePlaybackTitle(
    provider: String,
    currentConfig: suspend () -> MetadataConfig,
    resolveMetadata: suspend () -> MetadataCacheEntity?,
): String? {
    val config = currentConfig()
    val metadata = if (config.enabled) {
        try {
            resolveMetadata()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    } else null
    if (currentConfig() != config) return null
    return localizedContentTitle(provider, metadata, config)
}
