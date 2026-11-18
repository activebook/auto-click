package com.activebook.autoclick.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.app.ActivityOptions
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.widget.Toast

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.activebook.autoclick.app.AppContainer
import com.activebook.autoclick.app.AutoClickApplication
import com.activebook.autoclick.app.R
import com.activebook.autoclick.app.domain.model.ClickerState
import com.activebook.autoclick.app.domain.model.FailureReason
import com.activebook.autoclick.app.platform.Permissions
import com.activebook.autoclick.app.service.overlay.OverlayController
import com.activebook.autoclick.app.ui.MainActivity
import com.activebook.autoclick.app.ui.failureMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the floating bubble and the tap timer alive while the user is
 * in other apps. Owns the windows, the notification, and the screen-state policy; all tapping
 * logic still lives in the coordinator.
 *
 * Screen policy: tapping pauses when the display turns off and resumes on unlock. Tapping a
 * black, locked screen would be pointless and would drain battery, so AutoClick never tries.
 */
class OverlayService : Service() {

    private lateinit var scope: CoroutineScope
    private var overlay: OverlayController? = null
    private var screenReceiverRegistered = false

    /** Whether this run has a bubble window; false when the overlay permission is missing. */
    private var bubbleAvailable = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> container().coordinator.pause()
                Intent.ACTION_USER_PRESENT -> container().coordinator.resume()
                else -> Unit
            }
        }
    }

    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            overlay?.onConfigurationChanged()
        }

        @Suppress("DEPRECATION")
        override fun onLowMemory() = Unit
    }

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        createNotificationChannel()
        // Must happen before any other work: a foreground service has ~5s to post its
        // notification, otherwise the system kills the process.
        startForegroundCompat(buildNotification(container().coordinator.state.value))

        attachBubble()
        registerScreenReceiver()
        registerComponentCallbacks(configurationCallbacks)
        observeState()
    }

    /**
     * Puts the hand on screen if — and only if — the overlay permission is held.
     *
     * The bubble is now a pure function of that permission (ADR-016): there is no "show the hand"
     * preference any more, so this has to be callable at runtime, not just once in [onCreate].
     * That is what makes "grant the permission while a session is already running" work: the
     * tapping continues and the hand simply appears.
     *
     * @return whether a bubble is on screen afterwards.
     */
    private fun attachBubble(): Boolean {
        if (overlay != null) return true
        if (!Permissions.hasOverlayPermission(this)) return false

        val controller = OverlayController(
            context = this,
            coordinator = container().coordinator,
            configFlow = container().config,
            repository = container().repository,
            displayProvider = container().displayProvider,
            scope = scope,
            onStartTapping = {
                overlay?.updateBoundsNow()
                container().coordinator.start()
            },
            onStopTapping = { container().coordinator.stop() },
            onOpenApp = { openApp() },
        )

        try {
            controller.show()
            if (!controller.isShowing) {
                throw IllegalStateException("The bubble window was not created")
            }
        } catch (error: RuntimeException) {
            container().coordinator.reportFailure(FailureReason.NO_OVERLAY_PERMISSION, error.message)
            return false
        }

        overlay = controller
        bubbleAvailable = true
        container().coordinator.setGuard(controller)
        overlayVisible.value = true
        return true
    }

    /**
     * Brings the app's own window to the front.
     *
     * A service starting an activity is a background activity launch, which Android 10+ throttles —
     * but a holder of `SYSTEM_ALERT_WINDOW` is on the explicit exemption list, and the tap that got
     * us here was a real user interaction with our window, so this is allowed. The intent is
     * identical to the notification's tap target, so the app reuses its existing task instead of
     * piling up new ones.
     */
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(intent) }
            .onFailure { error ->
                // Nothing else to try: say so rather than letting the button look dead.
                runCatching {
                    Toast.makeText(this, "Could not open AutoClick: ${error.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
    }

    /**
     * Takes the hand away. The system strips our windows when the permission is revoked, and the
     * guard cannot protect a target it can no longer see, so a running session is stopped too.
     */
    private fun releaseBubble() {
        overlay?.hide()
        overlay = null
        bubbleAvailable = false
        container().coordinator.setGuard(null)
        container().coordinator.stop()
        overlayVisible.value = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> container().coordinator.stop()
            ACTION_HIDE -> {
                releaseBubble()
                stopSelf()
            }
            ACTION_START -> {
                // Attach first (the permission may have arrived while a headless session ran),
                // then refresh the guard bounds, then start: the first tick must already know
                // where the bubble is, otherwise a bubble sitting on the target skips it.
                attachBubble()
                overlay?.updateBoundsNow()
                container().coordinator.start()
            }
            ACTION_SHOW -> {
                // No bubble and no permission: do not sit here pretending to be useful.
                if (!attachBubble()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            null -> attachBubble()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        runCatching { unregisterComponentCallbacks(configurationCallbacks) }

        container().coordinator.stop()
        container().coordinator.setGuard(null)
        overlay?.hide()
        overlay = null
        bubbleAvailable = false
        overlayVisible.value = false
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- notification

    private fun observeState() {
        scope.launch {
            container().coordinator.state.collect { state -> publishNotification(state) }
        }
    }

    private fun publishNotification(state: ClickerState) {
        val notification = buildNotification(state)
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            // POST_NOTIFICATIONS was denied: the service keeps working, the ongoing
            // notification simply stays hidden.
        }
    }

    private fun buildNotification(state: ClickerState): Notification {
        val config = container().config.value
        val title = when (state) {
            ClickerState.Idle -> getString(R.string.notification_title_idle)
            is ClickerState.Running -> getString(R.string.notification_title_running)
            is ClickerState.Finished -> getString(R.string.notification_title_finished)
            is ClickerState.Error -> getString(R.string.notification_title_error)
        }
        val text = when (state) {
            ClickerState.Idle ->
                if (bubbleAvailable) {
                    "Floating hand ready · every ${config.intervalSeconds}s"
                } else {
                    "Ready · every ${config.intervalSeconds}s"
                }

            is ClickerState.Running ->
                if (state.paused) {
                    "Paused while the screen is off · ${tapLabel(state.tapCount)} so far"
                } else {
                    "${tapLabel(state.tapCount)} · every ${config.intervalSeconds}s"
                }

            is ClickerState.Finished ->
                "Finished its tap budget · ${tapLabel(state.tapCount)} delivered · tap Start to run again"

            is ClickerState.Error -> failureMessage(state.reason)
        }

        val running = state is ClickerState.Running
        val actionIntent = PendingIntent.getService(
            this,
            REQUEST_TOGGLE,
            Intent(this, OverlayService::class.java)
                .setAction(if (running) ACTION_STOP else ACTION_START),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_autoclick)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setOngoing(state !is ClickerState.Error)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                0,
                getString(if (running) R.string.notification_action_stop else R.string.notification_action_start),
                actionIntent,
            )
            .build()
    }

    /** "1 tap", "2 taps" — the status line shows small counts often enough to matter. */
    private fun tapLabel(count: Long): String = if (count == 1L) "1 tap" else "$count taps"

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---------------------------------------------------------------- plumbing

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverRegistered = true
    }

    private fun container(): AppContainer = (application as AutoClickApplication).container

    companion object {
        const val ACTION_START = "com.activebook.autoclick.app.action.START"
        const val ACTION_SHOW = "com.activebook.autoclick.app.action.SHOW"
        const val ACTION_STOP = "com.activebook.autoclick.app.action.STOP"
        const val ACTION_HIDE = "com.activebook.autoclick.app.action.HIDE"

        private const val CHANNEL_ID = "autoclick_status"
        private const val NOTIFICATION_ID = 4711
        private const val REQUEST_TOGGLE = 10
        private const val REQUEST_OPEN = 11

        private val overlayVisible = MutableStateFlow(false)

        /** Whether the floating bubble is currently on screen; observed by the settings UI. */
        val isOverlayVisible: StateFlow<Boolean> = overlayVisible.asStateFlow()

        /** Shows the bubble and immediately starts tapping. */
        fun requestStart(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Removes the bubble and stops a running session. */
        fun requestHide(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_HIDE)
            runCatching { context.startService(intent) }
        }

        /** Shows the bubble without starting a session. */
        fun requestShow(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_SHOW)
            ContextCompat.startForegroundService(context, intent)
        }

    }
}
