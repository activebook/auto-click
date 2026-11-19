package com.activebook.autoclick.app.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.ui.components.CloseGlyph
import com.activebook.autoclick.app.ui.components.EyeOffGlyph
import com.activebook.autoclick.app.ui.components.NineGrid
import com.activebook.autoclick.app.ui.components.HandGlyph
import com.activebook.autoclick.app.ui.components.PlayGlyph
import com.activebook.autoclick.app.ui.components.SlidersGlyph
import com.activebook.autoclick.app.ui.components.StopGlyph

/** Hold this long without moving to raise the hide popover (system long-press is ~500 ms). */
private const val HAND_LONG_PRESS_MS = 400L

/**
 * The hide popover is the one deliberately dark control: a small "put it away" affordance that
 * reads as distinct from the white/green hand and panel. Everything else follows the app theme.
 */
private val PodSolid = Color(0xFF101D19)
private val PodFill = PodSolid.copy(alpha = 0.93f)
private val PodBorder = Color(0xFFFFFFFF).copy(alpha = 0.14f)
private val PodIcon = Color(0xFFE8F3F0)
private val PodTonal = Color(0xFFFFFFFF).copy(alpha = 0.10f)

/**
 * Gesture state kept in a remembered holder so a recomposition mid-gesture (the countdown ring
 * animates) cannot reset `dragging`/`longPressFired` or orphan the pending long-press job.
 */
private class BubbleGesture {
    var downX = 0f
    var downY = 0f
    var dragging = false
    var longPressFired = false
    var longPressJob: Job? = null
}

/**
 * The floating hand. One gesture handler drives drag, tap and long-press, so they can never be
 * mistaken for one another: move past slop = drag, release early = tap, hold still = long-press.
 */
