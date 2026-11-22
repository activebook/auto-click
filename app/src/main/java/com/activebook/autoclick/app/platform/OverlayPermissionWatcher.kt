package com.activebook.autoclick.app.platform

import android.app.AppOpsManager
import android.content.Context
import android.provider.Settings
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches for `SYSTEM_ALERT_WINDOW` to transition from denied to granted so the floating hand
 * appears immediately while the user is still inside system Settings.
 *
 * Combines an event-driven [AppOpsManager] mode listener (zero CPU while idle, catches manual
 * Settings navigation) with a bounded fallback poll after leaving AutoClick for overlay Settings,
 * covering OEM ROMs whose [AppOpsManager.OnOpChangedListener] lags or omits the toggle.
 */
class OverlayPermissionWatcher(
    private val scope: CoroutineScope,
    private val hasOverlayPermission: () -> Boolean,
    private val registerAppOpWatcher: (onChanged: () -> Unit) -> AutoCloseable?,
    private val onGranted: () -> Unit,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val pollTimeoutMs: Long = DEFAULT_POLL_TIMEOUT_MS,
) {

    private val lock = Any()
    private val delivered = AtomicBoolean(false)
    private var active = false
    private var appOpHandle: AutoCloseable? = null
    private var pollJob: Job? = null

    fun start(withFallbackPoll: Boolean) {
        if (hasOverlayPermission()) {
            cancel()
            return
        }

        synchronized(lock) {
            if (!active) {
                active = true
                delivered.set(false)
            }
            if (appOpHandle == null) {
                val handle = runCatching { registerAppOpWatcher { checkGranted() } }.getOrNull()
                if (active) {
                    appOpHandle = handle
                } else {
                    runCatching { handle?.close() }
                }
            }
            if (withFallbackPoll) {
                pollJob?.cancel()
                pollJob = scope.launch {
                    var elapsedMs = 0L
                    while (isActive && elapsedMs < pollTimeoutMs) {
                        delay(pollIntervalMs)
                        elapsedMs += pollIntervalMs
                        if (checkGranted()) break
                    }
                    synchronized(lock) {
                        if (pollJob === coroutineContext[Job]) {
                            pollJob = null
                        }
                    }
                }
            } else {
                pollJob?.cancel()
                pollJob = null
            }
        }
    }

    fun cancel() {
        val handleToClose: AutoCloseable?
        val jobToCancel: Job?
        synchronized(lock) {
            active = false
            delivered.set(false)
            handleToClose = appOpHandle
            appOpHandle = null
            jobToCancel = pollJob
            pollJob = null
        }
        jobToCancel?.cancel()
        runCatching { handleToClose?.close() }
    }

    private fun checkGranted(): Boolean {
        if (!hasOverlayPermission()) return false
        val handleToClose: AutoCloseable?
        val jobToCancel: Job?
        val shouldDeliver: Boolean
        synchronized(lock) {
            shouldDeliver = active && delivered.compareAndSet(false, true)
            if (!shouldDeliver) return false
            active = false
            handleToClose = appOpHandle
            appOpHandle = null
            jobToCancel = pollJob
            pollJob = null
        }
        jobToCancel?.cancel()
        runCatching { handleToClose?.close() }
        onGranted()
        return true
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 250L
        const val DEFAULT_POLL_TIMEOUT_MS = 120_000L

        fun forContext(
            context: Context,
            scope: CoroutineScope,
            onGranted: () -> Unit,
        ): OverlayPermissionWatcher {
            val appContext = context.applicationContext
            return OverlayPermissionWatcher(
                scope = scope,
                hasOverlayPermission = { Settings.canDrawOverlays(appContext) },
                registerAppOpWatcher = { onChanged ->
                    val appOps = appContext.getSystemService(AppOpsManager::class.java)
                        ?: return@OverlayPermissionWatcher null
                    val listener = AppOpsManager.OnOpChangedListener { _, packageName ->
                        if (packageName == null || packageName == appContext.packageName) {
                            onChanged()
                        }
                    }
                    val registered = runCatching {
                        appOps.startWatchingMode(
                            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                            appContext.packageName,
                            listener,
                        )
                    }.isSuccess
                    if (!registered) {
                        null
                    } else {
                        AutoCloseable { runCatching { appOps.stopWatchingMode(listener) } }
                    }
                },
                onGranted = onGranted,
            )
        }
    }
}
