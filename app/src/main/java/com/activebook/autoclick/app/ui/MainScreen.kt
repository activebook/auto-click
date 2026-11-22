package com.activebook.autoclick.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.activebook.autoclick.app.domain.geometry.GridGeometry
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.domain.model.ClickerState
import com.activebook.autoclick.app.domain.model.TargetMode
import com.activebook.autoclick.app.ui.components.ChevronDownGlyph
import com.activebook.autoclick.app.ui.components.HandGlyph
import com.activebook.autoclick.app.ui.components.NineGrid
import kotlin.math.roundToInt
import com.activebook.autoclick.app.domain.model.FailureReason

private val SuccessGreen = Color(0xFF15803D)
private val EnableOrange = Color(0xFFC2410C)

private val WarningAmber = Color(0xFFB45309)

/** The neutral grey Android's own settings switches use for "off". */
private val SystemTrack = Color(0xFFD9D9D9)

private val INTERVAL_PRESETS = listOf(1, 2, 5, 10, 30, 60)

/** Never, then a short ladder of common budgets. Anything else goes in the custom field. */
private val TAP_BUDGET_PRESETS = listOf(0, 1, 3, 5, 10, 100)

@Composable
fun MainScreen(
    config: ClickerConfig,
    state: ClickerState,
    permissions: PermissionSnapshot,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCellSelected: (Int) -> Unit,
    onIntervalSeconds: (Int) -> Unit,
    onStopAfterTaps: (Int) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onFlash: (Boolean) -> Unit,
    handVisible: Boolean,
    onToggleHand: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onToggleAccessibility: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleBattery: (Boolean) -> Unit,
    onPickTarget: (TargetMode) -> Unit,
) {
    val running = state is ClickerState.Running
    // Two switches actually gate tapping: Access for the tap itself, and Display over other apps
    // for the floating hand. Notifications and the battery exemption are comfort only.
    val canStart = permissions.accessibility && permissions.overlay
    val allPermissions = permissions.overlay && permissions.accessibility &&
        permissions.notifications && permissions.batteryUnrestricted

    val scrollState = rememberScrollState()
    var permissionsExpanded by remember(allPermissions) { mutableStateOf(!allPermissions) }

    // One composable, two places: while something is still off it belongs directly under Status,
    // because that is the first thing worth acting on. Once all four are on there is nothing left
    // to do, so it drops to the bottom of the page.
    val permissionsSection: @Composable () -> Unit = {
        PermissionsCard(
            permissions = permissions,
            expanded = permissionsExpanded,
            onToggle = { permissionsExpanded = !permissionsExpanded },
            onToggleOverlay = onToggleOverlay,
            onToggleAccessibility = onToggleAccessibility,
            onToggleNotifications = onToggleNotifications,
            onToggleBattery = onToggleBattery,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header()

        StatusCard(
            config = config,
            state = state,
            running = running,
            canStart = canStart,
            accessibilityGranted = permissions.accessibility,
            overlayGranted = permissions.overlay,
            onStart = onStart,
            onStop = onStop,
            onOpenAccessibility = onOpenAccessibility,
            onOpenOverlay = onEnableOverlay,
        )

        if (!allPermissions) permissionsSection()

        // Always visible (not folded into Advanced) so the hand can be brought back from here
        // after it was hidden. Hiding stops the run; this switch is the way back (ADR-021).
        val canShowHand = permissions.overlay && permissions.accessibility
        SectionCard(
            title = "Floating hand",
            subtitle = null,
        ) {
            SwitchRow(
                label = "Show floating hand",
                hint = when {
                    !permissions.overlay && !permissions.accessibility ->
                        "Needs Accessibility and \"Display over other apps\" first."
                    !permissions.overlay -> "Needs \"Display over other apps\" first."
                    !permissions.accessibility -> "Needs Accessibility first."
                    handVisible -> "The hand floats over other apps."
                    else -> "Hidden. Turn on to bring it back."
                },
                checked = handVisible && canShowHand,
                onCheckedChange = onToggleHand,
                enabled = canShowHand,
            )
        }

        SectionCard(
            title = "Where to tap",
            subtitle = "A cell centre, or any spot you point at.",
        ) {
            // Compact on purpose: this is an indicator of *where* the tap lands, not a control
            // surface the size of the screen. A small square next to the description reads faster.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NineGrid(
                    selectedIndex = config.cellIndex,
                    onSelect = onCellSelected,
                    pointMarker = if (config.isExactPoint) config.point else null,
                    modifier = Modifier.size(132.dp),
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (config.isExactPoint) {
                            "Exact spot"
                        } else {
                            GridGeometry.cellName(config.cellIndex)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (config.isExactPoint) {
                            percentOf(config.point.x) + " · " + percentOf(config.point.y) +
                                " from the top-left of the screen"
                        } else {
                            "Its centre is pressed."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Two entries instead of one generic "pick on screen": the mode is decided *before*
            // the screen is covered, so the picker never has to ask a question while it is in the
            // way of the very thing being picked. The filled one is the live mode.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TargetPickButton(
                    text = "Pick a cell",
                    active = !config.isExactPoint,
                    onClick = { onPickTarget(TargetMode.CELL) },
                    modifier = Modifier.weight(1f),
                )
                TargetPickButton(
                    text = "Pick exact spot",
                    active = config.isExactPoint,
                    onClick = { onPickTarget(TargetMode.POINT) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        IntervalCard(
            config = config,
            onIntervalSeconds = onIntervalSeconds,
            onStopAfterTaps = onStopAfterTaps,
        )

        AdvancedCard(
            config = config,
            onHaptics = onHaptics,
            onFlash = onFlash,
        )

        // Folded and last only once everything is on — see the note where `permissionsSection` is
        // defined: while a switch is off this card sits right below Status instead.
        if (allPermissions) permissionsSection()

        Text(
            text = "Taps are sent through the accessibility service. Nothing is read from the " +
                "screen and nothing leaves the phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            HandGlyph(color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text("AutoClick", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Nine-grid auto tap",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(
    config: ClickerConfig,
    state: ClickerState,
    running: Boolean,
    canStart: Boolean,
    accessibilityGranted: Boolean,
    overlayGranted: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
) {
    SectionCard(title = "Status", subtitle = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (dotColor, headline) = when (state) {
                is ClickerState.Running -> if (state.paused) {
                    WarningAmber to "Paused"
                } else {
                    SuccessGreen to "Tapping"
                }
                is ClickerState.Finished -> SuccessGreen to "Finished"
                is ClickerState.Error -> MaterialTheme.colorScheme.error to "Needs attention"
                ClickerState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant to "Idle"
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(headline, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Every ${config.intervalSeconds}s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // No status paragraph: the dot and its word already say Idle / Tapping / Finished, and the
        // button already says what it will do. The line survives only for an error, where it is the
        // one thing the user cannot infer and cannot act on without reading it.
        if (state is ClickerState.Error) {
            Text(
                text = failureMessage(state.reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state is ClickerState.Error && state.reason == FailureReason.TARGET_COVERED_BY_BUBBLE) {
            Text(
                text = "Tip: drag the hand to the opposite edge, then press start again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = if (running) onStop else onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = running || canStart,
            colors = if (running) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            Text(if (running) "Stop tapping" else "Start tapping")
        }

        // One button per missing requirement, each naming the system screen it opens. Two can be
        // missing at once on a fresh install, so two buttons is the honest state, not clutter.
        if (!accessibilityGranted || !overlayGranted) {
            Text(
                text = "Tapping needs both of these:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!accessibilityGranted) {
            EnableButton("Enable Accessibility", onOpenAccessibility)
        }
        if (!overlayGranted) {
            EnableButton("Enable Display over other apps", onOpenOverlay)
        }
    }
}

@Composable
private fun PermissionsCard(
    permissions: PermissionSnapshot,
    expanded: Boolean,
    onToggle: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onToggleAccessibility: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleBattery: (Boolean) -> Unit,
) {
    val granted = listOf(
        permissions.overlay,
        permissions.accessibility,
        permissions.notifications,
        permissions.batteryUnrestricted,
    ).count { it }

    CollapsibleSectionCard(
        title = "Before you start",
        subtitle = "Two are required. Tap a switch to open its system screen.",
        badge = if (granted == 4) "All set" else "$granted of 4",
        expanded = expanded,
        onToggle = onToggle,
    ) {
        PermissionSwitchRow(
            label = "Display over other apps",
            hint = "Shows the floating hand.",
            granted = permissions.overlay,
            onToggle = onToggleOverlay,
        )
        PermissionSwitchRow(
            label = "Accessibility: AutoClick taps",
            hint = "Sends the taps.",
            granted = permissions.accessibility,
            onToggle = onToggleAccessibility,
        )

        Text(
            text = "Optional",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionSwitchRow(
            label = "Notifications",
            hint = "Status line with a Stop button.",
            granted = permissions.notifications,
            onToggle = onToggleNotifications,
        )
        PermissionSwitchRow(
            label = "Battery: unrestricted",
            hint = "Keeps the service alive.",
            granted = permissions.batteryUnrestricted,
            onToggle = onToggleBattery,
        )

        if (!permissions.accessibility) {
            Text(
                text = "Greyed out? App info › Allow restricted settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The orange "can't start yet" button. One per missing requirement, so the label always says
 * exactly which system screen it opens.
 */
@Composable
private fun EnableButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = EnableOrange,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A permission as a switch the user can actually operate.
 *
 * Android will not let an app grant or revoke any of these four itself, so the honest behaviour of a
 * tap is: open the system screen that owns the switch, let the user flip it there, then come back.
 * The row reads the real state every second, so it never shows a value the system disagrees with.
 * On and off open different screens — the runtime-permission dialog vs the settings page.
 */
@Composable
private fun PermissionSwitchRow(
    label: String,
    hint: String,
    granted: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!granted) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = granted,
            onCheckedChange = { onToggle(!granted) },
            colors = systemSwitchColors(),
        )
    }
}

/** The stock Material switch, not a tinted one: grey track with a white thumb when off. */
@Composable
private fun systemSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = SystemTrack,
    uncheckedBorderColor = SystemTrack,
)

/** Section that folds: header row is the toggle, the chevron shows the state. */
@Composable
private fun CollapsibleSectionCard(
    title: String,
    subtitle: String?,
    badge: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                ChevronDownGlyph(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(20.dp)
                        .rotate(if (expanded) 180f else 0f),
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}

@Composable
private fun AdvancedCard(
    config: ClickerConfig,
    onHaptics: (Boolean) -> Unit,
    onFlash: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    CollapsibleSectionCard(
        title = "Advanced",
        subtitle = "Feedback while it runs. Both on by default.",
        badge = if (config.hapticOnTap && config.flashOnTap) "All on" else "Some off",
        expanded = expanded,
        onToggle = { expanded = !expanded },
    ) {
        SwitchRow(
            label = "Vibrate",
            hint = "A short buzz on every tap.",
            checked = config.hapticOnTap,
            onCheckedChange = onHaptics,
        )
        SwitchRow(
            label = "Flash marker",
            hint = "A ring at the point you just tapped.",
            checked = config.flashOnTap,
            onCheckedChange = onFlash,
        )
    }
}

@Composable
private fun IntervalCard(
    config: ClickerConfig,
    onIntervalSeconds: (Int) -> Unit,
    onStopAfterTaps: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var text by remember(config.intervalSeconds) { mutableStateOf(config.intervalSeconds.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null &&
        parsed in ClickerConfig.MIN_INTERVAL_SECONDS..ClickerConfig.MAX_INTERVAL_SECONDS

    // The budget gets the same chips-plus-custom treatment as the interval: 0 means "never".
    var budgetText by remember(config.stopAfterTaps) { mutableStateOf(config.stopAfterTaps.toString()) }
    val budgetParsed = budgetText.toIntOrNull()
    val budgetValid = budgetParsed != null &&
        budgetParsed in ClickerConfig.NO_TAP_BUDGET..ClickerConfig.MAX_STOP_AFTER_TAPS

    SectionCard(
        title = "Interval",
        subtitle = "Seconds to wait between two taps.",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            INTERVAL_PRESETS.forEach { preset ->
                AssistChip(
                    onClick = { onIntervalSeconds(preset) },
                    label = { Text("${preset}s") },
                    colors = if (preset == config.intervalSeconds) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    text = value.filter(Char::isDigit).take(4)
                },
                label = { Text("Seconds") },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (valid && parsed != config.intervalSeconds) {
                            parsed?.let(onIntervalSeconds)
                        }
                        focusManager.clearFocus()
                    },
                ),
                modifier = Modifier.width(150.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(
                onClick = { parsed?.let(onIntervalSeconds) },
                enabled = valid && parsed != config.intervalSeconds,
            ) {
                Text("Apply")
            }
        }

        Text(
            text = "${ClickerConfig.MIN_INTERVAL_SECONDS}–${ClickerConfig.MAX_INTERVAL_SECONDS} s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = "Stop after",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TAP_BUDGET_PRESETS.forEach { preset ->
                AssistChip(
                    onClick = { onStopAfterTaps(preset) },
                    label = {
                        Text(
                            when (preset) {
                                0 -> "Never"
                                else -> "$preset"
                            },
                        )
                    },
                    colors = if (preset == config.stopAfterTaps) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = budgetText,
                onValueChange = { value ->
                    budgetText = value.filter(Char::isDigit).take(6)
                },
                label = { Text("Taps") },
                singleLine = true,
                isError = budgetText.isNotEmpty() && !budgetValid,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (budgetValid && budgetParsed != config.stopAfterTaps) {
                            budgetParsed?.let(onStopAfterTaps)
                        }
                        focusManager.clearFocus()
                    },
                ),
                modifier = Modifier.width(150.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(
                onClick = { budgetParsed?.let(onStopAfterTaps) },
                enabled = budgetValid && budgetParsed != config.stopAfterTaps,
            ) {
                Text("Apply")
            }
        }

        Text(
            text = "0–${ClickerConfig.MAX_STOP_AFTER_TAPS} taps · 0 = never",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = when (config.stopAfterTaps) {
                ClickerConfig.NO_TAP_BUDGET -> "Runs until you stop it."
                1 -> "Stops by itself after 1 tap."
                else -> "Stops by itself after ${config.stopAfterTaps} taps."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = systemSwitchColors(),
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun TargetPickButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** "52%" — two digits are enough to tell two spots apart and never look like a precision claim. */
private fun percentOf(fraction: Float): String = (fraction * 100f).roundToInt().toString() + "%"
