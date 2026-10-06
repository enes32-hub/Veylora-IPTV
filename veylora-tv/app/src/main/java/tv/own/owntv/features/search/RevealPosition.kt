package tv.own.owntv.features.search

import androidx.paging.PagingSource

/**
 * Search's "Go to": where [id] sits in a list's own query, read in large pages off the main thread so
 * the screen can scroll straight there instead of paging through the list on screen. Supply the
 * rendered list's visibility predicate to count only its displayed rows. Null when the target is
 * absent or hidden. The source itself must be the same query used by the rendered pager.
 */
suspend fun <T : Any> PagingSource<Int, T>.positionOf(id: Long, idOf: (T) -> Long, maxRows: Int = 60_000, isVisible: (T) -> Boolean = { true }): Int? {
    var offset = 0
    var visibleOffset = 0
    while (offset < maxRows) {
        val page = load(PagingSource.LoadParams.Append(offset, REVEAL_PAGE, false)) as? PagingSource.LoadResult.Page ?: return null
        val visible = page.data.filter(isVisible)
        val i = visible.indexOfFirst { idOf(it) == id }
        if (i >= 0) return visibleOffset + i
        if (page.data.isEmpty() || page.nextKey == null) return null
        offset += page.data.size
        visibleOffset += visible.size
    }
    return null
}

private const val REVEAL_PAGE = 1000

/** A pending "Go to": the category to show, the item, and its position in that category's list. */
data class Reveal(val key: tv.own.owntv.core.live.LiveKey, val id: Long, val position: Int)
