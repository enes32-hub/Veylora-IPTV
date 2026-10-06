package tv.own.owntv.core.settings

/** Null means mixed per-source choices, not that another profile's choice should be displayed. */
fun playlistRefreshSelection(sourceIds: List<Long>, selections: Map<Long, PlaylistRefresh>): PlaylistRefresh? {
    if (sourceIds.isEmpty()) return PlaylistRefresh.OFF
    return sourceIds.map { selections[it] ?: PlaylistRefresh.OFF }.distinct().singleOrNull()
}
