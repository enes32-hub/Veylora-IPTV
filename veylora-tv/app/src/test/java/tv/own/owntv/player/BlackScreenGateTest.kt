package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class BlackScreenGateTest {
    @Test fun `first key wakes only and its repeat and release are swallowed`() {
        val gate = BlackScreenGate()
        gate.activate()
        assertTrue(gate.active)
        assertTrue(gate.consume(23, true))
        assertFalse(gate.active)
        assertTrue(gate.consume(23, true))
        assertTrue(gate.consume(23, false))
        assertFalse(gate.consume(23, true))
    }
    @Test fun `ordinary controls are untouched before activation`() {
        val gate = BlackScreenGate()
        assertFalse(gate.consume(22, true))
        assertFalse(gate.consume(22, false))
    }
    @Test fun `activation button release does not immediately wake the screen`() {
        val gate = BlackScreenGate()
        gate.activate()
        assertTrue(gate.consume(23, false))
        assertTrue(gate.active)
        assertTrue(gate.consume(4, true))
        assertFalse(gate.active)
    }
}
