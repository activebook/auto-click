package com.activebook.autoclick.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import com.activebook.autoclick.app.core.ClickSink
import com.activebook.autoclick.app.core.ClickerCoordinator
import com.activebook.autoclick.app.core.DisplayProvider
import com.activebook.autoclick.app.data.ClickerConfigRepository
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.platform.AndroidDisplayProvider
import com.activebook.autoclick.app.platform.HandlerTicker
import com.activebook.autoclick.app.platform.OverlayPermissionWatcher
import com.activebook.autoclick.app.platform.Permissions
import com.activebook.autoclick.app.service.OverlayService
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manual dependency container (five collaborators do not justify annotation processing).
 *
 * [config] is a hot [StateFlow] so the coordinator can read the latest settings synchronously
 * from any thread, and so live edits reach a running session immediately.
 */
class AppContainer(context: Context) {

    private data class AccessibilityBinding(
        val service: AccessibilityService,
        val sink: ClickSink,
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val accessibilityBinding = AtomicReference<AccessibilityBinding?>(null)

    private val overlayPermissionWatcher = OverlayPermissionWatcher.forContext(
        context = appContext,
        scope = scope,
    ) {
        if (Permissions.hasAccessibilityService(appContext) || hasAccessibilitySink) {
            setHandVisible(true)
            runCatching { OverlayService.requestShow(appContext) }
        }
    }

    val hasAccessibilitySink: Boolean
        get() = accessibilityBinding.get()?.sink?.isConnected == true

    /**
     * Live connected [AccessibilityService] instance used to host `TYPE_ACCESSIBILITY_OVERLAY`
     * windows so overlays remain visible over Settings screens that hide non-system overlays.
     */
    val accessibilityHost: AccessibilityService?
        get() = accessibilityBinding.get()?.service

    val repository = ClickerConfigRepository(appContext)
    val displayProvider: DisplayProvider = AndroidDisplayProvider(appContext)

    val config: StateFlow<ClickerConfig> = repository.config
        .stateIn(scope, SharingStarted.Eagerly, ClickerConfig.Default)

    /**
     * User preference: may the floating hand be on screen (on top of the overlay permission).
     * Starts as `null` until DataStore emits so cold-start UI never overwrites `false` with an
     * optimistic `true`.
     */
    val handVisible: StateFlow<Boolean?> = repository.handVisible
        .stateIn(scope, SharingStarted.Eagerly, null)

    val coordinator: ClickerCoordinator = ClickerCoordinator(
        configProvider = { config.value },
        displayProvider = displayProvider,
        sinkProvider = { accessibilityBinding.get()?.sink },
        tickerFactory = { intervalMs, onTick ->
            HandlerTicker(
                intervalMs = intervalMs,
                clock = SystemClock::uptimeMillis,
                onTick = onTick,
            )
        },
        clock = SystemClock::uptimeMillis,
    )

    init {
        // Live settings application: only rebuilds the ticker when something actually changed.
        scope.launch {
            config.collect { coordinator.applyConfig(it) }
        }
    }

    /** Persists hand visibility on the process scope so service teardown cannot cancel it. */
    fun setHandVisible(visible: Boolean) {
        scope.launch { repository.setHandVisible(visible) }
    }

    /** Persists config changes on the process scope so service teardown cannot cancel them. */
    fun updateConfig(config: ClickerConfig) {
        scope.launch { repository.updateConfig(config) }
    }

    /** Persists bubble coordinates on the process scope so service teardown cannot cancel them. */
    fun saveBubblePosition(x: Int, y: Int) {
        scope.launch { repository.saveBubblePosition(x, y) }
    }

    /** Called by the accessibility service when it connects. */
    fun attachAccessibility(service: AccessibilityService, sink: ClickSink) {
        accessibilityBinding.set(AccessibilityBinding(service, sink))
        coordinator.onSinkChanged()
    }

    /** Watches for overlay permission grant so the floating hand appears immediately. */
    fun watchForOverlayGrant(withFallbackPoll: Boolean) =
        overlayPermissionWatcher.start(withFallbackPoll)

    /** Stops watching for overlay permission grant once granted or no longer needed. */
    fun stopWatchingForOverlayGrant() = overlayPermissionWatcher.cancel()

    /**
     * Called when a service instance goes away. Compare-and-set on purpose: a service can be
     * rebound before the previous instance finishes unbinding, and the late `onDestroy` of the old
     * instance must not withdraw the sink the new one just published.
     */
    fun detachAccessibility(service: AccessibilityService, sink: ClickSink): Boolean {
        val current = accessibilityBinding.get()
        val cleared = current != null &&
            current.service === service &&
            current.sink === sink &&
            accessibilityBinding.compareAndSet(current, null)
        if (cleared) coordinator.onSinkChanged()
        return cleared
    }
}
