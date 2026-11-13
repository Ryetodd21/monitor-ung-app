package com.monitorung.app.data.snapshot

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassUsageWindowTest {
    private val minute = 60_000L

    @Test
    fun `two classes exclude the gap and survive reconstruction`() {
        fun collect(start: Int, end: Int): Long {
            val usage = ClassUsageWindow(start * minute, end * minute)
            usage.resume("social", 0)
            usage.pause("social", 240 * minute)
            usage.close(300 * minute)
            return usage.millis.values.sum() / minute
        }
        assertEquals(60L, collect(60, 120))
        assertEquals(60L, collect(180, 240))
        assertEquals(60L, collect(180, 240))
    }

    @Test
    fun `fractions accumulate and repeated resumes do not reset the window`() {
        val usage = ClassUsageWindow(0, 60_000)
        usage.resume("social", 0)
        usage.resume("social", 10_000)
        usage.pause("social", 30_000)
        usage.resume("social", 30_000)
        usage.close(60_000)
        assertEquals(60_000L, usage.millis["social"])
    }

    @Test
    fun `screen off and delayed callbacks cannot extend the class`() {
        val usage = ClassUsageWindow(0, 60_000)
        usage.resume("social", 0)
        usage.close(10_000)
        usage.resume("game", 50_000)
        usage.pause("social", 55_000)
        usage.close(120_000)
        assertEquals(mapOf("social" to 10_000L, "game" to 10_000L), usage.millis)
    }
}
