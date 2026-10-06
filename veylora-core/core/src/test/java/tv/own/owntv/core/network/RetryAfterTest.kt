package tv.own.owntv.core.network

import org.junit.Assert.*
import org.junit.Test

class RetryAfterTest {
    @Test fun `seconds and server HTTP dates preserve cooldown`() {
        assertEquals(120L, retryAfterSeconds("120", 0))
        assertEquals(120L, retryAfterSeconds("Thu, 01 Jan 1970 00:02:00 GMT", 0))
        assertEquals(1L, retryAfterSeconds("Thu, 01 Jan 1970 00:00:00 GMT", 1000))
        assertNull(retryAfterSeconds("invalid", 0))
        assertNull(retryAfterSeconds(null, 0))
    }
}
