package tv.own.owntv.features.movies

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.catalog.DiscoveryValues
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.metadata.MetadataMode
import tv.own.owntv.features.series.seriesTitleInfo

class DiscoveryDisplayTest {
    @Test fun `unmatched movie and series retain provider presentation`() {
        val movie = MovieEntity(id=1,sourceId=1,name="Provider title",plot="Provider plot",posterUrl="https://example.org/p.jpg",streamUrl="")
        val series = SeriesEntity(id=2,sourceId=1,name="Provider show",plot="Series plot",posterUrl="https://example.org/s.jpg")
        val info = movieTitleInfo(movie,null,MetadataMode.PROVIDER_PLUS_TMDB)
        assertEquals("Provider title", info.title)
        assertEquals("Provider plot", info.plot)
        assertEquals(movie.posterUrl, info.posterUrl)
        val show = seriesTitleInfo(series,null,MetadataMode.PROVIDER_PLUS_TMDB)
        assertEquals("Provider show", show.title)
        assertEquals("Series plot", show.plot)
        assertEquals(series.posterUrl, show.posterUrl)
    }
    @Test fun `translated metadata is displayed instead of provider language`() {
        val meta = tv.own.owntv.core.database.entity.MetadataCacheEntity("movie:tr:42",42,null,"movie",
            "Çevrilmiş ad",1975,"Çevrilmiş açıklama","/tr.jpg",null,8.0,null,null,null,null,1)
        val movie = MovieEntity(id=1,sourceId=1,name="Provider title",plot="Provider plot",posterUrl="https://example.org/en.jpg",streamUrl="")
        val info = movieTitleInfo(movie,meta,MetadataMode.PROVIDER_PLUS_TMDB)
        assertEquals("Çevrilmiş ad",info.title)
        assertEquals("Çevrilmiş açıklama",info.plot)
        assertTrue(info.posterUrl!!.endsWith("/tr.jpg"))
        val series = SeriesEntity(id=2,sourceId=1,name="Provider show",plot="Provider plot")
        assertEquals("Çevrilmiş ad",seriesTitleInfo(series,meta.copy(type="tv"),MetadataMode.PROVIDER_PLUS_TMDB).title)
    }
    @Test fun `movie hero displays the same effective values as its filter`() {
        val movie = MovieEntity(id=1, sourceId=1, name="Film", year=2014, rating=4.0, streamUrl="")
        val info = movieTitleInfo(movie, null, MetadataMode.TMDB_ONLY, DiscoveryValues(1,1975,8.7))
        assertEquals(1975, info.year)
        assertEquals(8.7, info.rating!!, 0.001)
    }
    @Test fun `series hero displays the same effective values as its filter`() {
        val series = SeriesEntity(id=2, sourceId=1, name="Series", year=2000, rating=4.0)
        val info = seriesTitleInfo(series, null, MetadataMode.TMDB_ONLY, DiscoveryValues(2,2001,8.0))
        assertEquals(2001, info.year)
        assertEquals(8.0, info.rating!!, 0.001)
    }
}
