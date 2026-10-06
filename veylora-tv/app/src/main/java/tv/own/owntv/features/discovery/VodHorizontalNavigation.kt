package tv.own.owntv.features.discovery

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection

/** Physical D-pad direction toward the start-side menu, matching the mirrored grid. */
internal class VodHorizontalNavigation(direction: LayoutDirection) {
    private val rtl = direction == LayoutDirection.Rtl
    val menuKey = if (rtl) Key.DirectionRight else Key.DirectionLeft
    val towardMenu = if (rtl) FocusDirection.Right else FocusDirection.Left
    val awayFromMenu = if (rtl) FocusDirection.Left else FocusDirection.Right

    fun leavesGrid(key: Key, index: Int, columns: Int): Boolean =
        key == menuKey && columns > 0 && index >= 0 && index % columns == 0
}
