package com.activebook.autoclick.app.service.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.activebook.autoclick.app.core.ClickerCoordinator
import com.activebook.autoclick.app.core.DisplayProvider
import com.activebook.autoclick.app.core.OverlayGuard
import com.activebook.autoclick.app.data.ClickerConfigRepository
import com.activebook.autoclick.app.domain.geometry.ScreenPoint
import com.activebook.autoclick.app.domain.geometry.ScreenRect
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.domain.model.ClickerState
import com.activebook.autoclick.app.domain.model.TargetMode
import com.activebook.autoclick.app.ui.overlay.PanelMode
import com.activebook.autoclick.app.ui.theme.AutoClickTheme
import com.activebook.autoclick.app.ui.failureMessage
import com.activebook.autoclick.app.ui.overlay.BubbleContent
import com.activebook.autoclick.app.ui.overlay.PanelContent
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the three overlay windows (bubble, panel, crosshair) and implements [OverlayGuard], the
 * engine's view of "does my own bubble sit on top of the target?".
 *
 * Window discipline
 * - bubble: 56dp, `FLAG_NOT_FOCUSABLE` (never steals IME or back), draggable, snaps to an edge.
 * - panel: separate window, created only while expanded, `WRAP_CONTENT` height.
 * - crosshair: `FLAG_NOT_TOUCHABLE`, invisible until a tap lands.
 *
 * Every WindowManager call happens on the main thread. The guard reads cached screen bounds
 * (updated on a frame callback) so the engine can ask from its ticker thread safely.
 */
