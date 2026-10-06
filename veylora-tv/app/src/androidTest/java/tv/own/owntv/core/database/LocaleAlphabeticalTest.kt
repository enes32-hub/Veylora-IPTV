package tv.own.owntv.core.database

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.features.discovery.LocalePagingSource
import tv.own.owntv.core.catalog.*
import androidx.room.useReaderConnection

@RunWith(AndroidJUnit4::class)
class LocaleAlphabeticalTest {
    private data class Row(val id: Long, val title: String)
    @Test fun actualAndroidLocaleRules() = runBlocking {
        suspend fun sorted(language: String, titles: List<String>): List<String> {
            val raw = object : PagingSource<Int,Row>() {
                override fun getRefreshKey(state: PagingState<Int,Row>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int,Row> =
                    LoadResult.Page(titles.mapIndexed { i,t -> Row(i.toLong(),t) },null,null)
            }
            return (LocalePagingSource(raw,language,{it.title},{it.id},{it.title})
                .load(PagingSource.LoadParams.Refresh(null,100,false)) as PagingSource.LoadResult.Page).data
        }
        assertEquals(listOf("Ceviz","Çam","Işık","İpek","Zebra"), sorted("tr-TR",listOf("Zebra","İpek","Çam","Işık","Ceviz")))
        assertEquals(listOf("Apple","Zebra","Örebro"), sorted("sv-SE",listOf("Örebro","Zebra","Apple")))
        assertEquals(listOf("Apple","Örebro","Zebra"), sorted("de-DE",listOf("Örebro","Zebra","Apple")))
        assertEquals(listOf("あ","か","さ"),sorted("ja-JP",listOf("さ","あ","か")))
        assertEquals(listOf("باب","تفاح","زيتون"),sorted("ar",listOf("زيتون","باب","تفاح")))
        assertEquals(listOf("北京","广州","上海"),sorted("zh-CN",listOf("上海","广州","北京")))
        val mixed = listOf("Zebra","Çam","Ceviz","İpek","Işık","あ","か","さ","東京","أمل","Яблоко")
        val result = sorted("ja-JP",mixed)
        assertEquals(mixed.toSet(),result.toSet())
        report("Japanese mixed-script ordering: ${result.joinToString(" → ")}")
        for (lang in listOf("ar", "zh-CN", "zh-TW")) {
            val other = sorted(lang,mixed)
            assertEquals(mixed.toSet(),other.toSet())
            report("$lang mixed-script ordering: ${other.joinToString(" → ")}")
        }
    }
    @Test fun installedCatalogueLoadsInBothLocales() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("catalogAudit") == "true")
        val db = org.koin.core.context.GlobalContext.get().get<OwnTVDatabase>()
        val ids = db.useReaderConnection { c -> c.usePrepared("SELECT DISTINCT sourceId FROM movies") { s ->
            buildList { while(s.step()) add(s.getLong(0)) }
        } }
        for (lang in listOf("tr-TR","ja-JP","ar","zh-CN")) {
            for (media in DiscoveryMedia.entries) {
                val start = android.os.SystemClock.elapsedRealtime()
                val q = discoveryQuery(media,ids,DiscoveryFilter(),lang)
                val sql = androidx.sqlite.db.SimpleSQLiteQuery(q.sql,q.arguments.toTypedArray())
                val source: PagingSource<Int,Long> = if(media==DiscoveryMedia.MOVIE)
                    LocalePagingSource(db.movieDao().alphabeticalPage(sql),lang,{it.displayTitle()},{it.item.id},{it.item.id})
                else LocalePagingSource(db.seriesDao().alphabeticalPage(sql),lang,{it.displayTitle()},{it.item.id},{it.item.id})
                val page = source.load(PagingSource.LoadParams.Refresh(null,60,false)) as PagingSource.LoadResult.Page
                assertEquals(60,page.data.size)
                assertTrue(page.itemsAfter>0)
                val next = source.load(PagingSource.LoadParams.Append(page.nextKey!!,60,false)) as PagingSource.LoadResult.Page
                assertTrue(page.data.intersect(next.data.toSet()).isEmpty())
                report("$media $lang count=${page.data.size+page.itemsAfter} loadMs=${android.os.SystemClock.elapsedRealtime()-start}")
                source.invalidate()
            }
        }
    }
    private fun report(value: String) = InstrumentationRegistry.getInstrumentation().sendStatus(0,
        android.os.Bundle().apply { putString("stream",value+"\n") })
}
