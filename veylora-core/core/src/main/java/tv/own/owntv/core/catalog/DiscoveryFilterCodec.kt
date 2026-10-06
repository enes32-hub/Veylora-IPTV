package tv.own.owntv.core.catalog

import org.json.JSONObject

object DiscoveryFilterCodec {
    fun encode(filter: DiscoveryFilter): String = JSONObject()
        .put("v", 1)
        .put("genre", filter.genre ?: JSONObject.NULL)
        .put("minYear", filter.minYear ?: JSONObject.NULL)
        .put("maxYear", filter.maxYear ?: JSONObject.NULL)
        .put("minRating", filter.minRating ?: JSONObject.NULL)
        .put("maxRating", filter.maxRating ?: JSONObject.NULL)
        .put("watched", filter.watched ?: JSONObject.NULL).toString()

    fun decode(raw: String?): DiscoveryFilter = runCatching {
        if (raw == null) return DiscoveryFilter()
        val value = JSONObject(raw)
        require(value.getInt("v") == 1)
        DiscoveryFilter(
            genre = if (value.isNull("genre")) null else value.getString("genre"),
            minYear = if (value.isNull("minYear")) null else value.getInt("minYear"),
            maxYear = if (value.isNull("maxYear")) null else value.getInt("maxYear"),
            minRating = if (!value.has("minRating")) null else if (value.isNull("minRating")) null else value.getDouble("minRating"),
            watched = if (value.isNull("watched")) null else value.getBoolean("watched"),
            maxRating = if (value.isNull("maxRating")) null else value.getDouble("maxRating"),
        )
    }.getOrElse { DiscoveryFilter() }
}
