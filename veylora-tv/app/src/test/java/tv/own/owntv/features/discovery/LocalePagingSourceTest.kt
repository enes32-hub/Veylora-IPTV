package tv.own.owntv.features.discovery

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalePagingSourceTest {
    @Test fun `application language controls alphabet independently from metadata language`() = runBlocking {
        val rows = listOf(Row(1,"Zebra"), Row(2,"Örebro"), Row(3,"Apple"))
        suspend fun ordered(ui: String, metadata: String): List<Long> {
            val config = tv.own.owntv.core.metadata.MetadataConfig(language = metadata, automaticLanguage = ui)
            val source = LocalePagingSource(upstream(rows), config, {it.title}, {it.id}, {it})
            return (source.load(PagingSource.LoadParams.Refresh(null,10,false)) as PagingSource.LoadResult.Page).data.map { it.id }
        }
        assertEquals(listOf(3L,1L,2L), ordered("sv-SE", "de-DE"))
        assertEquals(listOf(3L,1L,2L), ordered("sv-SE", "ja-JP"))
        assertEquals(listOf(3L,2L,1L), ordered("de-DE", "sv-SE"))
    }
    @Test fun `custom provider name is used only when localized title is absent`() {
        val movie = tv.own.owntv.core.database.entity.MovieEntity(id=1,sourceId=1,name="Apple",streamUrl="")
        val absent = tv.own.owntv.core.catalog.AlphabeticalMovie(movie,null)
        assertEquals("Zebra",absent.displayTitle("Zebra"))
        assertEquals("Apple",absent.copy(metadataTitle="Apple").displayTitle("Zebra"))
        val show = tv.own.owntv.core.database.entity.SeriesEntity(id=1,sourceId=1,name="Apple")
        assertEquals("Zebra",tv.own.owntv.core.catalog.AlphabeticalSeries(show,null).displayTitle("Zebra"))
    }
    private data class Row(val id: Long, val title: String)
    private fun upstream(rows: List<Row>) = object : PagingSource<Int, Row>() {
        override fun getRefreshKey(state: PagingState<Int, Row>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Row> {
            val start = params.key ?: 0
            val end = (start + params.loadSize).coerceAtMost(rows.size)
            return LoadResult.Page(rows.subList(start, end), null,
                end.takeIf { it < rows.size }, start, rows.size - end)
        }
    }
    @Test fun `Turkish alphabet sorts all results before slicing pages`() = runBlocking {
        val raw = upstream(listOf(Row(1,"Zebra"),Row(2,"Çam"),Row(3,"Ceviz"),Row(4,"Işık"),Row(5,"İpek")))
        val source = LocalePagingSource(raw, "tr-TR", { it.title }, { it.id }, { it })
        val first = source.load(PagingSource.LoadParams.Refresh(null, 2, false)) as PagingSource.LoadResult.Page
        val second = source.load(PagingSource.LoadParams.Append(first.nextKey!!, 3, false)) as PagingSource.LoadResult.Page
        assertEquals(listOf("Ceviz","Çam"),first.data.map { it.title })
        assertEquals(listOf("Işık","İpek","Zebra"),second.data.map { it.title })
        raw.invalidate()
        assertTrue(source.invalid)
    }
    @Test fun `locale changes the alphabet without discarding other scripts`() = runBlocking {
        val rows = listOf(Row(1,"Zebra"),Row(2,"Örebro"),Row(3,"Apple"))
        suspend fun ordered(locale: String): List<Long> {
            val source = LocalePagingSource(upstream(rows),locale,{it.title},{it.id},{it})
            return (source.load(PagingSource.LoadParams.Refresh(null,10,false)) as PagingSource.LoadResult.Page).data.map { it.id }
        }
        assertEquals(listOf(3L,2L,1L),ordered("de-DE"))
        assertEquals(listOf(3L,1L,2L),ordered("sv-SE"))
    }
    @Test fun `Japanese kana and stable ties retain every entry`() = runBlocking {
        val source = LocalePagingSource(upstream(listOf(Row(5,"か"),Row(3,"あ"),Row(2,"あ"),Row(7,"さ"))),
            "ja-JP",{it.title},{it.id},{it})
        val page = source.load(PagingSource.LoadParams.Refresh(null,20,false)) as PagingSource.LoadResult.Page
        assertEquals(listOf(2L,3L,5L,7L),page.data.map { it.id })
    }
}
