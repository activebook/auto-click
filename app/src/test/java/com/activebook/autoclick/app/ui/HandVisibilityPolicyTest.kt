package com.activebook.autoclick.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HandVisibilityPolicyTest {

    @Test
    fun `missing overlay permission resets stale hidden preference without showing overlay`() {
        val decision = reconcileHandVisibility(
            overlayGranted = false,
            wasOverlayGranted = false,
            accessibilityGranted = true,
            wasAccessibilityGranted = true,
            handVisible = false,
            overlayCurrentlyShown = false,
        )

        assertEquals(
            HandSyncDecision(persistHandVisibleTrue = true, showOverlay = false, detachOverlay = false),
            decision,
        )
    }

    @Test
    fun `missing accessibility permission detaches visible overlay and resets stale hidden preference`() {
        val decision = reconcileHandVisibility(
            overlayGranted = true,
            wasOverlayGranted = true,
            accessibilityGranted = false,
            wasAccessibilityGranted = true,
            handVisible = false,
            overlayCurrentlyShown = true,
        )

        assertEquals(
            HandSyncDecision(persistHandVisibleTrue = true, showOverlay = false, detachOverlay = true),
            decision,
        )
    }

    @Test
    fun `revoking overlay permission detaches visible overlay without changing true preference`() {
        val decision = reconcileHandVisibility(
            overlayGranted = false,
            wasOverlayGranted = true,
            accessibilityGranted = true,
            wasAccessibilityGranted = true,
            handVisible = true,
            overlayCurrentlyShown = true,
        )

        assertEquals(
            HandSyncDecision(persistHandVisibleTrue = false, showOverlay = false, detachOverlay = true),
            decision,
        )
    }

    @Test
    fun `granting overlay permission while accessibility is granted forces hand visible even if hidden`() {
        val decision = reconcileHandVisibility(
            overlayGranted = true,
            wasOverlayGranted = false,
            accessibilityGranted = true,
            wasAccessibilityGranted = true,
            handVisible = false,
            overlayCurrentlyShown = false,
        )

        assertEquals(
            HandSyncDecision(persistHandVisibleTrue = true, showOverlay = true, detachOverlay = false),
            decision,
        )
    }

    @Test
    fun `granting overlay permission while accessibility is still off does not show overlay yet`() {
        val decision = reconcileHandVisibility(
            overlayGranted = true,
            wasOverlayGranted = false,
            accessibilityGranted = false,
            wasAccessibilityGranted = false,
            handVisible = true,
            overlayCurrentlyShown = false,
        )

        assertEquals(
            HandSyncDecision(persistHandVisibleTrue = false, showOverlay = false, detachOverlay = false),
            decision,
        )
    }

    @Test
    fun `granting accessibility while overlay is held forces hand visible even when previously hidden`() {
        val decision = reconcileHandVisibility(
            overlayGranted = true,
            wasOverlayGranted = true,
            accessibilityGranted = true,
            wasAccessibilityGranted = false,
            handVisible = false,
            overlayCurrentlyShown = false,
        )

        assertEquals(
            HandSyncDecision(persistHandVisibleTrue = true, showOverlay = true, detachOverlay = false),
            decision,
        )
    }

    @Test
    fun `steady hidden preference stays hidden when both permissions were already granted`() {
        val decision = reconcileHandVisibility(
            overlayGranted = true,
            wasOverlayGranted = true,
            accessibilityGranted = true,
            wasAccessibilityGranted = true,
            handVisible = false,
            overlayCurrentlyShown = false,
        )

        assertEquals(
            HandSyncDecision(persistHandVisibleTrue = false, showOverlay = false, detachOverlay = false),
            decision,
        )
    }

    @Test
    fun `toggling Show floating hand off while overlay is shown detaches overlay`() {
        val decision = reconcileHandVisibility(
            overlayGranted = true,
            wasOverlayGranted = true,
            accessibilityGranted = true,
            wasAccessibilityGranted = true,
            handVisible = false,
            overlayCurrentlyShown = true,
        )

        assertEquals(
            HandSyncDecision(persistHandVisibleTrue = false, showOverlay = false, detachOverlay = true),
            decision,
        )
    }

    @Test
    fun `steady visible preference with both permissions granted shows overlay`() {
        val decision = reconcileHandVisibility(
            overlayGranted = true,
            wasOverlayGranted = true,
            accessibilityGranted = true,
            wasAccessibilityGranted = true,
            handVisible = true,
            overlayCurrentlyShown = true,
        )

        assertEquals(
            HandSyncDecision(persistHandVisibleTrue = false, showOverlay = true, detachOverlay = false),
            decision,
        )
    }
}
