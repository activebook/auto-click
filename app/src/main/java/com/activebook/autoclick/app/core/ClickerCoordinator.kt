package com.activebook.autoclick.app.core

import com.activebook.autoclick.app.domain.geometry.ScreenPoint
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.domain.model.ClickerState
import com.activebook.autoclick.app.domain.model.FailureReason
import com.activebook.autoclick.app.domain.model.TapTarget
import com.activebook.autoclick.app.domain.model.TapOutcome
import com.activebook.autoclick.app.domain.model.TapResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the tap loop: who is running, at which cell, at what interval, and what happened last.
 *
 * Concurrency model
 * - Start/stop/pause/resume/apply-config are called from the main thread (UI, notification,
 *   service callbacks) and serialise on [lock].
 * - Ticks arrive on the ticker's own thread; they take [lock] only to read the session and to
 *   publish a state change, never while calling into the accessibility layer.
 * - Every session gets a monotonically increasing generation. Late gesture callbacks compare
 *   their captured generation against the current one, so a callback arriving after Stop can
 *   never resurrect a Running state or bump the counter.
 */
class ClickerCoordinator(
    private val configProvider: () -> ClickerConfig,
    private val displayProvider: DisplayProvider,
    private val sinkProvider: () -> ClickSink?,
    private val tickerFactory: (intervalMs: Long, onTick: () -> Unit) -> Ticker,
    private val clock: () -> Long,
) {

    private val lock = Any()

    private val _state = MutableStateFlow<ClickerState>(ClickerState.Idle)
    val state: StateFlow<ClickerState> = _state.asStateFlow()

    private val _tapEvents = MutableSharedFlow<ScreenPoint>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emitted once per delivered tap, with the exact point that was tapped. */
    val tapEvents: SharedFlow<ScreenPoint> = _tapEvents.asSharedFlow()

    private var ticker: Ticker? = null
    private var generation = 0L
    /**
     * Frozen at Start, re-resolved against the live screen area on every tick. Cells and exact
     * points therefore travel through the identical resolver, guard checks included.
     */
    private var sessionTarget: TapTarget = TapTarget.CenterCell
    private var sessionIntervalMs = ClickerConfig.DEFAULT_INTERVAL_MS
    private var sessionBudget = ClickerConfig.DEFAULT_STOP_AFTER_TAPS
    private var startedAtUptime = 0L
    private var paused = false
    private var lastOutcome = TapOutcome.NONE
    private var lastTapAtUptime: Long? = null

    private val inFlight = AtomicBoolean(false)
    private val tapCount = AtomicLong(0)
    private val skippedTicks = AtomicLong(0)

    @Volatile
    private var guard: OverlayGuard? = null

    fun setGuard(overlayGuard: OverlayGuard?) {
        guard = overlayGuard
    }

    /**
     * Starts a run. Preconditions are validated up front so that a refusal always carries a
     * reason the UI can explain, instead of failing silently on the first tick.
     */
    fun start() {
        synchronized(lock) {
            if (ticker != null || paused) return

            val sink = sinkProvider()
            if (sink == null || !sink.isConnected) {
                failLocked(FailureReason.NO_ACCESSIBILITY)
                return
            }

            val config = configProvider()
            val area = displayProvider.usableArea()
            if (area.isEmpty) {
                failLocked(FailureReason.DISPLAY_UNAVAILABLE)
                return
            }

            val target = config.target.resolveIn(area)
            val overlay = guard
            if (overlay != null && overlay.overlaps(target)) {
                failLocked(FailureReason.TARGET_COVERED_BY_BUBBLE)
                return
            }

            generation += 1
            val sessionGeneration = generation
            sessionTarget = config.target
            sessionIntervalMs = config.intervalMs
            sessionBudget = config.stopAfterTaps
            startedAtUptime = clock()
            paused = false
            lastOutcome = TapOutcome.NONE
            lastTapAtUptime = null
            tapCount.set(0)
            skippedTicks.set(0)
            inFlight.set(false)

            val newTicker = tickerFactory(sessionIntervalMs) { onTick(sessionGeneration) }
            ticker = newTicker
            _state.value = ClickerState.Running(
                startedAtUptime = startedAtUptime,
                tapCount = 0,
                skippedTicks = 0,
                lastOutcome = TapOutcome.NONE,
                lastTapAtUptime = null,
                paused = false,
            )
            newTicker.start()
        }
    }

    /** Stops immediately. No tap can be delivered afterwards. */
    fun stop() {
        synchronized(lock) {
            val oldTicker = ticker
            ticker = null
            generation += 1
            paused = false
            inFlight.set(false)
            oldTicker?.stop()
            _state.value = ClickerState.Idle
        }
    }

    /** Pauses a running session without losing its counters (used when the screen turns off). */
    fun pause() {
        synchronized(lock) {
            val oldTicker = ticker ?: return
            ticker = null
            generation += 1
            inFlight.set(false)
            paused = true
            oldTicker.stop()
            publishRunningLocked()
        }
    }

    /** Resumes a paused session, re-validating the sink so a revoked permission stops cleanly. */
    fun resume() {
        synchronized(lock) {
            if (!paused) return
            val sink = sinkProvider()
            if (sink == null || !sink.isConnected) {
                failLocked(FailureReason.NO_ACCESSIBILITY)
                return
            }
            paused = false
            generation += 1
            val sessionGeneration = generation
            val newTicker = tickerFactory(sessionIntervalMs) { onTick(sessionGeneration) }
            ticker = newTicker
            publishRunningLocked()
            newTicker.start()
        }
    }

    /**
     * Applies new settings to a live session. Changing the interval rebuilds the ticker (which
     * re-anchors, so the new period takes effect on the next tick); changing the target — a cell
     * or an exact point — is picked up by the next tick automatically. Counters are preserved.
     */
    fun applyConfig(config: ClickerConfig) {
        synchronized(lock) {
            val newTarget = config.target
            if (ticker == null) {
                // Not running (or paused): the next start() uses the latest config anyway.
                sessionTarget = newTarget
                sessionIntervalMs = config.intervalMs
                sessionBudget = config.stopAfterTaps
                if (paused && sessionBudget > 0 && tapCount.get() >= sessionBudget.toLong()) {
                    finishLocked()
                }
                return
            }
            sessionBudget = config.stopAfterTaps
            // Lowering the budget under a session that already spent it ends the session now,
            // rather than delivering one more tap the user just said they did not want.
            if (sessionBudget > 0 && tapCount.get() >= sessionBudget.toLong()) {
                finishLocked()
                return
            }

            // Retargeting needs no new ticker: the next tick resolves the new target itself,
            // whether it moved a cell or moved to an exact pixel. Only the interval re-anchors.
            sessionTarget = newTarget
            if (config.intervalMs == sessionIntervalMs) {
                return
            }

            sessionIntervalMs = config.intervalMs

            val previousTicker = ticker
            generation += 1
            val sessionGeneration = generation
            inFlight.set(false)
            val newTicker = tickerFactory(sessionIntervalMs) { onTick(sessionGeneration) }
            ticker = newTicker
            previousTicker?.stop()
            publishRunningLocked()
            newTicker.start()
        }
    }

    /** Called when the accessibility service attaches or detaches. */
    fun onSinkChanged() {
        synchronized(lock) {
            val sink = sinkProvider()
            if (sink != null && sink.isConnected) {
                // A service that has just attached proves the earlier "disconnected" is over. The
                // error is deliberately sticky while the sink is gone, but it must not outlive the
                // condition it describes: a stale banner on top of a working permission is the
                // worst of both worlds.
                val current = _state.value
                if (current is ClickerState.Error && current.reason == FailureReason.SERVICE_DISCONNECTED) {
                    _state.value = ClickerState.Idle
                }
                return
            }
            if (ticker != null || paused) failLocked(FailureReason.SERVICE_DISCONNECTED)
        }
    }

    /**
     * Reports an external precondition failure (for example the overlay window could not be
     * created) so the UI shows the same error channel as engine failures.
     */
    fun reportFailure(reason: FailureReason, detail: String? = null) {
        synchronized(lock) {
            failLocked(reason, detail)
        }
    }

    /** Dismisses a terminal error so the UI returns to Idle after the user fixes the cause. */
    fun acknowledgeError() {
        synchronized(lock) {
            if (_state.value is ClickerState.Error) {
                _state.value = ClickerState.Idle
            }
        }
    }

    private fun onTick(sessionGeneration: Long) {
        val sink = sinkProvider()
        if (sink == null || !sink.isConnected) {
            synchronized(lock) {
                if (sessionGeneration == generation) {
                    failLocked(FailureReason.SERVICE_DISCONNECTED)
                }
            }
            return
        }

        var target: ScreenPoint? = null
        synchronized(lock) {
            if (sessionGeneration != generation) return

            val area = displayProvider.usableArea()
            if (area.isEmpty) {
                failLocked(FailureReason.DISPLAY_UNAVAILABLE)
                return
            }

            val point = sessionTarget.resolveIn(area)
            val overlay = guard
            if (overlay != null && overlay.overlaps(point)) {
                skippedTicks.incrementAndGet()
                lastOutcome = TapOutcome.SKIPPED_BY_BUBBLE
                publishRunningLocked()
                return
            }

            if (!inFlight.compareAndSet(false, true)) {
                skippedTicks.incrementAndGet()
                lastOutcome = TapOutcome.SKIPPED_IN_FLIGHT
                publishRunningLocked()
                return
            }
            target = point
        }

        val point = target ?: return
        val accepted = try {
            sink.tap(point.x, point.y, TAP_DURATION_MS) { result ->
                onTapResult(sessionGeneration, point, result)
            }
        } catch (error: RuntimeException) {
            false
        }

        if (!accepted) {
            inFlight.set(false)
            synchronized(lock) {
                if (sessionGeneration == generation) {
                    skippedTicks.incrementAndGet()
                    lastOutcome = TapOutcome.SKIPPED_IN_FLIGHT
                    publishRunningLocked()
                }
            }
        }
    }

    /** Arrives on the main thread from the accessibility gesture callback. */
    private fun onTapResult(sessionGeneration: Long, point: ScreenPoint, result: TapResult) {
        synchronized(lock) {
            // A stale callback must not clear the in-flight flag of the *new* session.
            if (sessionGeneration != generation) return
            inFlight.set(false)

            when (result) {
                TapResult.DELIVERED -> {
                    tapCount.incrementAndGet()
                    lastOutcome = TapOutcome.DELIVERED
                    lastTapAtUptime = clock()
                    _tapEvents.tryEmit(point)
                    // The budget is counted in *delivered* taps, not ticks: a skipped tick must not
                    // push the finish line, or the run would end short of what the user asked for.
                    if (sessionBudget > 0 && tapCount.get() >= sessionBudget.toLong()) {
                        finishLocked()
                    } else {
                        publishRunningLocked()
                    }
                }

                TapResult.CANCELLED -> {
                    skippedTicks.incrementAndGet()
                    lastOutcome = TapOutcome.CANCELLED
                    publishRunningLocked()
                }
            }
        }
    }

    /** Ends a session because it reached its tap budget. The counters survive in [ClickerState.Finished]. */
    private fun finishLocked() {
        val oldTicker = ticker
        ticker = null
        generation += 1
        paused = false
        inFlight.set(false)
        oldTicker?.stop()
        _state.value = ClickerState.Finished(
            tapCount = tapCount.get(),
            skippedTicks = skippedTicks.get(),
        )
    }

    private fun failLocked(reason: FailureReason, detail: String? = null) {
        val oldTicker = ticker
        ticker = null
        generation += 1
        paused = false
        inFlight.set(false)
        oldTicker?.stop()
        _state.value = ClickerState.Error(reason = reason, detail = detail)
    }

    /** Publishes counters onto the existing Running state; never revives a stopped session. */
    private fun publishRunningLocked() {
        val current = _state.value
        if (current !is ClickerState.Running) return
        _state.value = current.copy(
            tapCount = tapCount.get(),
            skippedTicks = skippedTicks.get(),
            lastOutcome = lastOutcome,
            lastTapAtUptime = lastTapAtUptime,
            paused = paused,
        )
    }

    companion object {
        /** Long enough to be a real tap, short enough not to be read as a long press. */
        const val TAP_DURATION_MS = 55L
    }
}
