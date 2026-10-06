package tv.own.owntv.core.network

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Retry-After permits either delta seconds or an HTTP date. */
internal fun retryAfterSeconds(value: String?, nowMs: Long = System.currentTimeMillis()): Long? {
    val text = value?.trim() ?: return null
    text.toLongOrNull()?.let { return it.coerceAtLeast(1) }
    return runCatching {
        val at = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        ((at - nowMs + 999) / 1000).coerceAtLeast(1)
    }.getOrNull()
}
