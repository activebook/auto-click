package com.activebook.autoclick.app.domain.model

/** Result of one tap attempt, reported by the accessibility layer. */
enum class TapResult {
    /** The gesture was dispatched and the system reported it as completed. */
    DELIVERED,

    /** The gesture was rejected or cancelled (for example another gesture was in progress). */
    CANCELLED,
}

/** Coarse outcome surfaced in the UI; distinguishes "did not tap" from "tapped". */
enum class TapOutcome {
    NONE,
    DELIVERED,
    CANCELLED,
    SKIPPED_IN_FLIGHT,
    SKIPPED_BY_BUBBLE,
}

/** Why the engine is not running. Always paired with a user-facing hint in the UI. */
enum class FailureReason {
    NO_ACCESSIBILITY,
    NO_OVERLAY_PERMISSION,
    DISPLAY_UNAVAILABLE,
    SERVICE_DISCONNECTED,
    TARGET_COVERED_BY_BUBBLE,
}

/** Single source of truth for the bubble, the notification and the settings screen. */
sealed interface ClickerState {

    /** Not tapping. Either never started, or stopped by the user. */
    data object Idle : ClickerState

    data class Running(
        val startedAtUptime: Long,
        val tapCount: Long,
        val skippedTicks: Long,
        val lastOutcome: TapOutcome,
        val lastTapAtUptime: Long?,
        val paused: Boolean,
    ) : ClickerState

    /**
     * The session ended by itself because it reached the tap budget. Distinct from [Idle] so the
     * bubble can say "DONE" and the counter stays visible instead of silently reading "OFF".
     */
    data class Finished(
        val tapCount: Long,
        val skippedTicks: Long,
    ) : ClickerState

    data class Error(
        val reason: FailureReason,
        val detail: String? = null,
    ) : ClickerState
}
