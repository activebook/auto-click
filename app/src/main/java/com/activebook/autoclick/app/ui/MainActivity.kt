package com.activebook.autoclick.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.activebook.autoclick.app.AppContainer
import com.activebook.autoclick.app.AutoClickApplication
import com.activebook.autoclick.app.domain.geometry.RelativePoint
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.domain.model.TargetMode
import com.activebook.autoclick.app.domain.model.ClickerState
import com.activebook.autoclick.app.domain.model.FailureReason
import com.activebook.autoclick.app.platform.Permissions
import com.activebook.autoclick.app.service.HintTrace
import com.activebook.autoclick.app.service.OverlayService
import com.activebook.autoclick.app.ui.grid.GridPickerActivity
import com.activebook.autoclick.app.ui.theme.AutoClickTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val handoffHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var handoffActive = false

    /** Set when the accessibility deep link is about to open; consumed in [onStop]. */
    private var pendingHint: String? = null
    private var pendingHintDeadline = 0L

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val container: AppContainer
        get() = (application as AutoClickApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val config by container.config.collectAsStateWithLifecycle()
            val clickerState by container.coordinator.state.collectAsStateWithLifecycle()
            val overlayVisible by OverlayService.isOverlayVisible.collectAsStateWithLifecycle()

            val permissions by produceState(
                initialValue = PermissionSnapshot.current(this@MainActivity),
            ) {
                while (true) {
                    value = PermissionSnapshot.current(this@MainActivity)
                    delay(1_000)
                }
            }

            // Fixing a permission should dismiss the error it caused, so the app never shows a
            // stale "accessibility is off" banner after the user has just switched it on.
            val error = clickerState as? ClickerState.Error
            LaunchedEffect(error?.reason, permissions.overlay, permissions.accessibility) {
                val fixed = when (error?.reason) {
                    FailureReason.NO_ACCESSIBILITY -> permissions.accessibility
                    FailureReason.NO_OVERLAY_PERMISSION -> permissions.overlay
                    // A disconnect is a live-connection problem, not a settings problem: the
                    // setting can read "on" while the system has already unbound the service,
                    // so this must stay visible instead of silently clearing.
                    FailureReason.SERVICE_DISCONNECTED -> false
                    else -> false
                }
                if (fixed) container.coordinator.acknowledgeError()
            }

            // The hand follows the permission and nothing else — its one control is the
            // "Display over other apps" switch in Before you start (ADR-016). Both directions are
            // driven from here, and both are idempotent: requestShow attaches the window only if it
            // is missing, requestHide tears it down and stops the session it can no longer guard.
            LaunchedEffect(permissions.overlay) {
                if (permissions.overlay) {
                    OverlayService.requestShow(this@MainActivity)
                } else if (OverlayService.isOverlayVisible.value) {
                    OverlayService.requestHide(this@MainActivity)
                }
            }

            AutoClickTheme {
                MainScreen(
                    config = config,
                    state = clickerState,
                    permissions = permissions,
                    onStart = {
                        // Accessibility alone is enough to tap. Without the overlay permission
                        // the service runs headless: same loop, no bubble.
                        if (permissions.accessibility) {
                            OverlayService.requestStart(this@MainActivity)
                        }
                    },
                    onStop = { container.coordinator.stop() },
                    // Tapping any cell is an explicit "go back to coarse": the mode changes with
                    // the gesture, so the pin disappears the moment a cell is chosen.
                    onCellSelected = { index ->
                        applyConfig(cell = index, targetMode = TargetMode.CELL)
                    },
                    onIntervalSeconds = { seconds -> applyConfig(seconds = seconds) },
                    onStopAfterTaps = { taps -> applyConfig(stopAfterTaps = taps) },
                    onHaptics = { enabled -> applyConfig(haptic = enabled) },
                    onFlash = { enabled -> applyConfig(flash = enabled) },
                    onOpenAccessibility = ::openAccessibility,
                    onEnableOverlay = ::openOverlay,
                    // A switch cannot grant anything itself, so it opens the screen that owns the
                    // grant: the same page in both directions, where the toggle lives.
                    onToggleOverlay = { openOverlay() },
                    onToggleAccessibility = { openAccessibility() },
                    onToggleNotifications = { enabled ->
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            }
                        } else {
                            launchSafely(
                                Permissions.notificationSettingsIntent(this),
                                checked = false,
                            )
                        }
                    },
                    onToggleBattery = { enabled ->
                        launchSafely(
                            if (enabled) {
                                Permissions.batteryOptimizationIntent(this)
                            } else {
                                Permissions.batteryOptimizationListIntent()
                            },
                            checked = enabled,
                        )
                    },
                    onPickTarget = { mode ->
                        startActivity(GridPickerActivity.intent(this@MainActivity, mode))
                    },
                )
            }
        }
    }

    private fun applyConfig(
        cell: Int? = null,
        seconds: Int? = null,
        stopAfterTaps: Int? = null,
        haptic: Boolean? = null,
        flash: Boolean? = null,
        targetMode: TargetMode? = null,
        point: RelativePoint? = null,
    ) {
        val current = container.config.value
        val next = ClickerConfig.ofSeconds(
            cellIndex = cell ?: current.cellIndex,
            intervalSeconds = seconds ?: current.intervalSeconds,
            stopAfterTaps = stopAfterTaps ?: current.stopAfterTaps,
            hapticOnTap = haptic ?: current.hapticOnTap,
            flashOnTap = flash ?: current.flashOnTap,
            // Every write carries the mode and point forward, so changing the interval can never
            // silently drop an exact target back to a cell.
            targetMode = targetMode ?: current.targetMode,
            point = point ?: current.point,
        )
        lifecycleScope.launch { container.repository.updateConfig(next) }
    }

    /**
     * Opens Accessibility with our row revealed.
     *
     * Settings is asked to scroll to and pulse the row. On a ROM that ignores the request the user
     * still lands on the right page, so it says which row to look for instead of leaving him at the
     * top of a long list.
     */
    /**
     * Starts an intent, and if this ROM has no such screen says so instead of dropping the tap.
     */
    private fun launchSafely(intent: Intent, checked: Boolean) {
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(
                this,
                if (checked) "This ROM has no screen for that permission" else "No screen to take that back",
                Toast.LENGTH_LONG,
            ).show()
        } catch (error: SecurityException) {
            Toast.makeText(this, "This ROM does not allow that screen", Toast.LENGTH_LONG).show()
        }
    }

    /** Opens the floating-window screen, where the overlay permission is granted. */
    private fun openOverlay() {
        if (!Permissions.openOverlaySettings(this)) {
            Toast.makeText(
                this,
                "This ROM has no floating window screen — look for \"Display over other apps\" " +
                    "in App info",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * Opens Settings. When the deep link works the list scrolls *itself* to our row — but Settings
     * waits ~1.5s first, so for a moment the page just sits at the top and looks like the tap did
     * nothing.
     *
     * The notice is therefore not shown here: it goes up in [onStop], i.e. the moment Settings has
     * actually taken the screen. Requesting it at this point meant most of its life was spent
     * behind the launch animation, which is exactly the "I could not even see it" report.
     */
    private fun openAccessibility() {
        val route = Permissions.openAccessibilitySettings(this)
        if (route == Permissions.AccessibilityRoute.HIGHLIGHTED) {
            pendingHint = "Finding \"AutoClick taps\" — please wait"
            pendingHintDeadline = SystemClock.elapsedRealtime() + 4_000L
            return
        }

        // Fallback route: there is no auto-scroll to announce, so one short instruction is enough.
        showTopToast("Scroll to \"Downloaded services\" — first row")
    }

    /**
     * The accessibility screen force-hides every non-system overlay window
     * (`mForceHideNonSystemOverlayWindow=true`, measured), so an `APPLICATION_OVERLAY` pill is
     * invisible there — the hand is hidden too. A text Toast is drawn by the system, not by us, so
     * it survives; and this app holds `SYSTEM_ALERT_WINDOW`, which exempts it from the
     * background-toast restriction.
     */
    private fun showTopToast(message: String) {
        val offset = resources.displayMetrics.density * 64f
        Toast.makeText(this, message, Toast.LENGTH_LONG).apply {
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, offset.toInt())
        }.show()
    }

    override fun onResume() {
        super.onResume()
        // The user is back in the app: stop the second hand-off line from popping over our screen.
        handoffActive = false
    }

    override fun onStop() {
        super.onStop()
        val hint = pendingHint ?: return
        pendingHint = null
        // If Settings took ages to come up, or the user went somewhere else entirely, a notice
        // about a scroll they can no longer see would be noise.
        if (SystemClock.elapsedRealtime() > pendingHintDeadline) return

        HintTrace.log(this, "onStop -> toast")
        handoffActive = true
        showTopToast(hint)
        // One Toast lives ~3.5s and Settings' own highlight lands right at the end of that, so a
        // second, shorter line covers the scroll itself instead of repeating the first.
        handoffHandler.postDelayed({
            if (handoffActive) showTopToast("Click the highlighted row")
        }, HANDOFF_SECOND_LINE_DELAY_MS)
    }

}

/** Gap between the "please wait" line and the "watch for the row" line (~one Toast later). */
private const val HANDOFF_SECOND_LINE_DELAY_MS = 2400L
