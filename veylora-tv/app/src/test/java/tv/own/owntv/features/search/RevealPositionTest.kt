package tv.own.owntv.features.search

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RevealPositionTest {
    private fun pages() = object : PagingSource<Int, Long>() {
        override fun getRefreshKey(state: PagingState<Int, Long>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Long> {
            val offset = params.key ?: 0
            val rows = (1L..6L).toList().drop(offset).take(2)
            return LoadResult.Page(rows, null, if (offset + rows.size < 6) offset + rows.size else null)
        }
    }
    @Test fun hiddenRowsAcrossPagesDoNotShiftRevealPosition() = runBlocking {
        assertEquals(1, pages().positionOf(5, { it }, isVisible = { it % 2L == 1L && it != 3L }))
    }
    @Test fun hiddenTargetIsNotRevealed() = runBlocking {
        assertNull(pages().positionOf(4, { it }, isVisible = { it % 2L == 1L }))
    }
}
