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
