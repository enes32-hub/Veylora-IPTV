package tv.own.owntv.core.settings

/** Read catalog sort preferences, preserving all supported selections. */
internal fun parseVodSortPreference(raw: String?): SettingsRepository.SortMode {
    val mode = raw?.let { runCatching { SettingsRepository.SortMode.valueOf(it) }.getOrNull() }
        ?: SettingsRepository.SortMode.PLAYLIST
    return if (mode == SettingsRepository.SortMode.ALPHA) SettingsRepository.SortMode.PLAYLIST else mode
}
