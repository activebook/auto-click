package com.activebook.autoclick.app.platform

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.activebook.autoclick.app.service.accessibility.AutoClickAccessibilityService

/**
 * Single place that answers "may AutoClick do X yet?" and how to send the user to the right
 * system screen. Every check is cheap and side-effect free, so the UI can poll it while visible.
 */
object Permissions {

    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasAccessibilityService(context: Context): Boolean {
        val contentResolver = context.contentResolver
        val enabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!enabled) return false

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val expectedPackage = context.packageName
        val expectedClass = AutoClickAccessibilityService::class.java.name

        return enabledServices.split(':').any { entry ->
            val component = ComponentName.unflattenFromString(entry.trim()) ?: return@any false
            component.packageName == expectedPackage && component.className == expectedClass
        }
    }

    /**
     * vivo's floating-window list, which this ROM already opens scrolled to our row.
     *
     * Returns false when neither the targeted nor the plain screen exists, so the caller can say
     * so instead of leaving the user on a button that did nothing.
     */
    fun openOverlaySettings(context: Context): Boolean {
        val targeted = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startSafely(context, targeted)) return true
        return startSafely(
            context,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * The battery whitelist dialog: "always run in the background?" — one tap, and it is the only
     * thing that can put us on the exemption list.
     *
     * AutoClick is sideloaded rather than distributed through the Play Store (ADR-008).
     */
    @SuppressLint("BatteryLife")
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri(),
        )

    /** The exemption list — the only screen that can take an app back off it. */
    fun batteryOptimizationListIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Our own notification switches, the counterpart of the runtime permission dialog. */
    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun hasNotificationPermission(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Where the user actually ended up after [openAccessibilitySettings]. */
    enum class AccessibilityRoute {
        /** The Accessibility list was asked to scroll to our row and pulse it. */
        HIGHLIGHTED,

        /** Only the plain list could be opened; the user has to find "AutoClick taps" himself. */
        LIST,
    }

    /**
     * Opens Accessibility with the "AutoClick taps" row revealed and highlighted.
     *
     * This is the mechanism apps like Scroll X use, and it needs no permission at all: Settings'
     * accessibility list is an ordinary exported activity, and it accepts a *fragment arguments*
     * bundle naming which preference to reveal. Settings then scrolls the list to that preference
     * and plays its own highlight animation — the scroll and the pulse are Settings' UI reacting to
     * our extra, not something we do to somebody else's app.
     *
     * The key has to go inside the bundle. Passed as a flat String extra it never reaches the
     * fragment (`getArguments().getString(":settings:fragment_args_key")`), which is why a
     * correctly spelled intent can still open the list and sit at the top — the failure mode this
     * implementation used to have.
     *
     * What does *not* work, measured on this device, in case it is ever considered again:
     * `ACCESSIBILITY_DETAILS_SETTINGS` (the per-service page) is declared with the signature
     * permission `OPEN_ACCESSIBILITY_DETAILS_SETTINGS` (`signature|installer` here), so a
     * sideloaded app gets SecurityException; vivo's `TOGGLE_ACCESSIBILITY_SERVICE` needs
     * `com.vivo.settings.permission.START_SETTINGS_ACTIVITY`; `SubSettings` / fragment class
     * arguments are ignored by this build.
     */
    fun openAccessibilitySettings(context: Context): AccessibilityRoute {
        val components = listOf(
            // AccessibilityServiceInfo#getId() spells the component out in full ("pkg/com.pkg.Cls"),
            // and that is what Settings keys its preference rows with on AOSP-derived builds.
            ComponentName(context, AutoClickAccessibilityService::class.java).flattenToString(),
            // Some OEM lists prefer the short form ("pkg/.Cls").
            ComponentName(context, AutoClickAccessibilityService::class.java).flattenToShortString(),
        )

        for (component in components) {
            if (startSafely(context, highlightIntent(component))) {
                return AccessibilityRoute.HIGHLIGHTED
            }
        }

        startSafely(
            context,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return AccessibilityRoute.LIST
    }

    /**
     * The Accessibility list, asked to reveal one preference.
     *
     * `:settings:show_fragment_args` is the bundle Settings' own search results hand to a settings
     * page; `:settings:fragment_args_key` inside it is the preference key to scroll to. The flat
     * copy of the key is included because some builds read it straight off the intent.
     */
    private fun highlightIntent(component: String): Intent {
        val args = Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, component) }
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Settings is singleTop-ish in practice: without CLEAR_TOP a re-launch can bring the
            // already-open page forward and drop the extras, which looks like "nothing happened".
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, args)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, component)
    }

    private fun startSafely(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (error: SecurityException) {
        false
    } catch (error: ActivityNotFoundException) {
        false
    }

    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
}
