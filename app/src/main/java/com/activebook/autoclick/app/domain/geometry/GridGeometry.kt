package com.activebook.autoclick.app.domain.geometry

/**
 * A rectangle in screen pixels. Deliberately independent of android.graphics so that all grid
 * math stays unit-testable on the JVM.
 *
 * [right] and [bottom] are exclusive, matching [android.graphics.Rect] semantics.
 */
data class ScreenRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val center: ScreenPoint get() = ScreenPoint(left + width / 2f, top + height / 2f)
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    fun contains(point: ScreenPoint): Boolean =
        point.x >= left && point.x <= right && point.y >= top && point.y <= bottom

    companion object {
        /** Builds a rect from size + inset edges, the shape most Android APIs hand us. */
        fun ofSize(
            width: Int,
            height: Int,
            insetLeft: Int = 0,
            insetTop: Int = 0,
            insetRight: Int = 0,
            insetBottom: Int = 0,
        ): ScreenRect = ScreenRect(
            left = insetLeft.toFloat(),
            top = insetTop.toFloat(),
            right = (width - insetRight).toFloat(),
            bottom = (height - insetBottom).toFloat(),
        )
    }
}

/** A point in screen pixels. */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * Splits a rectangular screen area into a 3x3 grid and converts between cell indices and pixels.
 *
 * Index convention (row-major, the same order the settings UI renders):
 *
 * ```
 *  0 | 1 | 2
 *  3 | 4 | 5
 *  6 | 7 | 8
 * ```
 */
object GridGeometry {

    const val COLUMNS = 3
    const val ROWS = 3
    const val CELL_COUNT = COLUMNS * ROWS

    /** Index of the visually centered cell; used as the default target. */
    const val CENTER_CELL_INDEX = 4

    fun isValidIndex(cellIndex: Int): Boolean = cellIndex in 0 until CELL_COUNT

    fun columnOf(cellIndex: Int): Int = cellIndex % COLUMNS

    fun rowOf(cellIndex: Int): Int = cellIndex / COLUMNS

    /**
     * Bounds of the requested cell inside [area]. Cells tile the area exactly: the nine rects
     * cover it without gaps or overlap, so a tap can never land in an unassigned region.
     */
    fun cellRect(cellIndex: Int, area: ScreenRect): ScreenRect {
        require(isValidIndex(cellIndex)) { "cellIndex out of range: $cellIndex" }
        val cellWidth = area.width / COLUMNS
        val cellHeight = area.height / ROWS
        val column = columnOf(cellIndex)
        val row = rowOf(cellIndex)
        return ScreenRect(
            left = area.left + column * cellWidth,
            top = area.top + row * cellHeight,
            right = area.left + (column + 1) * cellWidth,
            bottom = area.top + (row + 1) * cellHeight,
        )
    }

    /** Center point of the requested cell — the exact coordinate AutoClick taps. */
    fun cellCenter(cellIndex: Int, area: ScreenRect): ScreenPoint {
        require(isValidIndex(cellIndex)) { "cellIndex out of range: $cellIndex" }
        val cellWidth = area.width / COLUMNS
        val cellHeight = area.height / ROWS
        val column = columnOf(cellIndex)
        val row = rowOf(cellIndex)
        return ScreenPoint(
            x = area.left + (column + 0.5f) * cellWidth,
            y = area.top + (row + 0.5f) * cellHeight,
        )
    }

    /**
     * Which cell a screen point falls into. Used by the on-screen picker, where the user taps
     * the real screen instead of a preview. Out-of-area taps are clamped to the nearest edge.
     */
    fun cellIndexAt(point: ScreenPoint, area: ScreenRect): Int {
        val column = columnAt(point.x, area.left, area.width)
        val row = columnAt(point.y, area.top, area.height)
        return row * COLUMNS + column
    }

    private fun columnAt(value: Float, start: Float, size: Float): Int {
        if (size <= 0f) return 0
        val ratio = (value - start) / size * COLUMNS
        return ratio.toInt().coerceIn(0, COLUMNS - 1)
    }

    /** Human readable name, used in the status line and accessibility hints. */
    fun cellName(cellIndex: Int): String {
        require(isValidIndex(cellIndex)) { "cellIndex out of range: $cellIndex" }
        val row = rowOf(cellIndex) + 1
        val column = columnOf(cellIndex) + 1
        return if (cellIndex == CENTER_CELL_INDEX) {
            "Row $row, column $column (center)"
        } else {
            "Row $row, column $column"
        }
    }
}
