package tv.own.owntv.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRulesTest {
    @Test fun localizedHomeCardPreservesProviderIdentityAndPlayback() {
        val snapshot = tv.own.owntv.core.database.entity.TrendingItemEntity(1,0,42,
            tv.own.owntv.core.model.MediaType.MOVIE,1,7,"remote","stable","Provider","Canonical",
            null,null,null,"Old title",null,1975,"Old plot","/old.jpg",null,8.0,null,"generation",1)
        val movie = tv.own.owntv.core.database.entity.MovieEntity(id=7,sourceId=1,name="Provider",streamUrl="stream")
        val item = tv.own.owntv.core.home.TrendingHomeItem.Movie(snapshot,movie)
        val meta = tv.own.owntv.core.database.entity.MetadataCacheEntity("movie:tr:42",42,null,"movie",
            "Türkçe",1975,"Açıklama","/tr.jpg",null,8.0,null,null,null,null,1)
        val actual = localizedHomeItem(item,meta) as tv.own.owntv.core.home.TrendingHomeItem.Movie
        assertEquals("Türkçe",actual.snapshot.localizedTitle)
        assertEquals("/tr.jpg",actual.snapshot.posterPath)
        assertEquals(movie,actual.movie)
        assertEquals("stable",actual.snapshot.providerStableKey)
        val pending = localizedHomeItem(item,null)
        assertEquals("Provider", pending.snapshot.localizedTitle)
        assertNull(pending.snapshot.overview)
        assertNull(pending.snapshot.posterPath)
    }

    // --- Keep watching stills (G12): TMDB backdrop → provider backdrop → poster → channel logo ---

    @Test
    fun tmdbBackdropWins() = assertEquals(HomeStill.Picture("tmdb"), homeStill("tmdb", "prov", "poster", null))

    @Test
    fun providerBackdropWhenNoTmdb() = assertEquals(HomeStill.Picture("prov"), homeStill(null, "prov", "poster", null))

    @Test
    fun posterIsCroppedWhenNoBackdrop() = assertEquals(HomeStill.Picture("poster"), homeStill(" ", null, "poster", null))

    @Test
    fun channelShowsItsLogo() = assertEquals(HomeStill.Logo("logo"), homeStill(null, null, null, "logo"))

    @Test
    fun nothingAtAll() = assertEquals(HomeStill.None, homeStill(null, "", null, ""))

    // --- the Trending pager ---

    @Test
    fun rightGoesToNext() = assertEquals(3, trendingPagerTarget(2, 6, 1))

    @Test
    fun rightOnLastWraps() = assertEquals(0, trendingPagerTarget(5, 6, 1))

    @Test
    fun leftGoesToPrevious() = assertEquals(1, trendingPagerTarget(2, 6, -1))

    @Test
    fun leftOnFirstIsNotThePagers() = assertNull(trendingPagerTarget(0, 6, -1))

    @Test
    fun singleTitleNeverPages() = assertNull(trendingPagerTarget(0, 1, 1))
}
