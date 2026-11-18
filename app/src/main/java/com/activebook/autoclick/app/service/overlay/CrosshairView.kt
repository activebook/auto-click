package com.activebook.autoclick.app.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/**
 * Tiny proof-of-execution marker: a ring flashed at the exact point that was tapped.
 *
 * A plain View (not Compose) because it is created and destroyed per session, never interactive,
 * and only needs one paint pass every tap.
 */
class CrosshairView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFF2DD4BF.toInt()
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFF0F766E.toInt()
    }

    init {
        // Purely decorative: never intercepts a touch, never takes focus.
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - ringPaint.strokeWidth

        canvas.drawCircle(centerX, centerY, radius, ringPaint)
        canvas.drawCircle(centerX, centerY, ringPaint.strokeWidth * 1.4f, dotPaint)

        val tickLength = radius * 0.55f
        canvas.drawLine(centerX, centerY - radius - tickLength * 0.25f, centerX, centerY - radius + tickLength, tickPaint)
        canvas.drawLine(centerX, centerY + radius - tickLength, centerX, centerY + radius + tickLength * 0.25f, tickPaint)
        canvas.drawLine(centerX - radius - tickLength * 0.25f, centerY, centerX - radius + tickLength, centerY, tickPaint)
        canvas.drawLine(centerX + radius - tickLength, centerY, centerX + radius + tickLength * 0.25f, centerY, tickPaint)
    }
}
