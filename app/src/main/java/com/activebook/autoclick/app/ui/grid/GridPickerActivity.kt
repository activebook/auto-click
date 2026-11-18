package com.activebook.autoclick.app.ui.grid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.activebook.autoclick.app.AutoClickApplication
import com.activebook.autoclick.app.domain.geometry.GridGeometry
import com.activebook.autoclick.app.domain.model.ClickerConfig
import com.activebook.autoclick.app.ui.components.NineGrid
import com.activebook.autoclick.app.ui.theme.AutoClickTheme
import kotlinx.coroutines.launch

/**
 * Full-screen picker: the nine zones cover the *real* display (status bar and navigation bar
 * excluded), so the zone tapped here is exactly the zone the engine will press. A scaled-down
 * preview told the user how the thirds are laid out; this lets them point at the actual spot.
 *
 * Only the *cell index* is derived here — never a pixel coordinate — so the choice stays correct
 * across rotation and resolution changes.
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
                    selectedIndex = config.cellIndex,
                    onSelect = { index ->
                        val current = config
                        lifecycleScope.launch {
                            container.repository.updateConfig(
                                ClickerConfig.ofSeconds(
                                    cellIndex = index,
                                    intervalSeconds = current.intervalSeconds,
                                    stopAfterTaps = current.stopAfterTaps,
                                    hapticOnTap = current.hapticOnTap,
                                    flashOnTap = current.flashOnTap,
                                ),
                            )
                            finish()
                        }
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }
}

@Composable
private fun GridPickerScreen(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Light scrim on purpose: the app underneath stays readable, so "where the tap lands"
            // is a real place on a real screen rather than an abstract diagram.
            .background(Color(0x99101618)),
    ) {
        NineGrid(
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            labelStyle = MaterialTheme.typography.headlineSmall,
            showLabelWhenSelected = true,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(2.dp),
        )

        // Chrome lives at the top on purpose: a floating bar at the bottom would sit on top of
        // cells 7-9 and swallow taps. Only the button below has a pointer handler, so the rest of
        // the hint bar lets taps fall through to the zone underneath.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF2101A1C),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, start = 12.dp, end = 12.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Tap the third of the screen to press",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Text(
                        text = "Currently " + GridGeometry.cellName(selectedIndex) + " · zones match the real screen",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xCCFFFFFF),
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White)
                }
            }
        }
    }
}
