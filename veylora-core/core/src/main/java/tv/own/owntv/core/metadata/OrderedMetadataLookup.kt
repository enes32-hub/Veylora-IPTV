package tv.own.owntv.core.metadata

sealed interface MetadataLookup {
    data class Found(val tmdbId: Int, val details: MovieDetails? = null, val hit: MetadataSearchResult? = null) : MetadataLookup
    data object Missing : MetadataLookup
    data object Unavailable : MetadataLookup
}

/** One identity request OR one name search after a confirmed missing identity; never a year re-query. */
suspend fun orderedMetadataLookup(
    tmdbId: Int?, year: Int?, type: MetadataType,
    identity: suspend (Int) -> IdentityDetails,
    search: suspend () -> List<MetadataSearchResult>?,
): MetadataLookup {
    tmdbId?.takeIf { it > 0 }?.let { id ->
        when (val response = identity(id)) {
            is IdentityDetails.Found -> return if (response.details.tmdbId == id)
                MetadataLookup.Found(id, details = response.details) else MetadataLookup.Unavailable
            IdentityDetails.Unavailable -> return MetadataLookup.Unavailable
            IdentityDetails.Missing -> Unit
        }
    }
    val hits = search() ?: return MetadataLookup.Unavailable
    val match = OrderedMetadataMatch.select(hits, year, type) ?: return MetadataLookup.Missing
    return MetadataLookup.Found(match.tmdbId, hit = match)
}
