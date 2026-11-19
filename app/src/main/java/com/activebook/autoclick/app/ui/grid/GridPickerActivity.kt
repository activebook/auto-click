package com.activebook.autoclick.app.ui.grid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.activebook.autoclick.app.AutoClickApplication
import com.activebook.autoclick.app.domain.geometry.GridGeometry
import com.activebook.autoclick.app.domain.geometry.RelativePoint
import com.activebook.autoclick.app.domain.geometry.ScreenPoint
import com.activebook.autoclick.app.domain.geometry.ScreenRect
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.domain.model.TargetMode
import com.activebook.autoclick.app.ui.components.NineGrid
import com.activebook.autoclick.app.ui.theme.AutoClickTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Full-screen picker with two modes, both writing to the same place:
 *
 * - **Cell** — the nine zones tile the *real* display (bars and cutout excluded), so the zone
 *   tapped here is exactly the zone the engine will press. Only the index is derived, so the
 *   choice survives rotation and resolution changes.
 * - **Exact** — the touch point itself becomes the target, stored as a fraction of that same
 *   display area. This is the only way to aim at a spot that is not a cell centre.
 *
 * The mode switch is explicit. Snapping "near a centre means the cell" was rejected: it would
 * silently rewrite a deliberate pixel into a cell centre, and the user could neither see it
 * happening nor undo it.
 */
class GridPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AutoClickApplication).container

        setContent {
            val config by container.config.collectAsStateWithLifecycle()
            AutoClickTheme {
                GridPickerScreen(
                    config = config,
                    areaProvider = { container.displayProvider.usableArea() },
                    onPickCell = { index ->
                        save(config, TargetMode.CELL, index, config.point)
                    },
                    onPickPoint = { point ->
                        save(config, TargetMode.POINT, config.cellIndex, point)
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun save(
        config: ClickerConfig,
        mode: TargetMode,
        cellIndex: Int,
        point: RelativePoint,
    ) {
        lifecycleScope.launch {
            container().repository.updateConfig(
                ClickerConfig.ofSeconds(
                    cellIndex = cellIndex,
                    intervalSeconds = config.intervalSeconds,
                    stopAfterTaps = config.stopAfterTaps,
                    hapticOnTap = config.hapticOnTap,
                    flashOnTap = config.flashOnTap,
                    targetMode = mode,
                    point = point,
                ),
            )
            finish()
        }
    }

    private fun container() = (application as AutoClickApplication).container
}

@Composable
private fun GridPickerScreen(
    config: ClickerConfig,
    areaProvider: () -> ScreenRect,
    onPickCell: (Int) -> Unit,
    onPickPoint: (RelativePoint) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(config.targetMode) }
    var marker by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Light scrim on purpose: the app underneath stays readable, so "where the tap lands"
            // is a real place on a real screen rather than an abstract diagram.
            .background(Color(0x99101618)),
    ) {
        if (mode == TargetMode.CELL) {
            NineGrid(
                selectedIndex = config.cellIndex,
                onSelect = onPickCell,
                labelStyle = MaterialTheme.typography.headlineSmall,
                showLabelWhenSelected = true,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(2.dp),
            )
        } else {
            ExactSpotLayer(
                areaProvider = areaProvider,
                marker = marker,
                onPress = { marker = it },
                onPick = onPickPoint,
            )
        }

        // Chrome lives at the top on purpose: a floating bar at the bottom would sit on top of
        // cells 7-9 and swallow taps. Only the switch and the button have pointer handlers, so
        // the rest of the hint bar lets taps fall through to the layer underneath.
        PickerBar(
            mode = mode,
            config = config,
            modifier = Modifier.align(Alignment.TopCenter),
            onModeChange = { newMode ->
                mode = newMode
                marker = null
            },
            onDismiss = onDismiss,
        )
    }
}

/**
 * The exact mode: thirds drawn faintly for orientation, a crosshair following the finger, and the
 * touch point committed on release.
 *
 * The committed value is the touch position *plus* the window's location on screen, then mapped
 * through the same [ScreenRect] the engine uses. Skipping the window offset would put the target
 * off by the height of the status bar on any ROM that insets the window instead of drawing behind
 * its bars.
 */
@Composable
private fun ExactSpotLayer(
    areaProvider: () -> ScreenRect,
    marker: Offset?,
    onPress: (Offset) -> Unit,
    onPick: (RelativePoint) -> Unit,
) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        onPress(offset)
                        tryAwaitRelease()
                    },
                    onTap = { offset ->
                        onPress(offset)
                        val location = IntArray(2).also(view::getLocationOnScreen)
                        val screenPoint = ScreenPoint(
                            x = offset.x + location[0],
                            y = offset.y + location[1],
                        )
                        onPick(RelativePoint.ofPoint(screenPoint, areaProvider()))
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val location = IntArray(2).also(view::getLocationOnScreen)
            val area = areaProvider()
            val left = area.left - location[0]
            val top = area.top - location[1]
            val width = area.width
            val height = area.height

            val lineColor = Color(0x66FFFFFF)
            for (third in 1..2) {
                val x = left + width * third / 3f
                drawLine(
                    color = lineColor,
                    start = Offset(x, top),
                    end = Offset(x, top + height),
                    strokeWidth = 1.dp.toPx(),
                )
                val y = top + height * third / 3f
                drawLine(
                    color = lineColor,
                    start = Offset(left, y),
                    end = Offset(left + width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            marker?.let { point ->
                drawCircle(
                    color = Color(0xFF8FE3D8),
                    radius = 15.dp.toPx(),
                    center = point,
                    style = Stroke(2.dp.toPx()),
                )
                drawCircle(color = Color(0xFF8FE3D8), radius = 3.dp.toPx(), center = point)
            }
        }
    }
}

@Composable
private fun PickerBar(
    mode: TargetMode,
    config: ClickerConfig,
    modifier: Modifier = Modifier,
    onModeChange: (TargetMode) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF2101A1C),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp, start = 12.dp, end = 12.dp),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (mode == TargetMode.CELL) {
                            "Tap the third of the screen to press"
                        } else {
                            "Tap the exact spot to press"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Text(
                        text = if (mode == TargetMode.CELL) {
                            "Currently " + GridGeometry.cellName(config.cellIndex) +
                                " \u00b7 zones match the real screen"
                        } else {
                            "A fixed screen position \u00b7 it does not follow page scrolling"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xCCFFFFFF),
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White)
                }
            }

            Row(
                modifier = Modifier.padding(top = 4.dp, end = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeChip("Cell", selected = mode == TargetMode.CELL) {
                    onModeChange(TargetMode.CELL)
                }
                ModeChip(
                    text = if (config.isExactPoint) {
                        "Exact \u00b7 " + percent(config.point.x) + " \u00b7 " + percent(config.point.y)
                    } else {
                        "Exact"
                    },
                    selected = mode == TargetMode.POINT,
                ) {
                    onModeChange(TargetMode.POINT)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF2B4A48) else Color(0x33FFFFFF),
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color(0xFF8FE3D8) else Color(0xCCFFFFFF),
            )
        }
    }
}

private fun percent(fraction: Float): String = (fraction * 100f).roundToInt().toString() + "%"
