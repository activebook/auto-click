package com.activebook.autoclick.app.ui.grid

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.activebook.autoclick.app.AutoClickApplication
import com.activebook.autoclick.app.domain.geometry.RelativePoint
import com.activebook.autoclick.app.domain.geometry.ScreenPoint
import com.activebook.autoclick.app.domain.geometry.ScreenRect
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.domain.model.TargetMode
import com.activebook.autoclick.app.ui.components.NineGrid
import com.activebook.autoclick.app.ui.theme.AutoClickTheme
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

/**
 * Full-screen picker for one mode at a time, both writing to the same place:
 *
 * - **Cell** — the nine zones tile the *real* display (bars and cutout excluded), so the zone
 *   tapped here is exactly the zone the engine will press. Only the index is derived, so the
 *   choice survives rotation and resolution changes.
 * - **Exact** — the touch point itself becomes the target, stored as a fraction of that same
 *   display area. This is the only way to aim at a spot that is not a cell centre.
 *
 * The mode is chosen *before* entering, by which button the user pressed on the app's target card:
 * a switch inside the picker competed with the thing the picker is for. Snapping "near a centre
 * means the cell" was rejected too: it would silently rewrite a deliberate pixel into a cell
 * centre, and the user could neither see it happening nor undo it.
 */
class GridPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AutoClickApplication).container
        // An unknown or absent extra falls back to the live config, so an old caller that only
        // knows "open the picker" still lands somewhere sensible.
        val mode = runCatching { TargetMode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty()) }
            .getOrDefault(container.config.value.targetMode)

        setContent {
            val config by container.config.collectAsStateWithLifecycle()
            AutoClickTheme {
                GridPickerScreen(
                    mode = mode,
                    config = config,
                    areaProvider = { container.displayProvider.usableArea() },
                    onPickCell = { index ->
                        save(config, TargetMode.CELL, index, config.point)
                    },
                    onPickPoint = { point ->
                        save(config, TargetMode.POINT, config.cellIndex, point)
                    },
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

    companion object {
        private const val EXTRA_MODE = "mode"

        fun intent(context: Context, mode: TargetMode): Intent =
            Intent(context, GridPickerActivity::class.java).putExtra(EXTRA_MODE, mode.name)
    }
}

@Composable
private fun GridPickerScreen(
    mode: TargetMode,
    config: ClickerConfig,
    areaProvider: () -> ScreenRect,
    onPickCell: (Int) -> Unit,
    onPickPoint: (RelativePoint) -> Unit,
) {
    var liveMarker by remember { mutableStateOf<Offset?>(null) }

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
                labelStyle = MaterialTheme.typography.titleLarge,
                showLabelWhenSelected = true,
                // Numbers sit in a corner so the middle of the screen stays empty: that is where
                // the hint goes, and it is also the first place a thumb reaches for.
                labelAlignment = Alignment.TopStart,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(2.dp),
            )
        } else {
            ExactSpotLayer(
                areaProvider = areaProvider,
                existing = config.point,
                marker = liveMarker,
                onPress = { liveMarker = it },
                onPick = onPickPoint,
            )
        }

        // The whole of the chrome: one large line, dead centre, no panel and no pointer handler,
        // so it explains the screen without taking a single tap away from it. There is no Cancel:
        // a tap *is* the answer, and Back is how you leave, so a button would only cover the thing
        // the user came to see.
        TipLine(
            text = if (mode == TargetMode.CELL) "Tap a cell" else "Tap the exact spot",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
        )
    }
}

/**
 * The exact mode: thirds drawn faintly for orientation, the stored spot marked, a crosshair
 * following the finger, and the touch point committed on release.
 *
 * The committed value is the touch position *plus* the window's location on screen, then mapped
 * through the same [ScreenRect] the engine uses. Skipping the window offset would put the target
 * off by the height of the status bar on any ROM that insets the window instead of drawing behind
 * its bars.
 */
@Composable
private fun ExactSpotLayer(
    areaProvider: () -> ScreenRect,
    existing: RelativePoint,
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

            // What is stored now, faint, so "another spot" can be judged against it.
            val stored = Offset(left + width * existing.x, top + height * existing.y)
            drawCircle(
                color = Color(0x668FE3D8),
                radius = 15.dp.toPx(),
                center = stored,
                style = Stroke(2.dp.toPx()),
            )
            drawCircle(color = Color(0x668FE3D8), radius = 3.dp.toPx(), center = stored)

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
private fun TipLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        // Big on purpose: it steals no taps, so it can afford to be read at a glance. A background
        // would be the only thing here that could cover the screen, and it is not needed — a
        // shadow keeps the words legible over any app.
        style = MaterialTheme.typography.headlineSmall.copy(
            textAlign = TextAlign.Center,
            shadow = Shadow(color = Color(0xCC000000), offset = Offset(0f, 2f), blurRadius = 8f),
        ),
        color = Color(0xFFFFFFFF),
        modifier = modifier,
    )
}
