package com.kiroland.mediacenter.data.transfer

import com.kiroland.mediacenter.data.transfer.PairingGuard.Check
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingGuardTest {

    private var now = 0L
    private val guard = PairingGuard(clock = { now }, maxFailures = 3, lockoutMs = 60_000)

    @Test
    fun `code is accepted in any typed form`() {
        assertEquals(Check.Ok, guard.check("K7M2QP9X", "k7m2-qp9x"))
        assertEquals(Check.Ok, guard.check("K7M2QP9X", "K7M2 QP9X"))
        assertEquals("K7M2-QP9X", PairingGuard.display("k7m2qp9x"))
    }

    @Test
    fun `repeated wrong codes lock out even the right one for a while`() {
        repeat(3) { assertEquals(Check.Wrong, guard.check("GOODCODE", "BADCODE$it")) }
        assertTrue(guard.check("GOODCODE", "GOODCODE") is Check.Locked)
        now += 60_001
        assertEquals(Check.Ok, guard.check("GOODCODE", "GOODCODE"))
    }

    @Test
    fun `missing code counts as wrong`() {
        assertEquals(Check.Wrong, guard.check("GOODCODE", null))
    }

    @Test
    fun `generated codes avoid look-alike characters`() {
        repeat(200) {
            val code = PairingGuard.newCode()
            assertEquals(8, code.length)
            assertTrue(code, code.none { it in "01OIL" })
        }
    }
}
