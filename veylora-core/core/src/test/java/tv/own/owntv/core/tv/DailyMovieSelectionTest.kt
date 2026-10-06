package tv.own.owntv.core.tv

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.MovieEntity

class DailyMovieSelectionTest {
    private val movies = (1L..40L).map { MovieEntity(id=it, sourceId=1, name="Film $it", year=2000, streamUrl="", posterUrl="https://example.org/$it.jpg") }
    @Test fun fourUniqueStableForDayAndIndependentOfInputOrder() {
        val first = dailyMovies(movies, 7, 86400000L)
        assertEquals(4, first.size)
        assertEquals(4, first.map { it.id }.distinct().size)
        assertEquals(first, dailyMovies(movies.reversed(), 7, 86401000L))
        assertNotEquals(first, dailyMovies(movies, 7, 172800000L))
    }
    @Test fun duplicateTitlesAndMissingArtworkDoNotFillSlots() {
        val input = listOf(movies.first(), movies.first().copy(id=99), movies[1].copy(posterUrl=null))
        assertEquals(listOf(movies.first()), dailyMovies(input, 7, 0))
        assertTrue(dailyMovies(emptyList(), 7, 0).isEmpty())
    }
}
