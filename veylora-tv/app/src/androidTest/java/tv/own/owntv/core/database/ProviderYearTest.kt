package tv.own.owntv.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

@RunWith(AndroidJUnit4::class)
class ProviderYearTest {
    @Test fun listYearAndReleaseDateSurviveStreamingParser() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""[{"stream_id":1,"name":"A","year":"1975"},
                    {"stream_id":2,"name":"B","release_date":"1999-03-31"},
                    {"stream_id":3,"name":"C","year":"bad"}]""".toResponseBody()).build()
        }.build()
        val client = XtreamClient(HttpClient(http))
        val rows = mutableListOf<XtVod>()
        val source = SourceEntity(id=1, name="Fixture", type=SourceType.XTREAM, url="https://example.test", username="fixture", password="fixture")
        client.streamVod(source, onItem={ rows.add(it) })
        assertEquals(listOf(1975,1999,null), rows.map { it.year })
    }
}
