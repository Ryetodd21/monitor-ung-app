package com.monitorung.app.data.snapshot

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UsageStatsManager returns the day's running total, not a delta. Two
 * collections 45 minutes apart report the same cumulative numbers, so the
 * store must subtract the previous snapshot. Rounds 13 and 26 both shipped
 * bugs in this area — doubled screen time, and a `wasReset` flag that meant
 * callers wrote absolute totals with accumulate semantics.
 */
class UsageSnapshotStoreTest {

    private val today = "2026-10-30"

    @Test
    fun `first collection is a full reset returning cumulative totals`() = runBlocking {
        val store = UsageSnapshotStore()
        val (deltas, wasReset) = store.computeDeltas(today, mapOf("com.ig" to 60_000L))

        assertTrue("first call has no previous snapshot", wasReset)
        assertEquals(60_000L, deltas["com.ig"])
    }

    @Test
    fun `repeated cumulative total yields zero delta`() = runBlocking {
        val store = UsageSnapshotStore()
        store.computeDeltas(today, mapOf("com.ig" to 60_000L))
        val (deltas, wasReset) = store.computeDeltas(today, mapOf("com.ig" to 60_000L))

        assertFalse("same day is not a reset", wasReset)
        assertEquals(0L, deltas["com.ig"])
    }

    @Test
    fun `growing cumulative total yields the increment`() = runBlocking {
        val store = UsageSnapshotStore()
        store.computeDeltas(today, mapOf("com.ig" to 60_000L))
        val (deltas, _) = store.computeDeltas(today, mapOf("com.ig" to 90_000L))

        assertEquals(30_000L, deltas["com.ig"])
    }

    @Test
    fun `new day resets the snapshot`() = runBlocking {
        val store = UsageSnapshotStore()
        store.computeDeltas(today, mapOf("com.ig" to 60_000L))
        val (deltas, wasReset) = store.computeDeltas("2026-10-31", mapOf("com.ig" to 5_000L))

        assertTrue(wasReset)
        assertEquals(5_000L, deltas["com.ig"])
    }

    @Test
    fun `total dropping never produces a negative delta`() = runBlocking {
        // Clock correction or package reinstall — must not corrupt the running sum.
        val store = UsageSnapshotStore()
        store.computeDeltas(today, mapOf("com.ig" to 90_000L))
        val (deltas, _) = store.computeDeltas(today, mapOf("com.ig" to 10_000L))

        assertEquals(10_000L, deltas["com.ig"])
    }

    @Test
    fun `reset drops the snapshot`() = runBlocking {
        val store = UsageSnapshotStore()
        store.computeDeltas(today, mapOf("com.ig" to 60_000L))
        store.reset()
        val (deltas, wasReset) = store.computeDeltas(today, mapOf("com.ig" to 20_000L))

        assertTrue(wasReset)
        assertEquals(20_000L, deltas["com.ig"])
    }
}
