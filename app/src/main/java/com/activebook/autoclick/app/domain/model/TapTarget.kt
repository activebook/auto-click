package com.activebook.autoclick.app.domain.model

import com.activebook.autoclick.app.domain.geometry.GridGeometry
import com.activebook.autoclick.app.domain.geometry.RelativePoint
import com.activebook.autoclick.app.domain.geometry.ScreenPoint
import com.activebook.autoclick.app.domain.geometry.ScreenRect

/**
 * The resolved answer to "where does the tap go?", frozen for the lifetime of a session.
 *
 * The coordinator snapshots this at Start and re-resolves it against the live usable area on
 * every tick, so a rotation or a keyboard that moves the bars moves the tap with them — exactly
 * how cells already behave.
 */
data class TapTarget(
    val mode: TargetMode,
    val cellIndex: Int,
    val point: RelativePoint,
) {
    fun resolveIn(area: ScreenRect): ScreenPoint = when (mode) {
        TargetMode.CELL -> GridGeometry.cellCenter(cellIndex, area)
        TargetMode.POINT -> point.resolveIn(area)
    }

    companion object {
        /** What a fresh install taps: the middle of the middle cell. */
        val CenterCell = TapTarget(
            mode = TargetMode.CELL,
            cellIndex = GridGeometry.CENTER_CELL_INDEX,
            point = RelativePoint.Center,
        )

        fun cell(cellIndex: Int): TapTarget = TapTarget(
            mode = TargetMode.CELL,
            cellIndex = cellIndex.coerceIn(0, GridGeometry.CELL_COUNT - 1),
            point = RelativePoint.Center,
        )

        fun point(cellIndex: Int, point: RelativePoint): TapTarget = TapTarget(
            mode = TargetMode.POINT,
            cellIndex = cellIndex.coerceIn(0, GridGeometry.CELL_COUNT - 1),
            point = point,
        )
    }
}
