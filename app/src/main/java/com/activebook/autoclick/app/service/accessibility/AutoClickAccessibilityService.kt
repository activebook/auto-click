package com.activebook.autoclick.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.activebook.autoclick.app.AutoClickApplication
import com.activebook.autoclick.app.platform.Permissions
import com.activebook.autoclick.app.service.OverlayService

/**
 * The only component with the privilege to inject touches.
 *
 * Deliberately minimal: no window content retrieval, no event inspection, no business logic.
 * It publishes a [AccessibilityClickSink] while connected and withdraws it on unbind/destroy
 * so the coordinator can stop instead of firing taps into the void.
 */
class AutoClickAccessibilityService : AccessibilityService() {

    private var sink: AccessibilityClickSink? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as? AutoClickApplication)?.container ?: return
        val newSink = AccessibilityClickSink(this)
        sink = newSink
        container.attachAccessibility(this, newSink)
        if (Permissions.hasOverlayPermission(this)) {
            container.setHandVisible(true)
            runCatching { OverlayService.requestShow(this) }
        } else {
            container.watchForOverlayGrant(withFallbackPoll = false)
        }
    }

    /** Intentionally ignored: AutoClick never reads what is on screen. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    /**
     * Called when the system wants to interrupt feedback (a key press, a gesture conflict). That
     * is transient and says nothing about the service being disconnected, so the sink stays put;
     * only unbind/destroy mean "no more taps".
     */
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        detach()
        super.onDestroy()
    }

    private fun detach() {
        val current = sink ?: return
        sink = null
        current.disconnect()
        val container = (application as? AutoClickApplication)?.container ?: return
        val cleared = container.detachAccessibility(this, current)
        OverlayService.detachHost(this, stopService = cleared)
    }
}
