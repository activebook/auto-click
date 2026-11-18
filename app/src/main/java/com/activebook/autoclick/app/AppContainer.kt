package com.activebook.autoclick.app

import android.content.Context
import android.os.SystemClock
import com.activebook.autoclick.app.core.ClickSink
import com.activebook.autoclick.app.core.ClickerCoordinator
import com.activebook.autoclick.app.core.DisplayProvider
import com.activebook.autoclick.app.core.Ticker
import com.activebook.autoclick.app.data.ClickerConfigRepository
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.platform.AndroidDisplayProvider
import com.activebook.autoclick.app.platform.HandlerTicker
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

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sinkReference = AtomicReference<ClickSink?>(null)

    val repository = ClickerConfigRepository(appContext)
    val displayProvider: DisplayProvider = AndroidDisplayProvider(appContext)

    val config: StateFlow<ClickerConfig> = repository.config
        .stateIn(scope, SharingStarted.Eagerly, ClickerConfig.Default)

    val coordinator: ClickerCoordinator = ClickerCoordinator(
        configProvider = { config.value },
        displayProvider = displayProvider,
        sinkProvider = { sinkReference.get() },
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

    /** Called by the accessibility service when it connects or disconnects. */
    fun setClickSink(sink: ClickSink?) {
        sinkReference.set(sink)
        coordinator.onSinkChanged()
    }
}
