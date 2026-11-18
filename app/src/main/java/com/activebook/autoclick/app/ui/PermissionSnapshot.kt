package com.activebook.autoclick.app.ui

import android.content.Context
import com.activebook.autoclick.app.platform.Permissions

/** A cheap, pollable snapshot of everything that can block a run. */
data class PermissionSnapshot(
    val overlay: Boolean,
    val accessibility: Boolean,
    val notifications: Boolean,
    val batteryUnrestricted: Boolean,
) {
    companion object {
        fun current(context: Context): PermissionSnapshot = PermissionSnapshot(
            overlay = Permissions.hasOverlayPermission(context),
            accessibility = Permissions.hasAccessibilityService(context),
            notifications = Permissions.hasNotificationPermission(context),
            batteryUnrestricted = Permissions.isIgnoringBatteryOptimizations(context),
        )
    }
}
