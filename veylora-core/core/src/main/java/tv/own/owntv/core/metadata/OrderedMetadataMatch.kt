package tv.own.owntv.core.metadata

/** Selection from ONE name search. Keep TMDB relevance order; year is a local preference. */
internal object OrderedMetadataMatch {
    fun select(
        hits: List<MetadataSearchResult>,
        providerYear: Int?,
        type: MetadataType,
    ): MetadataSearchResult? {
        val candidates = hits.filter { it.type == type && it.tmdbId > 0 }
        return providerYear?.let { year -> candidates.firstOrNull { it.year == year } }
            ?: candidates.firstOrNull()
    }
}
