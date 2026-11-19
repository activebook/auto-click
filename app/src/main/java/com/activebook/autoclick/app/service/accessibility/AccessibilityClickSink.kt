package com.activebook.autoclick.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.activebook.autoclick.app.core.ClickSink
import com.activebook.autoclick.app.domain.model.TapResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin adapter between [com.activebook.autoclick.app.core.ClickerCoordinator] and the
 * accessibility gesture API. It does exactly one thing: inject one tap, report one result.
 *
 * All work happens on the main thread, because [AccessibilityService.dispatchGesture] and its
 * callback are main-thread APIs.
 */
class AccessibilityClickSink(service: AccessibilityService) : ClickSink {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var serviceReference: AccessibilityService? = service

    override val isConnected: Boolean
        get() = serviceReference != null

    fun disconnect() {
        serviceReference = null
    }

    override fun tap(
        x: Float,
        y: Float,
        durationMs: Long,
        onResult: (TapResult) -> Unit,
    ): Boolean {
        val service = serviceReference ?: return false
        mainHandler.post { dispatch(service, x, y, durationMs, onResult) }
        return true
    }

    private fun dispatch(
        service: AccessibilityService,
        x: Float,
        y: Float,
        durationMs: Long,
        onResult: (TapResult) -> Unit,
    ) {
        val reported = AtomicBoolean(false)
        fun report(result: TapResult) {
            if (reported.compareAndSet(false, true)) onResult(result)
        }

        // A stationary path is a press-and-hold for `durationMs`: a tap, not a long press.
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMs.coerceIn(MIN_STROKE_MS, MAX_STROKE_MS),
        )

        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                report(TapResult.DELIVERED)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                report(TapResult.CANCELLED)
            }
        }

        val accepted = try {
            service.dispatchGesture(gesture, callback, mainHandler)
        } catch (error: RuntimeException) {
            false
        }

        // dispatchGesture returns false when another gesture is still in progress.
        if (!accepted) report(TapResult.CANCELLED)
    }

    private companion object {
        const val MIN_STROKE_MS = 20L
        const val MAX_STROKE_MS = 60_000L
    }
}
