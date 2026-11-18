package com.activebook.autoclick.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Icon set drawn on a Canvas instead of shipping an icon dependency or scaled bitmap assets.
 * Everything stays vector-crisp at any density and inherits the theme color.
 */

/** A palm with four fingers and a thumb, in a 24x24 logical box. */
@Composable
fun HandGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val unit = size.minDimension / 24f

        fun bar(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
            drawRoundRect(
                color = color,
                topLeft = Offset(left * unit, top * unit),
                size = Size((right - left) * unit, (bottom - top) * unit),
                cornerRadius = CornerRadius(radius * unit, radius * unit),
            )
        }

        // Palm.
        bar(7.0f, 12.2f, 17.0f, 21.6f, 2.6f)
        // Fingers.
        bar(7.5f, 5.4f, 9.4f, 13.0f, 0.95f)
        bar(9.9f, 4.2f, 11.8f, 13.0f, 0.95f)
        bar(12.3f, 5.2f, 14.2f, 13.0f, 0.95f)
        bar(14.7f, 6.9f, 16.6f, 13.0f, 0.95f)
        // Thumb, angled off the palm.
        rotate(degrees = -34f, pivot = Offset(7.4f * unit, 14.4f * unit)) {
            bar(4.4f, 12.6f, 6.4f, 17.6f, 1.0f)
        }
    }
}

@Composable
fun PlayGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.30f, size.height * 0.20f)
            lineTo(size.width * 0.82f, size.height * 0.50f)
            lineTo(size.width * 0.30f, size.height * 0.80f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun StopGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val inset = size.minDimension * 0.27f
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(size.minDimension * 0.10f, size.minDimension * 0.10f),
        )
    }
}

/** Three sliders — a settings glyph that needs no gear geometry. */
@Composable
fun SlidersGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val trackWidth = size.width * 0.72f
        val startX = size.width * 0.14f
        val knobRadius = size.minDimension * 0.09f
        val rows = listOf(0.27f to 0.64f, 0.50f to 0.34f, 0.73f to 0.54f)
        rows.forEach { (y, knobAt) ->
            drawLine(
                color = color,
                start = Offset(startX, size.height * y),
                end = Offset(startX + trackWidth, size.height * y),
                strokeWidth = size.minDimension * 0.075f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = color,
                radius = knobRadius,
                center = Offset(startX + trackWidth * knobAt, size.height * y),
            )
        }
    }
}

@Composable
fun ChevronDownGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = size.minDimension * 0.10f
        drawLine(
            color = color,
            start = Offset(size.width * 0.24f, size.height * 0.38f),
            end = Offset(size.width * 0.50f, size.height * 0.64f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.64f),
            end = Offset(size.width * 0.76f, size.height * 0.38f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun CloseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = size.minDimension * 0.10f
        drawLine(
            color = color,
            start = Offset(size.width * 0.28f, size.height * 0.28f),
            end = Offset(size.width * 0.72f, size.height * 0.72f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.72f, size.height * 0.28f),
            end = Offset(size.width * 0.28f, size.height * 0.72f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Crosshair used in the nine-grid preview to mark the chosen cell. */
fun DrawScope.drawCrosshair(color: Color, radius: Float, stroke: Float) {
    drawCircle(color, radius, Offset(size.width / 2f, size.height / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
    drawCircle(color, stroke * 1.1f, Offset(size.width / 2f, size.height / 2f))
}
