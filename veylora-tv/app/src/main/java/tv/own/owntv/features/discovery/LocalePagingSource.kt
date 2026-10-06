package tv.own.owntv.features.discovery

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Collator
import java.util.Locale

/** Sort the complete filtered result, not individual pages. No artwork/network is loaded here. */
internal class LocalePagingSource<R : Any, T : Any>(
    private val source: PagingSource<Int, R>,
    private val language: String,
    private val title: (R) -> String,
    private val id: (R) -> Long,
    private val item: (R) -> T,
) : PagingSource<Int, T>() {
    constructor(source: PagingSource<Int, R>, config: tv.own.owntv.core.metadata.MetadataConfig,
        title: (R) -> String, id: (R) -> Long, item: (R) -> T) :
        this(source, config.automaticLanguage, title, id, item)

    private val lock = Mutex()
    private var sorted: List<T>? = null
    init {
        source.registerInvalidatedCallback { invalidate() }
        registerInvalidatedCallback { source.invalidate() }
    }
    override fun getRefreshKey(state: PagingState<Int, T>): Int? = state.anchorPosition
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = withContext(Dispatchers.Default) {
        try {
            val rows = lock.withLock {
                sorted ?: run {
                    val all = mutableListOf<R>()
                    var key: Int? = null
                    do {
                        val page = source.load(LoadParams.Refresh(key, Int.MAX_VALUE, false))
                        when (page) {
                            is LoadResult.Page -> { all.addAll(page.data); key = page.nextKey }
                            is LoadResult.Error -> throw page.throwable
                            is LoadResult.Invalid -> return@withContext LoadResult.Invalid()
                        }
                    } while (key != null && !invalid)
                    if (invalid) return@withContext LoadResult.Invalid()
                    // Android's Collator uses ICU/CLDR for every supported locale. It does not
                    // guess phonetic readings of kanji or transliterate foreign-language titles.
                    val collator = Collator.getInstance(Locale.forLanguageTag(language.ifBlank { "en" })).apply {
                        strength = Collator.SECONDARY
                        decomposition = Collator.CANONICAL_DECOMPOSITION
                    }
                    val job = currentCoroutineContext()
                    // Generate each ICU key once, not for every comparison in O(n log n) sorting.
                    val keyed = all.map { row ->
                        job.ensureActive()
                        Triple(row, collator.getCollationKey(title(row)), id(row))
                    }
                    var comparisons = 0
                    keyed.sortedWith { a, b ->
                        if (++comparisons % 1024 == 0) job.ensureActive()
                        val compared = a.second.compareTo(b.second)
                        if (compared != 0) compared else a.third.compareTo(b.third)
                    }.map { item(it.first) }.also { sorted = it }
                }
            }
            if (invalid) return@withContext LoadResult.Invalid()
            val requested = params.key ?: 0
            val start = if (params is LoadParams.Prepend) (requested - params.loadSize).coerceAtLeast(0)
                else requested.coerceIn(0, rows.size)
            val end = if (params is LoadParams.Prepend) requested.coerceIn(start, rows.size)
                else (start.toLong() + params.loadSize).coerceAtMost(rows.size.toLong()).toInt()
            LoadResult.Page(rows.subList(start, end), start.takeIf { it > 0 },
                end.takeIf { it < rows.size }, start, rows.size - end)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { LoadResult.Error(error) }
    }
}
