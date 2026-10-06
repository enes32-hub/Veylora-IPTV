package tv.own.owntv.core.metadata

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Logical worker identity follows the coroutine through dispatcher changes. */
class MetadataWorker(val id: Int) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<MetadataWorker>
}

suspend fun <T> forEachMetadataItem(items: List<T>, action: suspend (T) -> Unit) = coroutineScope {
    val next = AtomicInteger()
    repeat(minOf(10, items.size)) { worker ->
        launch(MetadataWorker(worker)) {
            while (true) {
                val index = next.getAndIncrement()
                if (index >= items.size) break
                action(items[index])
            }
        }
    }
}
