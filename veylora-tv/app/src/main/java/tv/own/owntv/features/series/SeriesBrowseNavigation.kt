package tv.own.owntv.features.series

internal fun seriesReturnComplete(itemCount: Int, catalogCount: Int, focused: Boolean): Boolean =
    (itemCount == 0 && catalogCount == 0) || focused

/** Navigation decisions shared by the grid and list when returning from a show's episodes. */
internal class SeriesBrowseNavigation<Category> {
    private var previousCategory: Category? = null
    private var previouslyRemembered: Boolean? = null

    fun shouldReset(category: Category, remember: Boolean): Boolean {
        val changed = previousCategory != category || previouslyRemembered != remember
        previousCategory = category
        previouslyRemembered = remember
        return changed && !remember
    }
}

internal fun seriesReturnIndex(
    seriesId: Long?,
    previousIndex: Int,
    itemCount: Int,
    placeholdersBefore: Int,
    loadedIds: List<Long>,
): Int? {
    if (itemCount == 0) return null
    val loadedIndex = loadedIds.indexOf(seriesId)
    return if (loadedIndex >= 0) placeholdersBefore + loadedIndex
    else previousIndex.coerceIn(0, itemCount - 1)
}
