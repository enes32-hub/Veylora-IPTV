package tv.own.owntv.features.discovery

internal suspend fun <T> focusedMetadata(
    cached: suspend () -> T?,
    waitForFocus: suspend () -> Unit,
    resolve: suspend () -> T?,
): T? {
    cached()?.let { return it }
    waitForFocus()
    return resolve()
}
