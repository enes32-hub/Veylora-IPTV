package tv.own.owntv.features.discovery

import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One shuffle seed shared by every movie/series query in a single foreground app session. */
class DiscoverySessionSeed(
    seedFactory: () -> Long = { Random.nextLong(1, 0x3fffffffL) },
) {
    private val createSeed = seedFactory
    private val _value = MutableStateFlow(createSeed())
    val state: StateFlow<Long> = _value.asStateFlow()
    val value: Long get() = _value.value

    fun beginSession() {
        _value.value = createSeed()
    }
}

/** A fresh process gets an initial seed; MainActivity rotates it when the app returns from background. */
object DiscoverySessionRandom {
    private val seed = DiscoverySessionSeed()
    val sessionSeed: StateFlow<Long> = seed.state
    val value: Long get() = seed.value
    fun beginSession() = seed.beginSession()
}
