package com.activebook.autoclick.app.domain.timing

/**
 * Drift-free interval planning.
 *
 * Every target is computed from a single anchor instead of "now + interval", so a late wake-up
 * never accumulates error: tick *n* is always `anchor + n * interval`. If the process was
 * suspended for longer than one interval, the schedule is rebound to the current time and the
 * missed ticks are dropped rather than fired in a burst.
 *
 * All times are monotonic uptime milliseconds (`SystemClock.uptimeMillis`).
 */
object IntervalMath {

    /** A schedule decision: where the anchor is, which tick comes next, and when it fires. */
    data class Plan(
        val anchorUptime: Long,
        val tick: Long,
        val targetUptime: Long,
    )

    fun targetUptime(anchorUptime: Long, intervalMs: Long, tick: Long): Long =
        anchorUptime + intervalMs * tick

    /**
     * Decides when [nextTick] should fire.
     *
     * @param anchorUptime anchor established when the session started.
     * @param intervalMs validated positive interval.
     * @param nextTick 1-based index of the tick about to be scheduled.
     * @param nowUptime current monotonic time.
     */
    fun plan(
        anchorUptime: Long,
        intervalMs: Long,
        nextTick: Long,
        nowUptime: Long,
    ): Plan {
        require(intervalMs > 0) { "intervalMs must be positive, was $intervalMs" }
        require(nextTick > 0) { "nextTick must be positive, was $nextTick" }

        val target = targetUptime(anchorUptime, intervalMs, nextTick)
        return if (nowUptime - target > intervalMs) {
            // More than one whole period behind: rebind instead of replaying missed ticks.
            val rebasedTick = 1L
            Plan(
                anchorUptime = nowUptime,
                tick = rebasedTick,
                targetUptime = targetUptime(nowUptime, intervalMs, rebasedTick),
            )
        } else {
            Plan(anchorUptime = anchorUptime, tick = nextTick, targetUptime = target)
        }
    }
}
