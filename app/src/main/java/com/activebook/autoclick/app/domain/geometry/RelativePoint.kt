package com.activebook.autoclick.app.domain.geometry

/**
 * A point expressed as a fraction of a rectangle, with `(0, 0)` at its top-left and `(1, 1)` at
 * its bottom-right.
 *
 * Precise targets are stored this way instead of as absolute pixels. A cell index is
 * rotation-proof because it is derived; a pixel is not — `1080 × 2408` turned sideways would put
 * the stored `(900, 2100)` outside the screen or on a different control. A fraction keeps the
 * *relative* intent ("a bit under the middle") across rotation, resolution and system-bar changes,
 * and is re-resolved into the current pixels on every tick by the same code path cells use.
 */
data class RelativePoint(val x: Float, val y: Float) {

    /** Converts to screen pixels inside [area]. Values are clamped again here, so a hand-built
     * instance can never aim outside the screen. */
    fun resolveIn(area: ScreenRect): ScreenPoint = ScreenPoint(
        x = area.left + clamp(x) * area.width,
        y = area.top + clamp(y) * area.height,
    )

    companion object {
        /** The visual middle — the same place the centre cell's glyph points at. */
        val Center = RelativePoint(0.5f, 0.5f)

        fun of(x: Float, y: Float): RelativePoint = RelativePoint(clamp(x), clamp(y))

        /** Fraction of a screen point. Points outside [area] are clamped onto its edge. */
        fun ofPoint(point: ScreenPoint, area: ScreenRect): RelativePoint {
            if (area.isEmpty) return Center
            return of(
                x = (point.x - area.left) / area.width,
                y = (point.y - area.top) / area.height,
            )
        }

        /** NaN and infinities would poison every later resolution, so they fall back to the middle. */
        private fun clamp(value: Float): Float =
            if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f
    }
}
