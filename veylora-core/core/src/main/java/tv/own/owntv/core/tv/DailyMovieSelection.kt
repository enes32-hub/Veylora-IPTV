package tv.own.owntv.core.tv

import tv.own.owntv.core.database.entity.MovieEntity
import java.util.Locale
import kotlin.random.Random

/** Local catalogue selection only: no metadata or stream requests. */
internal fun dailyMovies(movies: List<MovieEntity>, profileId: Long, now: Long): List<MovieEntity> =
    movies.sortedBy { it.id }
        .filter { it.posterUrl?.startsWith("https://") == true || it.posterUrl?.startsWith("http://") == true }
        .distinctBy { (it.titleSignature.ifBlank { it.name.trim().lowercase(Locale.ROOT) }) to (it.year ?: it.parsedYear) }
        .shuffled(Random(now / 86_400_000L xor profileId))
        .take(4)
