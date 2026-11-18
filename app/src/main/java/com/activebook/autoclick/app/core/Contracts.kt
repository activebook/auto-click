package com.activebook.autoclick.app.core

import com.activebook.autoclick.app.domain.geometry.ScreenPoint
import com.activebook.autoclick.app.domain.geometry.ScreenRect
import com.activebook.autoclick.app.domain.model.TapResult

/** Repeating timer. One instance per run session; [stop] must be idempotent. */
interface Ticker {
    fun start()
    fun stop()
}

/** The single capability AutoClick needs from the accessibility service. */
interface ClickSink {
    val isConnected: Boolean

    /**
     * Requests one tap. Implementations must never block the caller: the result is
     * delivered through [onResult] exactly once, on the main thread.
     *
     * @return false when the request could not even be queued.
     */
    fun tap(x: Float, y: Float, durationMs: Long, onResult: (TapResult) -> Unit): Boolean
}

/** Supplies the current usable screen area (display minus system bars), in screen pixels. */
interface DisplayProvider {
    fun usableArea(): ScreenRect
}

/** Lets the engine know whether its own floating bubble sits on top of the tap target. */
interface OverlayGuard {
    fun overlaps(point: ScreenPoint): Boolean
}
