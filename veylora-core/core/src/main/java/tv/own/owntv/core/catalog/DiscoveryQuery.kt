package tv.own.owntv.core.catalog

import org.json.JSONObject
import tv.own.owntv.core.metadata.MetadataMode

data class DiscoveryQuery(val sql: String, val arguments: List<Any>)
/** Display values projected by the very same expressions used for filtering and ordering. */
data class DiscoveryValues(val id: Long, val year: Int?, val rating: Double?)

/** Immediate display fallback while Room's scoped values observation starts. */
fun discoveryValues(id: Long, providerYear: Int?, parsedYear: Int?, providerRating: Double?,
    tmdbYear: Int?, tmdbRating: Double?, mode: MetadataMode): DiscoveryValues {
    val year = providerYear ?: parsedYear
    val ownRating = providerRating?.takeIf { it.isFinite() && it > 0 && it <= 10 }
    val otherRating = tmdbRating?.takeIf { it.isFinite() && it > 0 && it <= 10 }
    return when (mode) {
        MetadataMode.PROVIDER -> DiscoveryValues(id, year, ownRating)
        MetadataMode.PROVIDER_PLUS_TMDB -> DiscoveryValues(id, tmdbYear ?: year, otherRating ?: ownRating)
        MetadataMode.TMDB_ONLY -> DiscoveryValues(id, tmdbYear ?: year, otherRating ?: ownRating)
    }
}

enum class DiscoveryMedia { MOVIE, SERIES }
enum class DiscoveryOrder { TITLE, RATING, ADDED, PROVIDER, POPULAR, RANDOM }
data class DiscoveryManualOrder(val profileId: Long, val contextKey: String)
sealed interface DiscoveryScope {
    data object All : DiscoveryScope
    data object Favorites : DiscoveryScope
    data object History : DiscoveryScope
    data class Custom(val contextKey: String) : DiscoveryScope
}

