package tv.own.owntv.core.metadata

import org.json.JSONObject

/** Only title-level fields: never borrow an episode's or a recommendation's identity. */
data class ProviderMetadataIdentity(val tmdbId: Int? = null, val year: Int? = null) {
    companion object {
        fun parse(json: String): ProviderMetadataIdentity {
            val root = JSONObject(json)
            val objects = listOfNotNull(root.optJSONObject("info"), root.optJSONObject("movie_data"), root)
            val id = objects.firstNotNullOfOrNull { obj ->
                listOf("tmdb_id", "tmdb", "tmdbId").firstNotNullOfOrNull { field ->
                    obj.optString(field).trim().toIntOrNull()?.takeIf { it > 0 }
                }
            }
            val year = objects.firstNotNullOfOrNull { obj ->
                listOf("releasedate", "release_date", "releaseDate", "year", "first_air_date").firstNotNullOfOrNull { field ->
                    obj.optString(field).trim().take(4).toIntOrNull()?.takeIf { it in 1800..2200 }
                }
            }
            return ProviderMetadataIdentity(id, year)
        }
    }
}