@Composable
fun BubbleContent(
    running: Boolean,
    paused: Boolean,
    finished: Boolean,
    intervalMs: Long,
    tapCount: Long,
    onDragStart: (Float, Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val dragStartCallback by rememberUpdatedState(onDragStart)
    val dragCallback by rememberUpdatedState(onDrag)
    val dragEndCallback by rememberUpdatedState(onDragEnd)
    val tapCallback by rememberUpdatedState(onTap)
    val longPressCallback by rememberUpdatedState(onLongPress)

    val context = LocalContext.current
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    val gesture = remember { BubbleGesture() }
    val scope = rememberCoroutineScope()

    // Follows the app's white/green theme (surface + primary), like every other surface in the
    // app, so the floating control reads as part of AutoClick rather than a foreign dark plate.
    // Translucent + no shadow: glass over the host app, not a sticker or a rectangular halo.
    val bubbleColor = if (running) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    }
    val glyphColor = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

    // Countdown ring: fills 0 -> 1 over one interval, then snaps back to 0 on the next delivered
    // tap (tapCount increments). Because the engine fires a tap exactly one interval after the
    // previous one, the ring reaches the top right as the tap lands — a visible countdown that
    // restarts every cycle. Paused or idle hides it; the ring is a live-run cue, not a dial.
    val ringActive = running && !paused
    val ringColor = MaterialTheme.colorScheme.onPrimary
    val ringProgress = remember { Animatable(0f) }
    LaunchedEffect(ringActive, tapCount, intervalMs) {
        if (!ringActive) {
            ringProgress.snapTo(0f)
            return@LaunchedEffect
        }
        ringProgress.snapTo(0f)
        ringProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = intervalMs.toInt(), easing = LinearEasing),
        )
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bubbleColor)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
            // Raw MotionEvents, not Compose pointer positions: this window is moved *by* the
            // drag, so window-local coordinates shift under the finger and produce a feedback
            // loop (measured: jitter of +/-40 px and a drag that never matches the finger).
            // `MotionEvent.rawX/rawY` are true screen coordinates and are immune to that.
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        gesture.downX = event.rawX
                        gesture.downY = event.rawY
                        gesture.dragging = false
                        gesture.longPressFired = false
                        gesture.longPressJob?.cancel()
                        // Arm the hold timer; a drag or an early release cancels it below.
                        gesture.longPressJob = scope.launch {
                            delay(HAND_LONG_PRESS_MS)
                            if (!gesture.dragging) {
                                gesture.longPressFired = true
                                longPressCallback()
                            }
                        }
                        dragStartCallback(gesture.downX, gesture.downY)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (!gesture.dragging &&
                            (abs(event.rawX - gesture.downX) > touchSlop ||
                                abs(event.rawY - gesture.downY) > touchSlop)
                        ) {
                            gesture.dragging = true
                            gesture.longPressJob?.cancel()
                        }
                        if (gesture.dragging) dragCallback(event.rawX, event.rawY)
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        gesture.longPressJob?.cancel()
                        if (gesture.dragging) {
                            // Events can be coalesced or dropped while the main thread is busy
                            // (the panel relayouts on every move), so commit the final pointer
                            // position here instead of relying on the last MOVE that arrived.
                            dragCallback(event.rawX, event.rawY)
                            dragEndCallback()
                        } else if (!gesture.longPressFired) {
                            tapCallback()
                        }
                        // A fired long-press swallows the UP: the popover is already open.
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        gesture.longPressJob?.cancel()
                        if (gesture.dragging) dragEndCallback()
                        true
                    }

                    else -> false
                }
            }
            .semantics {
                contentDescription = when {
                    running -> "AutoClick is tapping. Tap to open controls."
                    finished -> "AutoClick finished its tap budget. Tap to open controls."
                    else -> "AutoClick is idle. Tap to open controls."
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The countdown ring sits at the perimeter, under the hand and label (which stay centred
        // and never overlap it). A faint full track shows the path; the bright arc sweeps clockwise
        // from the top once per interval. Drawn only while a run is live, so an idle or paused hand
        // looks exactly as it did before.
        Canvas(modifier = Modifier.matchParentSize()) {
            if (ringActive) {
                val stroke = 2.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                val ringStyle = Stroke(width = stroke, cap = StrokeCap.Round)
                drawArc(
                    color = ringColor.copy(alpha = 0.22f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = ringStyle,
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * ringProgress.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = ringStyle,
                )
            }
        }

        // Glyph above, state word below, flush against each other: the glyph's own bottom padding
        // plus the text's line leading leave a hairline of daylight so they read separate without
        // a wasted gap. The stack is centred as a whole on the 40 dp disc.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HandGlyph(color = glyphColor, modifier = Modifier.size(28.dp))
            Text(
                text = when {
                    paused -> "PAUSED"
                    running -> "ON"
                    finished -> "DONE"
                    else -> "OFF"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 6.sp,
                    letterSpacing = 0.3.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = when {
                    paused -> Color(0xFFB45309)
                    finished -> Color(0xFF1B7F3B)
                    running -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                // Placement-only nudge up: tightens the glyph/word gap without moving the glyph
                // or changing the stack's measured height.
                modifier = Modifier.offset(y = (-2).dp),
            )
        }
    }
}

/** What the expanded overlay panel is currently showing. */
enum class PanelMode { CONTROLS, SETTINGS }

/** Expanded control surface: start/stop, settings, collapse. Nothing else — the buttons say the rest. */
/**
 * The long-press hide popover: a single eye-off button in a small card. Tapping it hides the hand
 * and stops the run; the full-screen scrim behind it dismisses on any other tap.
 */
@Composable
fun HidePopover(onHide: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PodFill,
        border = BorderStroke(1.dp, PodBorder),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PodTonal)
                .clickable(onClick = onHide)
                .semantics { contentDescription = "Hide the floating hand and stop" },
            contentAlignment = Alignment.Center,
        ) {
            EyeOffGlyph(color = PodIcon, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun PanelContent(
    mode: PanelMode,
    config: ClickerConfig,
    running: Boolean,
    errorText: String?,
    onStartStop: () -> Unit,
    onToggleSettings: () -> Unit,
    onCollapse: () -> Unit,
    onOpenApp: () -> Unit,
    onSelectCell: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
) {
    if (mode == PanelMode.SETTINGS) {
        SettingsPanel(
            config = config,
            onSelectCell = onSelectCell,
            onIntervalChange = onIntervalChange,
            onDone = onToggleSettings,
            onOpenApp = onOpenApp,
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelButton(
                    description = if (running) "Stop tapping" else "Start tapping",
                    containerColor = if (running) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (running) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    onClick = onStartStop,
                ) { color ->
                    if (running) StopGlyph(color = color) else PlayGlyph(color = color)
                }

                PanelButton(
                    description = "Settings",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onToggleSettings,
                ) { color -> SlidersGlyph(color = color) }

                // An X, not a chevron. A chevron reads as "there is more below me" — the wrong
                // promise for a panel that has nothing below it; the third button closes the panel
                // and leaves the hand, which is exactly what an X means.
                PanelButton(
                    description = "Close panel",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onCollapse,
                ) { color -> CloseGlyph(color = color) }
            }

            // Only a failure gets a line here. A permanent "Idle · Row 2, column 2 · every 1s"
            // repeated what the play button and the settings page already show, and it made the
            // panel taller for no information.
            if (errorText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun PanelButton(
    description: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    glyph: @Composable (Color) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        glyph(contentColor)
    }
}

/**
 * Settings live *inside* the overlay on purpose: a service cannot reliably start an activity
 * from the background (verified: a plain startActivity, and even a PendingIntent send with the
 * Android 14 "allowed" mode, is accepted by the system on vivo / Android 14 but the task never
 * becomes visible). Editing the target and the period in place also keeps the tap loop running.
 */
@Composable
private fun SettingsPanel(
    config: ClickerConfig,
    onSelectCell: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onDone: () -> Unit,
    onOpenApp: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Opens the full app: the panel only carries the two knobs you reach for
                    // mid-session, everything else (permissions, feedback, pick-on-screen) lives
                    // in the app itself.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable(onClick = onOpenApp)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .semantics { contentDescription = "Open the app" },
                    ) {
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClick = onDone)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .semantics { contentDescription = "Close settings" },
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Text(
                text = "Target cell",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // An exact point is drawn as a pin rather than snapped to the nearest cell: the grid
            // here is the only thing that can change the target mid-run, and tapping a cell is
            // the documented way back to coarse aiming.
            NineGrid(
                selectedIndex = config.cellIndex,
                onSelect = onSelectCell,
                pointMarker = if (config.isExactPoint) config.point else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Every",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StepButton(
                        label = "\u2212",
                        description = "Decrease interval",
                        enabled = config.intervalSeconds > ClickerConfig.MIN_INTERVAL_SECONDS,
                        onClick = { onIntervalChange(-1) },
                    )
                    Text(
                        text = "${config.intervalSeconds}s",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(46.dp),
                    )
                    StepButton(
                        label = "+",
                        description = "Increase interval",
                        enabled = config.intervalSeconds < ClickerConfig.MAX_INTERVAL_SECONDS,
                        onClick = { onIntervalChange(1) },
                    )
                }
            }

        }
    }
}

@Composable
private fun StepButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
        )
    }
}
