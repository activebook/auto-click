package com.activebook.autoclick.app.domain.timing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalMathTest {

    @Test
    fun `target uptime is anchored, not incremental`() {
        val anchor = 1000L
        assertEquals(1000L + 1000L * 10, IntervalMath.targetUptime(anchor, 1000L, 10L))
        assertEquals(1000L + 5_000L * 100, IntervalMath.targetUptime(anchor, 5_000L, 100L))
    }

    @Test
    fun `plan keeps the anchor when on time`() {
        val plan = IntervalMath.plan(anchorUptime = 1000L, intervalMs = 1000L, nextTick = 3L, nowUptime = 2500L)
        assertEquals(1000L, plan.anchorUptime)
        assertEquals(3L, plan.tick)
        assertEquals(4000L, plan.targetUptime)
    }

    @Test
    fun `plan tolerates a small delay without drifting`() {
        // 40ms late on tick 3: the anchor must not move, so error never accumulates.
        val plan = IntervalMath.plan(anchorUptime = 1000L, intervalMs = 1000L, nextTick = 3L, nowUptime = 4040L)
        assertEquals(1000L, plan.anchorUptime)
        assertEquals(3L, plan.tick)
        assertEquals(4000L, plan.targetUptime)
    }

    @Test
    fun `plan rebases after a long suspension instead of replaying ticks`() {
        // Process was frozen for ~9s while tick 4 was due at 5000.
        val plan = IntervalMath.plan(anchorUptime = 1000L, intervalMs = 1000L, nextTick = 4L, nowUptime = 14_000L)
        assertEquals(14_000L, plan.anchorUptime)
        assertEquals(1L, plan.tick)
        assertEquals(15_000L, plan.targetUptime)
        assertTrue(plan.targetUptime > 14_000L)
    }

    @Test
    fun `plan does not rebase when exactly one period behind`() {
        val plan = IntervalMath.plan(anchorUptime = 1000L, intervalMs = 1000L, nextTick = 5L, nowUptime = 6000L)
        assertEquals(1000L, plan.anchorUptime)
        assertEquals(5L, plan.tick)
        assertEquals(6000L, plan.targetUptime)
    }

    @Test
    fun `one thousand ticks stay exactly on the anchor grid`() {
        val anchor = 12_345L
        val interval = 1000L
        var plan = IntervalMath.Plan(anchor, 0L, anchor)

        // Simulates a loop that wakes up 5ms late every time, for 1000 ticks.
        var simulatedNow = anchor
        for (tick in 1L..1000L) {
            simulatedNow = IntervalMath.targetUptime(anchor, interval, tick) + 5L
            plan = IntervalMath.plan(plan.anchorUptime, interval, tick, simulatedNow)
            assertEquals("tick $tick", anchor + interval * tick, plan.targetUptime)
        }
        assertEquals(1000L, plan.tick)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero interval is rejected`() {
        IntervalMath.plan(anchorUptime = 0L, intervalMs = 0L, nextTick = 1L, nowUptime = 0L)
    }
}
