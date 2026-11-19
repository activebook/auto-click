package com.activebook.autoclick.app.core

import com.activebook.autoclick.app.domain.geometry.RelativePoint
import com.activebook.autoclick.app.domain.geometry.ScreenPoint
import com.activebook.autoclick.app.domain.geometry.ScreenRect
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.domain.model.ClickerState
import com.activebook.autoclick.app.domain.model.FailureReason
import com.activebook.autoclick.app.domain.model.TargetMode
import com.activebook.autoclick.app.domain.model.TapOutcome
import com.activebook.autoclick.app.domain.model.TapResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickerCoordinatorTest {

    private class FakeSink : ClickSink {
        override var isConnected: Boolean = true

        val taps = mutableListOf<Pair<Float, Float>>()
        val pendingResults = mutableListOf<(TapResult) -> Unit>()
        var acceptRequests = true

        override fun tap(
            x: Float,
            y: Float,
            durationMs: Long,
            onResult: (TapResult) -> Unit,
        ): Boolean {
            taps += x to y
            pendingResults += onResult
            return acceptRequests
        }

        fun deliverLatest() {
            val callback = pendingResults.removeAt(pendingResults.lastIndex)
            callback(TapResult.DELIVERED)
        }

        fun cancelLatest() {
            val callback = pendingResults.removeAt(pendingResults.lastIndex)
            callback(TapResult.CANCELLED)
        }
    }

    private class FakeTicker(val intervalMs: Long, val onTick: () -> Unit) : Ticker {
        var started = false
        var stopped = false

        override fun start() {
            started = true
        }

        override fun stop() {
            stopped = true
        }

        fun fire() = onTick()
    }

    private class Harness(initialConfig: ClickerConfig = ClickerConfig.Default) {
        val sink = FakeSink()
        var sinkAvailable = true
        var config: ClickerConfig = initialConfig
        var area: ScreenRect = ScreenRect(0f, 0f, 1080f, 1920f)
        var overlap = false
        var clock = 5_000L
        val tickers = mutableListOf<FakeTicker>()

        val coordinator = ClickerCoordinator(
            configProvider = { config },
            displayProvider = object : DisplayProvider {
                override fun usableArea(): ScreenRect = area
            },
            sinkProvider = { if (sinkAvailable) sink else null },
            tickerFactory = { intervalMs, onTick ->
                FakeTicker(intervalMs, onTick).also { tickers += it }
            },
            clock = { clock },
        ).also { coordinator ->
            coordinator.setGuard(object : OverlayGuard {
                override fun overlaps(point: ScreenPoint): Boolean = overlap
            })
        }

        val state: ClickerState get() = coordinator.state.value
    }

    @Test
    fun `start fails without an accessibility sink`() {
        val harness = Harness().apply { sinkAvailable = false }
        harness.coordinator.start()

        val state = harness.state
        assertTrue(state is ClickerState.Error)
        assertEquals(FailureReason.NO_ACCESSIBILITY, (state as ClickerState.Error).reason)
        assertTrue(harness.tickers.isEmpty())
    }

    @Test
    fun `start taps the exact center of the chosen cell`() {
        val harness = Harness()
        harness.coordinator.start()

        val running = harness.state as ClickerState.Running
        assertEquals(0L, running.tapCount)

        harness.tickers.single().fire()
        assertEquals(1, harness.sink.taps.size)
        assertEquals(540f, harness.sink.taps[0].first, 0.01f)
        assertEquals(960f, harness.sink.taps[0].second, 0.01f)

        harness.sink.deliverLatest()
        assertEquals(1L, (harness.state as ClickerState.Running).tapCount)
        assertEquals(TapOutcome.DELIVERED, (harness.state as ClickerState.Running).lastOutcome)
    }

    @Test
    fun `an exact point taps that pixel, not the centre of the cell it falls in`() {
        // 0.2 / 0.8 in a 1080x1920 area is (216, 1536) — nowhere near the middle cell's (540, 960).
        val exact = ClickerConfig.ofSeconds(
            cellIndex = 4,
            intervalSeconds = 1,
            stopAfterTaps = 0,
            hapticOnTap = true,
            flashOnTap = true,
            targetMode = TargetMode.POINT,
            point = RelativePoint.of(0.2f, 0.8f),
        )
        val harness = Harness(exact)
        harness.coordinator.start()
        harness.tickers.single().fire()

        assertEquals(216f, harness.sink.taps[0].first, 0.01f)
        assertEquals(1536f, harness.sink.taps[0].second, 0.01f)
    }

    @Test
    fun `an exact point follows the usable area when the bars move`() {
        val exact = ClickerConfig.ofSeconds(
            cellIndex = 4,
            intervalSeconds = 1,
            stopAfterTaps = 0,
            hapticOnTap = true,
            flashOnTap = true,
            targetMode = TargetMode.POINT,
            point = RelativePoint.of(0.5f, 0.5f),
        )
        val harness = Harness(exact)
        harness.coordinator.start()
        // Same fraction, smaller area: the tap must move with the area instead of staying put.
        harness.area = ScreenRect(0f, 200f, 1080f, 1720f)
        harness.tickers.single().fire()

        assertEquals(540f, harness.sink.taps[0].first, 0.01f)
        assertEquals(200f + 760f, harness.sink.taps[0].second, 0.01f)
    }

    @Test
    fun `retargeting to an exact point mid-run needs no restart and applies on the next tick`() {
        val harness = Harness()
        harness.coordinator.start()
        val firstTicker = harness.tickers.single()
        firstTicker.fire()
        harness.sink.deliverLatest()

        harness.config = ClickerConfig.ofSeconds(
            cellIndex = 4,
            intervalSeconds = 1,
            stopAfterTaps = 0,
            hapticOnTap = true,
            flashOnTap = true,
            targetMode = TargetMode.POINT,
            point = RelativePoint.of(0.9f, 0.1f),
        )
        harness.coordinator.applyConfig(harness.config)
        firstTicker.fire()

        // No second ticker: a target change must not re-anchor the schedule.
        assertEquals(1, harness.tickers.size)
        assertEquals(1080f * 0.9f, harness.sink.taps[1].first, 0.01f)
        assertEquals(1920f * 0.1f, harness.sink.taps[1].second, 0.01f)
    }

    @Test
    fun `a bubble over an exact point refuses the start for the same reason a cell does`() {
        val exact = ClickerConfig.ofSeconds(
            cellIndex = 4,
            intervalSeconds = 1,
            stopAfterTaps = 0,
            hapticOnTap = true,
            flashOnTap = true,
            targetMode = TargetMode.POINT,
            point = RelativePoint.of(0.3f, 0.3f),
        )
        val harness = Harness(exact)
        harness.overlap = true
        harness.coordinator.start()

        val state = harness.state
        assertTrue(state is ClickerState.Error)
        assertEquals(FailureReason.TARGET_COVERED_BY_BUBBLE, (state as ClickerState.Error).reason)
        assertTrue(harness.sink.taps.isEmpty())
    }

    @Test
    fun `top left cell taps one sixth in`() {
        val harness = Harness(ClickerConfig.ofSeconds(cellIndex = 0, intervalSeconds = 1, stopAfterTaps = 0, hapticOnTap = true, flashOnTap = true))
        harness.coordinator.start()
        harness.tickers.single().fire()

        assertEquals(180f, harness.sink.taps[0].first, 0.01f)
        assertEquals(320f, harness.sink.taps[0].second, 0.01f)
    }

    @Test
    fun `stop makes late gesture callbacks harmless`() {
        val harness = Harness()
        harness.coordinator.start()
        harness.tickers.single().fire()
        harness.coordinator.stop()
        harness.sink.deliverLatest()

        assertEquals(ClickerState.Idle, harness.state)
    }

    @Test
    fun `a tick is skipped while a gesture is still in flight`() {
        val harness = Harness()
        harness.coordinator.start()

        harness.tickers.single().fire()
        harness.tickers.single().fire()

        assertEquals(1, harness.sink.taps.size)
        val running = harness.state as ClickerState.Running
        assertEquals(0L, running.tapCount)
        assertEquals(1L, running.skippedTicks)
        assertEquals(TapOutcome.SKIPPED_IN_FLIGHT, running.lastOutcome)
    }

    @Test
    fun `cancelled gestures are counted as skipped, not delivered`() {
        val harness = Harness()
        harness.coordinator.start()
        harness.tickers.single().fire()
        harness.sink.cancelLatest()

        val running = harness.state as ClickerState.Running
        assertEquals(0L, running.tapCount)
        assertEquals(1L, running.skippedTicks)
        assertEquals(TapOutcome.CANCELLED, running.lastOutcome)
    }

    @Test
    fun `a bubble covering the target refuses to start`() {
        val harness = Harness().apply { overlap = true }
        harness.coordinator.start()

        val state = harness.state
        assertTrue(state is ClickerState.Error)
        assertEquals(FailureReason.TARGET_COVERED_BY_BUBBLE, (state as ClickerState.Error).reason)
        assertTrue(harness.tickers.isEmpty())
    }

    @Test
    fun `a bubble dragged over the target mid-run skips instead of spraying taps`() {
        val harness = Harness()
        harness.coordinator.start()
        harness.tickers.single().fire()
        harness.sink.deliverLatest()

        harness.overlap = true
        harness.tickers.single().fire()

        val running = harness.state as ClickerState.Running
        assertEquals(1L, running.tapCount)
        assertEquals(1L, running.skippedTicks)
        assertEquals(TapOutcome.SKIPPED_BY_BUBBLE, running.lastOutcome)
        assertEquals(1, harness.sink.taps.size)
    }

    @Test
    fun `a disconnected sink fails the session and stops the ticker`() {
        val harness = Harness()
        harness.coordinator.start()
        val ticker = harness.tickers.single()

        harness.sinkAvailable = false
        harness.coordinator.onSinkChanged()

        val state = harness.state
        assertTrue(state is ClickerState.Error)
        assertEquals(FailureReason.SERVICE_DISCONNECTED, (state as ClickerState.Error).reason)
        assertTrue(ticker.stopped)
    }

    @Test
    fun `applyConfig rebuilds the ticker with the new interval and keeps counters`() {
        val harness = Harness()
        harness.coordinator.start()
        harness.tickers.single().fire()
        harness.sink.deliverLatest()

        val newConfig = ClickerConfig.ofSeconds(cellIndex = 2, intervalSeconds = 3, stopAfterTaps = 0, hapticOnTap = true, flashOnTap = true)
        harness.config = newConfig
        harness.coordinator.applyConfig(newConfig)

        assertEquals(2, harness.tickers.size)
        assertEquals(3_000L, harness.tickers[1].intervalMs)
        assertTrue(harness.tickers[0].stopped)
        assertEquals(1L, (harness.state as ClickerState.Running).tapCount)
    }

    @Test
    fun `applyConfig is a no-op while idle`() {
        val harness = Harness()
        harness.config = ClickerConfig.ofSeconds(8, 10, 0, true, true)
        harness.coordinator.applyConfig(harness.config)

        assertTrue(harness.tickers.isEmpty())
        assertEquals(ClickerState.Idle, harness.state)
    }

    @Test
    fun `pause keeps counters and resume restarts the loop`() {
        val harness = Harness()
        harness.coordinator.start()
        harness.tickers.single().fire()
        harness.sink.deliverLatest()

        harness.coordinator.pause()
        val pausedState = harness.state as ClickerState.Running
        assertTrue(pausedState.paused)
        assertEquals(1L, pausedState.tapCount)
        assertTrue(harness.tickers[0].stopped)

        harness.coordinator.resume()
        assertEquals(2, harness.tickers.size)
        assertFalse((harness.state as ClickerState.Running).paused)
        assertEquals(1L, (harness.state as ClickerState.Running).tapCount)
    }

    @Test
    fun `reportFailure stops the loop`() {
        val harness = Harness()
        harness.coordinator.start()
        harness.coordinator.reportFailure(FailureReason.DISPLAY_UNAVAILABLE)

        assertTrue(harness.tickers.single().stopped)
        val state = harness.state
        assertTrue(state is ClickerState.Error)
        assertEquals(FailureReason.DISPLAY_UNAVAILABLE, (state as ClickerState.Error).reason)
    }

    @Test
    fun `second start is ignored while already running`() {
        val harness = Harness()
        harness.coordinator.start()
        harness.coordinator.start()

        assertEquals(1, harness.tickers.size)
    }

    @Test
    fun `acknowledgeError returns to idle`() {
        val harness = Harness().apply { sinkAvailable = false }
        harness.coordinator.start()
        harness.coordinator.acknowledgeError()

        assertEquals(ClickerState.Idle, harness.state)
    }

    @Test
    fun `tap budget ends the session after the exact number of delivered taps`() {
        val harness = Harness(ClickerConfig.ofSeconds(4, 1, 3, true, true))
        harness.coordinator.start()

        repeat(3) {
            harness.tickers.single().fire()
            harness.sink.deliverLatest()
        }

        val state = harness.state
        assertTrue(state is ClickerState.Finished)
        assertEquals(3L, (state as ClickerState.Finished).tapCount)
        assertEquals(3, harness.sink.taps.size)
        assertTrue(harness.tickers.single().stopped)

        // A tick that arrives after the budget was spent must not produce a fourth tap.
        harness.tickers.single().fire()
        assertEquals(3, harness.sink.taps.size)
    }

    @Test
    fun `a skipped tick does not spend the budget`() {
        val harness = Harness(ClickerConfig.ofSeconds(4, 1, 2, true, true))
        harness.coordinator.start()
        val ticker = harness.tickers.single()

        ticker.fire()
        harness.sink.deliverLatest()
        ticker.fire() // in flight, no result yet -> skipped
        ticker.fire() // still in flight -> skipped
        harness.sink.deliverLatest()

        val state = harness.state
        assertTrue(state is ClickerState.Finished)
        assertEquals(2L, (state as ClickerState.Finished).tapCount)
    }

    @Test
    fun `lowering the budget below the spent count stops the session immediately`() {
        val harness = Harness()
        harness.coordinator.start()
        repeat(3) {
            harness.tickers.single().fire()
            harness.sink.deliverLatest()
        }
        assertEquals(3L, (harness.state as ClickerState.Running).tapCount)

        val tightened = ClickerConfig.ofSeconds(4, 1, 2, true, true)
        harness.config = tightened
        harness.coordinator.applyConfig(tightened)

        val state = harness.state
        assertTrue(state is ClickerState.Finished)
        assertEquals(3L, (state as ClickerState.Finished).tapCount)
        assertTrue(harness.tickers.single().stopped)
    }

    @Test
    fun `start after finishing begins a fresh session`() {
        val harness = Harness(ClickerConfig.ofSeconds(4, 1, 1, true, true))
        harness.coordinator.start()
        harness.tickers.single().fire()
        harness.sink.deliverLatest()
        assertTrue(harness.state is ClickerState.Finished)

        harness.coordinator.start()
        assertEquals(2, harness.tickers.size)
        assertEquals(0L, (harness.state as ClickerState.Running).tapCount)
    }
}
