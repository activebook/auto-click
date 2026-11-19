package com.activebook.autoclick.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.activebook.autoclick.app.domain.geometry.RelativePoint

/** Target marker: ring plus center dot. */
@Composable
fun CrosshairGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(26.dp)) {
        val radius = size.minDimension / 2f - 2.dp.toPx()
        drawCircle(color, radius, Offset(size.width / 2f, size.height / 2f), style = Stroke(1.6.dp.toPx()))
        drawCircle(color, 3.dp.toPx(), Offset(size.width / 2f, size.height / 2f))
    }
}

/**
 * The nine-grid. Cells tile the whole box, matching [com.activebook.autoclick.app.domain.geometry.GridGeometry]
 * exactly, so what the user taps here is the cell the engine will target.
 *
 * When [pointMarker] is set the grid stops being a selector and becomes a locator: no cell is
 * highlighted, and a pin sits at the exact point. That is the honest picture of "the target is not
 * a cell", which is why the pin exists instead of a nearest-cell highlight that would lie.
 */
@Composable
fun NineGrid(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.labelMedium,
    showLabelWhenSelected: Boolean = false,
    pointMarker: RelativePoint? = null,
    labelAlignment: Alignment = Alignment.Center,
) {
    val pinColor = MaterialTheme.colorScheme.primary
    Box(modifier = modifier) {
        NineCells(
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            labelStyle = labelStyle,
            showLabelWhenSelected = showLabelWhenSelected,
            showSelection = pointMarker == null,
            labelAlignment = labelAlignment,
        )
        if (pointMarker != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawPin(
                    center = Offset(
                        x = pointMarker.x * size.width,
                        y = pointMarker.y * size.height,
                    ),
                    color = pinColor,
                )
            }
        }
    }
}

@Composable
private fun NineCells(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    labelStyle: TextStyle,
    showLabelWhenSelected: Boolean,
    showSelection: Boolean,
    labelAlignment: Alignment,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0 until 3) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (column in 0 until 3) {
                    val index = row * 3 + column
                    val selected = showSelection && index == selectedIndex
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(shape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = shape,
                            )
                            .clickable { onSelect(index) }
                            .semantics {
                                contentDescription = "Target cell ${index + 1}"
                            },
                        contentAlignment = labelAlignment,
                    ) {
                        if (selected) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // The crosshair means "the tap lands here", which is only true of a
                                // cell's centre. Corner-aligned labels (the full-screen picker) are
                                // indices, not aims, so they do not get one.
                                if (labelAlignment == Alignment.Center) {
                                    CrosshairGlyph(color = MaterialTheme.colorScheme.primary)
                                }
                                if (showLabelWhenSelected) {
                                    Text(
                                        text = "${index + 1}",
                                        style = labelStyle,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = labelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ring plus dot: readable at 10 dp, so it survives the 132 dp preview as well as a full screen. */
private fun DrawScope.drawPin(center: Offset, color: Color) {
    drawCircle(color, radius = 7.dp.toPx(), center = center, style = Stroke(1.8.dp.toPx()))
    drawCircle(color, radius = 2.dp.toPx(), center = center)
}