fun discoveryQuery(
    media: DiscoveryMedia,
    sourceIds: List<Long>,
    filter: DiscoveryFilter,
    language: String,
    categoryId: Long? = null,
    search: String = "",
    order: DiscoveryOrder = DiscoveryOrder.TITLE,
    countOnly: Boolean = false,
    excludedCategoryIds: Set<Long> = emptySet(),
    genresOnly: Boolean = false,
    manualOrder: DiscoveryManualOrder? = null,
    metadataMode: MetadataMode = MetadataMode.PROVIDER_PLUS_TMDB,
    includeAdult: Boolean = true,
    itemIds: List<Long>? = null,
    valuesOnly: Boolean = false,
    excludedItemKeys: Set<String> = emptySet(),
    profileId: Long = 0,
    randomSeed: Long = 1,
    scope: DiscoveryScope = DiscoveryScope.All,
): DiscoveryQuery {
    val table = if (media == DiscoveryMedia.MOVIE) "movies" else "series"
    val type = if (media == DiscoveryMedia.MOVIE) "movie" else "tv"
    val prefix = if (language.isBlank()) "$type:" else "$type:$language:"
    val args = mutableListOf<Any>()
    if (metadataMode.enrich) args.addAll(listOf("$type:", if (includeAdult) "" else ":kids", prefix))
    val manual = manualOrder?.takeIf { order == DiscoveryOrder.PROVIDER && !countOnly && !genresOnly }
    val manualJoin = if (manual == null) "" else {
        args += manual.profileId
        args += media.name
        args += manual.contextKey
        "LEFT JOIN content_order o ON o.itemId = m.id AND o.profileId = ? AND o.mediaType = ? AND o.contextKey = ? "
    }
    val conditions = mutableListOf<String>()
    if (scope != DiscoveryScope.All) {
        val scopeTable = when (scope) {
            DiscoveryScope.Favorites -> "favorites"
            DiscoveryScope.History -> "watch_history"
            is DiscoveryScope.Custom -> "custom_category_members"
            DiscoveryScope.All -> error("Unreachable scope")
        }
        val context = if (scope is DiscoveryScope.Custom) " AND scope.contextKey = ?" else ""
        conditions += "EXISTS (SELECT 1 FROM $scopeTable scope WHERE scope.profileId = ? AND scope.mediaType = ? AND scope.itemId = m.id$context)"
        args += profileId
        args += media.name
        if (scope is DiscoveryScope.Custom) args += scope.contextKey
    }
    filter.watched?.let { watched ->
        val exists = "EXISTS (SELECT 1 FROM watch_history h WHERE h.profileId = ? AND h.mediaType = ? AND h.itemId = m.id)"
        conditions += if (watched) exists else "NOT $exists"
        args += profileId
        args += media.name
    }
    if (sourceIds.isEmpty()) conditions += "0" else {
        conditions += "m.sourceId IN (${sourceIds.joinToString(",") { "?" }})"
        args.addAll(sourceIds)
    }
    categoryId?.let { conditions += "m.categoryId = ?"; args += it }
    itemIds?.let { ids ->
        if (ids.isEmpty()) conditions += "0" else {
            conditions += "m.id IN (${ids.joinToString(",") { "?" }})"
            args.addAll(ids)
        }
    }
    if (excludedItemKeys.isNotEmpty()) {
        conditions += "(m.sourceId || ':' || COALESCE(m.remoteId, m.name)) NOT IN (${excludedItemKeys.joinToString(",") { "?" }})"
        args.addAll(excludedItemKeys)
    }
    if (excludedCategoryIds.isNotEmpty()) {
        conditions += "(m.categoryId IS NULL OR m.categoryId NOT IN (${excludedCategoryIds.joinToString(",") { "?" }}))"
        args.addAll(excludedCategoryIds)
    }
    if (search.isNotBlank()) {
        // Literal substring, not SQL LIKE wildcard syntax; all user text is bound.
        conditions += "instr(lower(m.name), lower(?)) > 0"
        args += search.trim()
    }
    val providerYear = "COALESCE(m.year, m.parsedYear)"
    val providerRating = "CASE WHEN m.rating > 0 AND m.rating <= 10 THEN m.rating END"
    val tmdbRating = "CASE WHEN meta.rating > 0 AND meta.rating <= 10 THEN meta.rating END"
    val year = when (metadataMode) {
        MetadataMode.PROVIDER -> providerYear
        MetadataMode.PROVIDER_PLUS_TMDB -> "COALESCE(meta.year, $providerYear)"
        MetadataMode.TMDB_ONLY -> "COALESCE(meta.year, $providerYear)"
    }
    val rating = when (metadataMode) {
        MetadataMode.PROVIDER -> providerRating
        MetadataMode.PROVIDER_PLUS_TMDB -> "COALESCE($tmdbRating, $providerRating)"
        MetadataMode.TMDB_ONLY -> "COALESCE($tmdbRating, $providerRating)"
    }
    conditions += if (metadataMode.enrich)
        "($rating IS NOT NULL OR ((m.rating IS NULL OR m.rating = 0) AND (meta.rating IS NULL OR meta.rating = 0)))"
    else "($rating IS NOT NULL OR m.rating IS NULL OR m.rating = 0)"
    filter.minYear?.let { conditions += "$year >= ?"; args += it }
    filter.maxYear?.let { conditions += "$year <= ?"; args += it }
    filter.minRating?.let { conditions += "$rating >= ?"; args += it }
    filter.maxRating?.let { conditions += "$rating <= ?"; args += it }
    filter.genre?.trim()?.takeIf { it.isNotEmpty() }?.let {
        conditions += if (metadataMode.enrich)
            "(instr(lower(meta.genresJson), lower(?)) > 0 OR EXISTS (SELECT 1 FROM metadata_cache genreMeta WHERE genreMeta.tmdbId = match.tmdbId AND genreMeta.type = '$type' AND instr(lower(genreMeta.genresJson), lower(?)) > 0))"
        else "instr(NULL, lower(?)) > 0"
        args += JSONObject.quote(it)
        if (metadataMode.enrich) args += JSONObject.quote(it)
    }
    val alphabetical = order == DiscoveryOrder.TITLE && !countOnly && !genresOnly && !valuesOnly
    val displayTitle = if (!alphabetical || !metadataMode.enrich) "NULL" else {
        val locale = java.util.Locale.forLanguageTag(language.ifBlank { "en" })
        // SELECT placeholders precede JOIN and WHERE placeholders. Use the stored bundle even
        // before that locale has been visited; never borrow another language's latest cache row.
        args.addAll(0, listOf(prefix, locale.language, locale.country))
        "COALESCE(NULLIF((SELECT TRIM(title) FROM metadata_cache WHERE key = ? || match.tmdbId), ''), (SELECT COALESCE(" +
            "(SELECT NULLIF(TRIM(json_extract(t.value, '$.title')), '') FROM json_each(b.overview, '$.texts') t " +
            "WHERE json_extract(t.value, '$.language') = ? AND NULLIF(TRIM(json_extract(t.value, '$.title')), '') IS NOT NULL " +
            "ORDER BY CASE WHEN json_extract(t.value, '$.region') = ? THEN 0 ELSE 1 END, t.key LIMIT 1), " +
            "NULLIF(TRIM(json_extract(b.overview, '$.title')), ''), " +
            "(SELECT NULLIF(TRIM(json_extract(t.value, '$.title')), '') FROM json_each(b.overview, '$.texts') t " +
            "WHERE json_extract(t.value, '$.language') = 'en' AND NULLIF(TRIM(json_extract(t.value, '$.title')), '') IS NOT NULL LIMIT 1)) " +
            "FROM metadata_cache b WHERE b.key = 'bundle:$type:' || match.tmdbId AND json_valid(b.overview)), " +
            "NULLIF((SELECT TRIM(title) FROM metadata_cache WHERE key IN ('$type:en-US:' || match.tmdbId, '$type:en:' || match.tmdbId) " +
            "ORDER BY key DESC LIMIT 1), ''))"
    }
    val select = if (countOnly) "COUNT(*)" else if (genresOnly) (if (metadataMode.enrich) "DISTINCT meta.genresJson" else "DISTINCT NULL AS genresJson")
        else if (valuesOnly) "m.id, $year AS year, $rating AS rating" else if (alphabetical) "m.*, $displayTitle AS metadataTitle" else "m.*"
    val metadataJoin = if (!metadataMode.enrich) "" else
        "LEFT JOIN metadata_match match ON match.localKey = ? || m.sourceId || ':' || COALESCE(m.remoteId, m.name) || ? " +
        // Prefer the selected language, but retain identity data for filters until localized
        // details are fetched on focus. Switching languages must not empty the catalogue.
        "LEFT JOIN metadata_cache meta ON meta.key = COALESCE((SELECT key FROM metadata_cache WHERE key = ? || match.tmdbId), " +
        "(SELECT key FROM metadata_cache WHERE tmdbId = match.tmdbId AND type = '$type' ORDER BY updatedAt DESC LIMIT 1)) " +
        ""
    val sql = "SELECT $select FROM $table m " + metadataJoin +
        manualJoin +
        "WHERE " + conditions.joinToString(" AND ")
    if (countOnly || genresOnly) return DiscoveryQuery(sql, args)
    val ordering = when (order) {
        DiscoveryOrder.POPULAR -> {
            args += media.name
            // A missing trend rank goes after ranked titles, never masquerades as a score.
            "COALESCE((SELECT MIN(t.trendingRank) FROM trending_items t WHERE t.sourceId = m.sourceId AND t.providerItemId = m.id AND t.mediaType = ?), 2147483647) ASC, m.sortOrder ASC, m.id ASC"
        }
        DiscoveryOrder.RANDOM -> {
            // Bound odd multiplier: repeatable across every paging query, below SQLite overflow.
            args += kotlin.random.Random(randomSeed).nextLong(100000001L, 2000000000L)
            "((m.id % 2147483647) * ? % 2147483647) ASC, m.id ASC"
        }
        // LocalePagingSource orders globally with the requested locale; avoid sorting by binary
        // title here as well (and evaluating every JSON title for a redundant SQLite sort).
        DiscoveryOrder.TITLE -> if (alphabetical) "m.id ASC" else "m.name ASC, m.id ASC"
        DiscoveryOrder.RATING -> "$rating DESC, m.name ASC, m.id ASC"
        DiscoveryOrder.ADDED -> "m.addedAt DESC, m.id DESC"
        DiscoveryOrder.PROVIDER -> (if (manual == null) "" else "CASE WHEN o.position IS NULL THEN 1 ELSE 0 END, o.position, ") +
            "m.sortOrder ASC, m.name ASC, m.id ASC"
    }
    return DiscoveryQuery(sql + " ORDER BY $ordering", args)
}
