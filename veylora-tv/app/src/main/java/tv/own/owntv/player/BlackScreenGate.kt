package tv.own.owntv.player

/** Consume the entire waking press so its release cannot click a control behind the overlay. */
internal class BlackScreenGate {
    var active: Boolean = false
        private set
    private var wakingKey: Int? = null

    fun activate() { active = true; wakingKey = null }

    fun consume(keyCode: Int, down: Boolean): Boolean {
        if (active) {
            if (down) { active = false; wakingKey = keyCode }
            return true
        }
        if (wakingKey == keyCode) {
            if (!down) wakingKey = null
            return true
        }
        return false
    }
}
