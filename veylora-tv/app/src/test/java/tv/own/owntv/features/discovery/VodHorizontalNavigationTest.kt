package tv.own.owntv.features.discovery

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test

class VodHorizontalNavigationTest {
    @Test fun `RTL first card left stays in grid and right opens menu`() {
        val navigation = VodHorizontalNavigation(LayoutDirection.Rtl)
        assertFalse(navigation.leavesGrid(Key.DirectionLeft, 0, 6))
        assertTrue(navigation.leavesGrid(Key.DirectionRight, 0, 6))
        assertFalse(navigation.leavesGrid(Key.DirectionRight, 1, 6))
        assertTrue(navigation.leavesGrid(Key.DirectionRight, 6, 6))
        assertEquals(Key.DirectionRight, navigation.menuKey)
        assertEquals(FocusDirection.Right, navigation.towardMenu)
        assertEquals(FocusDirection.Left, navigation.awayFromMenu)
    }
    @Test fun `LTR first card right stays in grid and left opens menu`() {
        val navigation = VodHorizontalNavigation(LayoutDirection.Ltr)
        assertFalse(navigation.leavesGrid(Key.DirectionRight, 0, 6))
        assertTrue(navigation.leavesGrid(Key.DirectionLeft, 0, 6))
        assertFalse(navigation.leavesGrid(Key.DirectionLeft, 1, 6))
        assertTrue(navigation.leavesGrid(Key.DirectionLeft, 6, 6))
        assertEquals(FocusDirection.Left, navigation.towardMenu)
        assertEquals(FocusDirection.Right, navigation.awayFromMenu)
    }
    @Test fun `vertical keys never open horizontal navigation`() {
        for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            val navigation = VodHorizontalNavigation(direction)
            assertFalse(navigation.leavesGrid(Key.DirectionDown, 0, 6))
            assertFalse(navigation.leavesGrid(Key.DirectionUp, 0, 6))
        }
    }
}
