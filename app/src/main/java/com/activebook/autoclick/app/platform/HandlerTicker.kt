package com.activebook.autoclick.app.platform

import android.os.Handler
import android.os.HandlerThread
import com.activebook.autoclick.app.core.Ticker
import com.activebook.autoclick.app.domain.timing.IntervalMath

/**
 * A ticker that owns a dedicated [HandlerThread] and schedules each tick at an absolute uptime
 * derived from a single anchor (see [IntervalMath]).
 *
 * Why a Handler instead of AlarmManager: sub-second exact alarms do not exist in practice, and
 * one wakeup per tick would be battery hostile. A foreground service plus a looper gives
 * frame-level accuracy at effectively zero idle cost.
 *
 * The next tick is scheduled *before* running the callback, so a slow gesture dispatch does not
 * push the following tick late.
 */
class HandlerTicker(
    private val intervalMs: Long,
    private val clock: () -> Long,
    private val onTick: () -> Unit,
    threadName: String = "autoclick-ticker",
) : Ticker {

    private val thread = HandlerThread(threadName).apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    private var running = false

    private var anchorUptime = 0L
    private var nextTick = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            scheduleNext()
            onTick()
        }
    }

    override fun start() {
        if (running) return
        running = true
        anchorUptime = clock()
        nextTick = 0L
        scheduleNext()
    }

    override fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }

    private fun scheduleNext() {
        if (!running) return
        nextTick += 1
        val plan = IntervalMath.plan(anchorUptime, intervalMs, nextTick, clock())
        anchorUptime = plan.anchorUptime
        nextTick = plan.tick
        handler.postAtTime(tickRunnable, plan.targetUptime)
    }
}
