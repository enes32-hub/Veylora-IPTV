package tv.own.owntv.features.discovery

/** Bounded presentation cache for a visible catalogue; navigation must not blank existing cards. */
internal class StablePresentationCache<T>(private val limit: Int = 500) {
    private var scope: Any? = null
    private val rows = linkedMapOf<Long, T>()
    private val checked = linkedSetOf<Long>()

    fun selectScope(next: Any): Boolean {
        if (scope == next) return false
        scope = next
        rows.clear()
        checked.clear()
        return true
    }

    fun update(requested: List<Long>, found: Map<Long, T>, pending: Set<Long> = emptySet()) {
        checked.removeAll(requested.toSet())
        checked.addAll(requested.filter { it !in pending })
        while (checked.size > limit.coerceAtLeast(requested.size)) checked.remove(checked.first())
        // Refresh requested entries (including deletions), keeping the overlapping/previous row stable.
        requested.forEach { rows.remove(it) }
        requested.forEach { id -> found[id]?.let { rows[id] = it } }
        while (rows.size > limit.coerceAtLeast(requested.size)) rows.remove(rows.keys.first())
    }

    fun snapshot(): Map<Long, T> = rows.toMap()
    fun checkedIds(): Set<Long> = checked.toSet()
}
