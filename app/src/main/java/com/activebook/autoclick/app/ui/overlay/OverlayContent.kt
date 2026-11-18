package com.activebook.autoclick.app.ui.overlay

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.domain.geometry.GridGeometry
import com.activebook.autoclick.app.ui.components.ChevronDownGlyph
import com.activebook.autoclick.app.ui.components.NineGrid
import com.activebook.autoclick.app.ui.components.HandGlyph
import com.activebook.autoclick.app.ui.components.PlayGlyph
import com.activebook.autoclick.app.ui.components.SlidersGlyph
import com.activebook.autoclick.app.ui.components.StopGlyph

/**
 * The floating hand. One gesture handler drives both drag and tap, so a drag can never be
 * mistaken for a tap (and vice versa) — no double detectors fighting each other.
 */
@Composable
fun BubbleContent(
    running: Boolean,
    paused: Boolean,
    onDragStart: (Float, Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
) {
    val dragStartCallback by rememberUpdatedState(onDragStart)
    val dragCallback by rememberUpdatedState(onDrag)
    val dragEndCallback by rememberUpdatedState(onDragEnd)
    val tapCallback by rememberUpdatedState(onTap)

    val context = LocalContext.current
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var downX = 0f
    var downY = 0f
    var dragging = false

    // Translucent on purpose: the hand should look like it floats over the app, not like an
    // opaque decal stuck to it. No drop shadow either - Compose renders that as a rectangular
    // halo behind a circular view, which is exactly the "ugly plate" this avoids.
    val bubbleColor = if (running) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    }
    val glyphColor = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(bubbleColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), CircleShape)
            // Raw MotionEvents, not Compose pointer positions: this window is moved *by* the
            // drag, so window-local coordinates shift under the finger and produce a feedback
            // loop (measured: jitter of +/-40 px and a drag that never matches the finger).
            // `MotionEvent.rawX/rawY` are true screen coordinates and are immune to that.
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        dragging = false
                        dragStartCallback(downX, downY)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging &&
                            (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop)
                        ) {
                            dragging = true
                        }
                        if (dragging) dragCallback(event.rawX, event.rawY)
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (dragging) {
                            // Events can be coalesced or dropped while the main thread is busy
                            // (the panel relayouts on every move), so commit the final pointer
                            // position here instead of relying on the last MOVE that arrived.
                            dragCallback(event.rawX, event.rawY)
                            dragEndCallback()
                        } else {
                            tapCallback()
                        }
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        if (dragging) dragEndCallback()
                        true
                    }

                    else -> false
                }
            }
            .semantics {
                contentDescription = if (running) {
                    "AutoClick is tapping. Tap to open controls."
                } else {
                    "AutoClick is idle. Tap to open controls."
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // A word instead of a dot: at 56 dp the dot alone never told the user whether the loop
        // was live, and it vanished entirely when idle.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HandGlyph(color = glyphColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = when {
                    paused -> "PAUSED"
                    running -> "ON"
                    else -> "OFF"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (paused) Color(0xFFB45309) else glyphColor,
            )
        }
    }
}

/** What the expanded overlay panel is currently showing. */
enum class PanelMode { CONTROLS, SETTINGS }

/** Expanded control surface: start/stop, settings, collapse — plus a one-line status. */
@Composable
fun PanelContent(
    mode: PanelMode,
    config: ClickerConfig,
    running: Boolean,
    status: String,
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
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
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (running) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    onClick = onStartStop,
                ) { color ->
                    if (running) StopGlyph(color = color) else PlayGlyph(color = color)
                }

                PanelButton(
                    description = "Settings",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onToggleSettings,
                ) { color -> SlidersGlyph(color = color) }

                PanelButton(
                    description = "Minimize controls",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onCollapse,
                ) { color -> ChevronDownGlyph(color = color) }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
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
            .size(46.dp)
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
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
            NineGrid(
                selectedIndex = config.cellIndex,
                onSelect = onSelectCell,
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

            Text(
                text = GridGeometry.cellName(config.cellIndex) + " \u00b7 " + config.intervalSeconds + "s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
