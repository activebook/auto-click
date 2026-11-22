package com.activebook.autoclick.app.ui

/** Action the UI should take to keep the floating hand aligned with permissions and preference. */
data class HandSyncDecision(
    val persistHandVisibleTrue: Boolean = false,
    val showOverlay: Boolean = false,
    val detachOverlay: Boolean = false,
)

/**
 * Reconciles floating-hand visibility from permission transitions and the persisted preference.
 *
 * The floating hand requires both `Display over other apps` and `Accessibility: AutoClick taps`.
 * Whenever the pair transitions to granted, any prior hidden preference is reset so the hand
 * immediately confirms both permissions work; when either permission is lost, the overlay is
 * detached.
 */
fun reconcileHandVisibility(
    overlayGranted: Boolean,
    wasOverlayGranted: Boolean,
    accessibilityGranted: Boolean,
    wasAccessibilityGranted: Boolean,
    handVisible: Boolean,
    overlayCurrentlyShown: Boolean,
): HandSyncDecision {
    val requiredGranted = overlayGranted && accessibilityGranted
    val wasRequiredGranted = wasOverlayGranted && wasAccessibilityGranted
    if (!requiredGranted) {
        return HandSyncDecision(
            persistHandVisibleTrue = !handVisible,
            detachOverlay = overlayCurrentlyShown,
        )
    }

    return when {
        !wasRequiredGranted -> HandSyncDecision(
            persistHandVisibleTrue = !handVisible,
            showOverlay = true,
        )
        handVisible -> HandSyncDecision(showOverlay = true)
        else -> HandSyncDecision(detachOverlay = overlayCurrentlyShown)
    }
}
