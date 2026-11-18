package com.activebook.autoclick.app.ui

import com.activebook.autoclick.app.domain.model.FailureReason

/** One place for every failure explanation, so the bubble, the notification and the app agree. */
fun failureMessage(reason: FailureReason): String = when (reason) {
    FailureReason.NO_ACCESSIBILITY ->
        "Accessibility is off. Enable Settings › Accessibility › AutoClick taps."

    FailureReason.NO_OVERLAY_PERMISSION ->
        "Floating window permission is off. Allow \"Display over other apps\"."

    FailureReason.DISPLAY_UNAVAILABLE ->
        "Screen size could not be read. Turn the screen on and try again."

    FailureReason.SERVICE_DISCONNECTED ->
        "The accessibility service disconnected. Re-enable it to keep tapping."

    FailureReason.TARGET_COVERED_BY_BUBBLE ->
        "The floating hand covers your target. Drag it away, then start again."
}
