package tv.own.owntv.core.catalog

data class DiscoveryFilter(
    val genre: String? = null,
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val minRating: Double? = null,
    val maxRating: Double? = null,
    val watched: Boolean? = null,
) {
    init {
        require(minYear == null || minYear in 1800..9999)
        require(maxYear == null || maxYear in 1800..9999)
        require(minYear == null || maxYear == null || minYear <= maxYear)
        require(minRating == null || minRating.isFinite() && minRating in 0.0..10.0)
        require(maxRating == null || maxRating.isFinite() && maxRating in 0.0..10.0)
        require(minRating == null || maxRating == null || minRating <= maxRating)
    }

    val activeCount: Int get() = listOf(!genre.isNullOrBlank(), minYear != null || maxYear != null,
        minRating != null || maxRating != null, watched != null).count { it }

    /** Unknown metadata is not fabricated to satisfy an active range. No duration restriction. */
    fun accepts(year: Int?, rating: Double?, genres: List<String>, isWatched: Boolean = false): Boolean {
        if (watched != null && watched != isWatched) return false;
        if (rating != null && (!rating.isFinite() || rating !in 0.0..10.0)) return false
        if (minYear != null && (year == null || year < minYear)) return false
        if (maxYear != null && (year == null || year > maxYear)) return false
        if (minRating != null && (rating == null || rating < minRating)) return false
        if (maxRating != null && (rating == null || rating > maxRating)) return false
        if (!genre.isNullOrBlank() && genres.none { it.trim().equals(genre.trim(), ignoreCase = true) }) return false
        return true
    }
}
