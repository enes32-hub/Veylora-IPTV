package tv.own.owntv.core.metadata

import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtreamClient

/** Exceptions intentionally propagate: a network failure is not proof of missing identity. */
class ProviderIdentityLookup(private val sources: SourceDao, private val xtream: XtreamClient) {
    suspend fun lookup(sourceId: Long, remoteId: String?, series: Boolean): ProviderMetadataIdentity {
        val source = sources.getById(sourceId) ?: return ProviderMetadataIdentity()
        if (source.type != SourceType.XTREAM || remoteId.isNullOrBlank()) return ProviderMetadataIdentity()
        return try {
            xtream.metadataIdentity(source, remoteId, series)
        } catch (error: tv.own.owntv.core.network.HttpClient.HttpStatusException) {
            if (error.code == 404) ProviderMetadataIdentity() else throw error
        }
    }
}