class OverlayController(
    private val context: Context,
    private val coordinator: ClickerCoordinator,
    private val configFlow: StateFlow<ClickerConfig>,
    private val repository: ClickerConfigRepository,
    private val displayProvider: DisplayProvider,
    private val scope: CoroutineScope,
    private val onStartTapping: () -> Unit,
    private val onStopTapping: () -> Unit,
    private val onOpenApp: () -> Unit,
) : OverlayGuard {

    private val windowManager: WindowManager =
        requireNotNull(context.getSystemService(WindowManager::class.java)) {
            "WindowManager unavailable"
        }

    private val density = context.resources.displayMetrics.density
    private val bubbleSizePx = (56 * density).toInt()
    private val panelWidthPx = (212 * density).toInt()
    private val panelEstimateHeightPx = (108 * density).toInt()
    private val crosshairSizePx = (30 * density).toInt()
    private val edgeMarginPx = (12 * density).toInt()
    private val gapPx = (8 * density).toInt()

    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleOwner: OverlayLifecycleOwner? = null

    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelOwner: OverlayLifecycleOwner? = null
    private var panelExpanded = false

    private var crosshairView: CrosshairView? = null
    private var crosshairParams: WindowManager.LayoutParams? = null

    @Volatile
    private var bubbleBounds: ScreenRect? = null

    @Volatile
    private var panelBounds: ScreenRect? = null

    /** Window origin and pointer position at ACTION_DOWN, both in screen px. */
    private var dragAnchorWindowX = 0
    private var dragAnchorWindowY = 0
    private var dragDownScreenX = 0f
    private var dragDownScreenY = 0f

    private val panelModeState = mutableStateOf<PanelMode>(PanelMode.CONTROLS)
    private val configState = mutableStateOf<ClickerConfig>(ClickerConfig.Default)

    /** Throttles panel relayout during a drag; see [dragBubbleTo]. */
    private var lastPanelRefreshAt = 0L

    /**
     * Re-anchors the panel every time its measured height changes.
     *
     * The panel window is `WRAP_CONTENT` in height, so switching between the controls and the
     * settings page (a 130dp vs a 298dp layout) grows or shrinks the window *downward from a fixed
     * `params.y`* — the window then no longer sits beside the hand, it sits beside where the hand
     * was. A one-shot `post { refinePanelPosition() }` was not enough: composition and measure
     * happen a frame later than the post, so it read the previous height and "fixed" nothing.
     * Listening for the real layout change is the only reliable signal, and it also covers every
     * other height change (the status line wrapping to two lines, a font-scale change).
     */
    private val panelLayoutListener = View.OnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
        if (!panelExpanded || panelView !== view) return@OnLayoutChangeListener
        if (bottom - top == oldBottom - oldTop) return@OnLayoutChangeListener
        refinePanelPosition()
        updateBoundsNow()
    }

    private var observing = false
    private var boundsUpdateScheduled = false

    private val runningState = mutableStateOf(false)
    private val pausedState = mutableStateOf(false)
    private val finishedState = mutableStateOf(false)
    private val errorLineState = mutableStateOf<String?>(null)

    /** Increments on every delivered tap; the bubble's countdown ring restarts on each change. */
    private val tapCountState = mutableStateOf(0L)

    val isShowing: Boolean get() = bubbleView != null

    // ---------------------------------------------------------------- lifecycle

    fun show() {
        if (bubbleView != null) return
        val area = displayProvider.usableArea()
        if (area.isEmpty) return

        val defaultX = (area.right - bubbleSizePx - edgeMarginPx).toInt()
        val defaultY = (area.top + area.height * 0.35f).toInt()
        createBubble(defaultX, defaultY)
        observe()
        restoreSavedPosition()
    }

    fun hide() {
        collapsePanel()
        removeCrosshair()
        bubbleView?.let { view -> runCatching { windowManager.removeView(view) } }
        bubbleView = null
        bubbleParams = null
        bubbleBounds = null
        panelBounds = null
        bubbleOwner?.onDestroy()
        bubbleOwner = null
    }

    fun onConfigurationChanged() {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val area = displayProvider.usableArea()
        if (area.isEmpty) return

        params.x = params.x.coerceIn(area.left.toInt(), max(area.left.toInt(), (area.right - bubbleSizePx).toInt()))
        params.y = params.y.coerceIn(area.top.toInt(), max(area.top.toInt(), (area.bottom - bubbleSizePx).toInt()))
        runCatching { windowManager.updateViewLayout(view, params) }
        if (panelExpanded) refreshPanelPlacement()
        updateBoundsNow()
    }

    // ---------------------------------------------------------------- guard

    override fun overlaps(point: ScreenPoint): Boolean {
        bubbleBounds?.let { if (it.contains(point)) return true }
        panelBounds?.let { if (it.contains(point)) return true }
        return false
    }

    fun updateBoundsNow() {
        bubbleBounds = bubbleView?.let { screenBoundsOf(it) }
        panelBounds = panelView?.let { screenBoundsOf(it) }
    }

    // ---------------------------------------------------------------- bubble

    private fun createBubble(x: Int, y: Int) {
        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        val view = composeView(owner) {
            AutoClickTheme {
                BubbleContent(
                    running = runningState.value,
                    paused = pausedState.value,
                    finished = finishedState.value,
                    intervalMs = configState.value.intervalMs,
                    tapCount = tapCountState.value,
                    onDragStart = { x, y ->
                        dragDownScreenX = x
                        dragDownScreenY = y
                        dragAnchorWindowX = bubbleParams?.x ?: 0
                        dragAnchorWindowY = bubbleParams?.y ?: 0
                    },
                    onDrag = { x, y -> dragBubbleTo(x, y) },
                    onDragEnd = { endDragBubble() },
                    onTap = { onBubbleTap() },
                )
            }
        }

        val params = WindowManager.LayoutParams(
            bubbleSizePx,
            bubbleSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        windowManager.addView(view, params)
        bubbleView = view
        bubbleParams = params
        bubbleOwner = owner
        view.post { updateBoundsNow() }
    }

    /**
     * Moves the bubble by the pointer's displacement since ACTION_DOWN.
     *
     * [screenX]/[screenY] are absolute screen coordinates (MotionEvent.rawX/rawY). The window
     * origin captured at DOWN is the anchor, so the position never depends on where the window
     * happens to be at the moment of a move — no feedback loop, exactly 1:1 with the finger.
     */
    private fun dragBubbleTo(screenX: Float, screenY: Float) {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val area = displayProvider.usableArea()
        if (area.isEmpty) return

        val minX = area.left.toInt()
        val maxX = max(minX, (area.right - bubbleSizePx).toInt())
        val minY = area.top.toInt()
        val maxY = max(minY, (area.bottom - bubbleSizePx).toInt())

        val desiredX = (dragAnchorWindowX + (screenX - dragDownScreenX)).toInt()
        val desiredY = (dragAnchorWindowY + (screenY - dragDownScreenY)).toInt()

        params.x = desiredX.coerceIn(minX, maxX)
        params.y = desiredY.coerceIn(minY, maxY)
        runCatching { windowManager.updateViewLayout(view, params) }

        // Relayouting the panel on every pointer sample saturates the main thread and starves
        // the event stream (measured: a 516 px drag only reached 361 px). Follow the bubble at
        // a lower cadence while the finger is down; the final position is committed on touch up.
        if (panelExpanded) {
            val now = SystemClock.uptimeMillis()
            if (now - lastPanelRefreshAt >= PANEL_FOLLOW_INTERVAL_MS) {
                lastPanelRefreshAt = now
                refreshPanelPlacement()
            }
        }
        scheduleBoundsUpdate()
    }

    private fun endDragBubble() {
        if (panelExpanded) refreshPanelPlacement()
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val area = displayProvider.usableArea()
        if (area.isEmpty) return

        val snapX = if (params.x + bubbleSizePx / 2f < area.center.x) {
            (area.left + edgeMarginPx).toInt()
        } else {
            (area.right - bubbleSizePx - edgeMarginPx).toInt()
        }

        val startX = params.x
        if (startX != snapX) {
            ValueAnimator.ofInt(startX, snapX).apply {
                duration = 140L
                addUpdateListener { animation ->
                    params.x = animation.animatedValue as Int
                    runCatching { windowManager.updateViewLayout(view, params) }
                    if (panelExpanded) refreshPanelPlacement()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        persistBubblePosition(params.x, params.y)
                        if (panelExpanded) {
                            refreshPanelPlacement()
                            refinePanelPosition()
                        }
                        updateBoundsNow()
                    }
                })
                start()
            }
        } else {
            persistBubblePosition(params.x, params.y)
            updateBoundsNow()
        }
    }

    private fun persistBubblePosition(x: Int, y: Int) {
        scope.launch { repository.saveBubblePosition(x, y) }
    }

    /**
     * Switches the panel between controls and the in-overlay settings sheet. Both layouts are
     * wrap-content windows, so the panel has to be re-anchored once the new height is measured.
     */
    private fun toggleSettingsPanel() {
        panelModeState.value = if (panelModeState.value == PanelMode.CONTROLS) {
            PanelMode.SETTINGS
        } else {
            PanelMode.CONTROLS
        }
        // No manual repositioning here: the new layout measures a frame later and
        // [panelLayoutListener] re-anchors the window when it does.
    }

    private fun updateConfig(transform: (ClickerConfig) -> ClickerConfig) {
        val current = configState.value
        val next = transform(current)
        if (next == current) return
        configState.value = next
        scope.launch { repository.updateConfig(next) }
    }

    private fun onBubbleTap() {
        if (panelExpanded) collapsePanel() else expandPanel()
    }

    // ---------------------------------------------------------------- panel

    private fun expandPanel() {
        if (panelView != null) return
        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        val view = composeView(owner) {
            AutoClickTheme {
                PanelContent(
                    mode = panelModeState.value,
                    config = configState.value,
                    running = runningState.value,
                    errorText = errorLineState.value,
                    onStartStop = {
                        if (runningState.value) {
                            onStopTapping()
                        } else {
                            onStartTapping()
                            // Get out of the way the moment tapping begins. The check runs one
                            // frame later so a refused start (bubble over the target, missing
                            // permission) keeps the panel open with its explanation visible.
                            val startedPanel = panelView
                            startedPanel?.postDelayed(
                                {
                                    // Fold only the panel this Start was pressed on. If the user
                                    // already closed it and opened a fresh one within the grace
                                    // period, that new panel must stay open.
                                    if (runningState.value && panelView === startedPanel) {
                                        collapsePanel()
                                    }
                                },
                                PANEL_AUTOMINIMIZE_DELAY_MS,
                            )
                        }
                    },
                    onToggleSettings = { toggleSettingsPanel() },
                    onCollapse = { collapsePanel() },
                    // Collapse first: the app is about to cover the screen, so leaving a panel
                    // floating on top of it (and of the app's own scroll view) helps nobody.
                    onOpenApp = {
                        collapsePanel()
                        onOpenApp()
                    },
                    // Any cell tap switches back to coarse aiming, so the pin never lingers
                    // after the user has clearly chosen a cell.
                    onSelectCell = { index ->
                        updateConfig { it.copy(cellIndex = index, targetMode = TargetMode.CELL) }
                    },
                    onIntervalChange = { deltaSeconds ->
                        updateConfig { config ->
                            ClickerConfig.ofSeconds(
                                cellIndex = config.cellIndex,
                                intervalSeconds = (config.intervalSeconds + deltaSeconds)
                                    .coerceIn(
                                        ClickerConfig.MIN_INTERVAL_SECONDS,
                                        ClickerConfig.MAX_INTERVAL_SECONDS,
                                    ),
                                stopAfterTaps = config.stopAfterTaps,
                                hapticOnTap = config.hapticOnTap,
                                flashOnTap = config.flashOnTap,
                                // The panel only edits the interval here; dropping the mode would
                                // silently reset an exact target back to the cell centre.
                                targetMode = config.targetMode,
                                point = config.point,
                            )
                        }
                    },
                )
            }
        }

        val params = WindowManager.LayoutParams(
            panelWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        computePanelPosition(params)
        try {
            windowManager.addView(view, params)
        } catch (error: RuntimeException) {
            // Window token rejected (service tearing down, permission revoked): stay collapsed
            // instead of crashing the whole service.
            owner.onDestroy()
            panelExpanded = false
            return
        }

        panelExpanded = true
        panelView = view
        panelParams = params
        panelOwner = owner
        view.addOnLayoutChangeListener(panelLayoutListener)

        view.alpha = 0f
        view.animate().alpha(1f).setDuration(120L).start()
        view.post {
            refinePanelPosition()
            updateBoundsNow()
        }
    }

    private fun collapsePanel() {
        val view = panelView ?: return
        val owner = panelOwner
        panelView = null
        panelParams = null
        panelOwner = null
        panelExpanded = false
        panelBounds = null
        panelModeState.value = PanelMode.CONTROLS
        view.removeOnLayoutChangeListener(panelLayoutListener)
        view.animate().cancel()
        runCatching { windowManager.removeView(view) }
        owner?.onDestroy()
    }

    private fun computePanelPosition(params: WindowManager.LayoutParams) {
        val bubble = bubbleParams ?: return
        val area = displayProvider.usableArea()
        if (area.isEmpty) return

        val bubbleCenterX = bubble.x + bubbleSizePx / 2f
        val preferredX = if (bubbleCenterX < area.center.x) {
            bubble.x + bubbleSizePx + gapPx
        } else {
            bubble.x - panelWidthPx - gapPx
        }
        params.x = preferredX
            .coerceIn(area.left.toInt(), max(area.left.toInt(), (area.right - panelWidthPx).toInt()))
        // The controls page is ~130dp tall, the settings page ~298dp. Anchoring on the 108dp
        // estimate is fine before the first measure and wrong forever after: dragging the hand
        // with the settings page open left the panel 285px off-centre. Use the measured height
        // whenever there is one, and let the layout listener keep it fresh.
        val panelHeight = panelView?.height?.takeIf { it > 0 } ?: panelEstimateHeightPx
        params.y = (bubble.y + bubbleSizePx / 2f - panelHeight / 2f).toInt()
            .coerceIn(area.top.toInt(), max(area.top.toInt(), (area.bottom - panelHeight).toInt()))
    }

    private fun refinePanelPosition() {
        val view = panelView ?: return
        val params = panelParams ?: return
        val bubble = bubbleParams ?: return
        val area = displayProvider.usableArea()
        if (area.isEmpty) return

        val measuredHeight = view.height.takeIf { it > 0 } ?: return
        val y = (bubble.y + bubbleSizePx / 2f - measuredHeight / 2f).toInt()
            .coerceIn(area.top.toInt(), max(area.top.toInt(), (area.bottom - measuredHeight).toInt()))
        if (params.y != y) {
            params.y = y
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun refreshPanelPlacement() {
        val view = panelView ?: return
        val params = panelParams ?: return
        computePanelPosition(params)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    // ---------------------------------------------------------------- crosshair

    private fun ensureCrosshair(point: ScreenPoint): CrosshairView? {
        val existing = crosshairView
        if (existing != null) {
            moveCrosshair(point)
            return existing
        }

        val area = displayProvider.usableArea()
        if (area.isEmpty) return null

        val view = CrosshairView(context)
        view.alpha = 0f
        val params = WindowManager.LayoutParams(
            crosshairSizePx,
            crosshairSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        params.x = (point.x - crosshairSizePx / 2f).toInt()
        params.y = (point.y - crosshairSizePx / 2f).toInt()

        runCatching { windowManager.addView(view, params) }.onFailure { return null }
        crosshairView = view
        crosshairParams = params
        return view
    }

    private fun moveCrosshair(point: ScreenPoint) {
        val view = crosshairView ?: return
        val params = crosshairParams ?: return
        val x = (point.x - crosshairSizePx / 2f).toInt()
        val y = (point.y - crosshairSizePx / 2f).toInt()
        if (params.x != x || params.y != y) {
            params.x = x
            params.y = y
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun removeCrosshair() {
        val view = crosshairView ?: return
        crosshairView = null
        crosshairParams = null
        view.animate().cancel()
        runCatching { windowManager.removeView(view) }
    }

    // ---------------------------------------------------------------- state -> UI

    private fun observe() {
        if (observing) return
        observing = true

        scope.launch {
            combine(coordinator.state, configFlow) { state, config -> state to config }
                .collect { (state, config) -> render(state, config) }
        }

        scope.launch {
            coordinator.tapEvents.collect { point ->
                // Drive the countdown ring off the same delivered-tap signal as the crosshair
                // flash, so the ring resets exactly when a tap lands — independent of whether
                // the flash itself is switched on.
                tapCountState.value += 1
                flashTap(point)
            }
        }
    }

    private fun render(state: ClickerState, config: ClickerConfig) {
        runningState.value = state is ClickerState.Running
        pausedState.value = (state as? ClickerState.Running)?.paused == true
        finishedState.value = state is ClickerState.Finished
        errorLineState.value = (state as? ClickerState.Error)?.let { failureMessage(it.reason) }
        configState.value = config

        if (state is ClickerState.Running) {
            // The marker must show what the engine is aiming at *now*: an exact point is not a
            // cell centre, and showing the cell's centre would be a lie the user has no way to
            // disprove — it is their only evidence of where the tap went.
            val area = displayProvider.usableArea()
            if (!area.isEmpty) ensureCrosshair(config.target.resolveIn(area))
        } else {
            removeCrosshair()
        }
    }

    private fun flashTap(point: ScreenPoint) {
        val config = configFlow.value
        if (config.flashOnTap) {
            // [point] is where the tap actually landed, so the marker goes straight there —
            // ensureCrosshair creates or moves the window and already guards an empty area.
            val view = ensureCrosshair(point)
            if (view != null) {
                view.animate().cancel()
                view.alpha = 1f
                view.animate().alpha(0f).setDuration(260L).start()
            }
        }
        if (config.hapticOnTap) vibrate()
    }

    private fun vibrate() {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun composeView(owner: OverlayLifecycleOwner, content: @Composable () -> Unit): ComposeView {
        val view = ComposeView(context)
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        view.setContent(content)
        return view
    }

    private fun screenBoundsOf(view: View): ScreenRect? {
        if (view.width <= 0 || view.height <= 0) return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return ScreenRect(
            left = location[0].toFloat(),
            top = location[1].toFloat(),
            right = (location[0] + view.width).toFloat(),
            bottom = (location[1] + view.height).toFloat(),
        )
    }

    private fun scheduleBoundsUpdate() {
        if (boundsUpdateScheduled) return
        val view = bubbleView
        if (view == null) {
            bubbleBounds = null
            return
        }
        boundsUpdateScheduled = true
        view.postOnAnimation {
            boundsUpdateScheduled = false
            updateBoundsNow()
        }
    }

    private fun restoreSavedPosition() {
        scope.launch {
            val saved = repository.bubblePosition.first()
            val params = bubbleParams ?: return@launch
            val view = bubbleView ?: return@launch
            val area = displayProvider.usableArea()
            if (area.isEmpty) return@launch

            params.x = saved?.x?.coerceIn(
                area.left.toInt(),
                max(area.left.toInt(), (area.right - bubbleSizePx).toInt()),
            ) ?: params.x
            params.y = saved?.y?.coerceIn(
                area.top.toInt(),
                max(area.top.toInt(), (area.bottom - bubbleSizePx).toInt()),
            ) ?: params.y

            runCatching { windowManager.updateViewLayout(view, params) }
            if (panelExpanded) refreshPanelPlacement()
            updateBoundsNow()
        }
    }
}

/** How often the expanded panel may follow the bubble while a drag is in progress. */
private const val PANEL_FOLLOW_INTERVAL_MS = 48L

/** Grace period after Start before the panel folds itself away. */
private const val PANEL_AUTOMINIMIZE_DELAY_MS = 160L
