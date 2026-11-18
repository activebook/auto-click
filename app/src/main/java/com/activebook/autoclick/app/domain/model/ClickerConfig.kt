package com.activebook.autoclick.app.domain.model

import com.activebook.autoclick.app.domain.geometry.GridGeometry

/**
 * Everything the user can configure. Immutable snapshot: a running session keeps its own copy,
 * so mid-run edits never mutate the schedule underneath the ticker.
 *
 * Instances are always created through [of], which clamps every field into its valid range.
 * [com.activebook.autoclick.app.data.ClickerConfigRepository] funnels stored values through the
 * same factory, so a corrupted preference file can never produce a zero interval.
 */
data class ClickerConfig(
    val cellIndex: Int,
    val intervalMs: Long,
    val hapticOnTap: Boolean,
    val flashOnTap: Boolean,
) {
    val intervalSeconds: Int get() = (intervalMs / MS_PER_SECOND).toInt()

    val isCenterCell: Boolean get() = cellIndex == GridGeometry.CENTER_CELL_INDEX

    companion object {
        const val MS_PER_SECOND = 1000L

        const val MIN_INTERVAL_SECONDS = 1
        const val MAX_INTERVAL_SECONDS = 3600
        const val DEFAULT_INTERVAL_SECONDS = 1

        const val MIN_INTERVAL_MS = MIN_INTERVAL_SECONDS * MS_PER_SECOND
        const val MAX_INTERVAL_MS = MAX_INTERVAL_SECONDS * MS_PER_SECOND
        const val DEFAULT_INTERVAL_MS = DEFAULT_INTERVAL_SECONDS * MS_PER_SECOND

        const val DEFAULT_CELL_INDEX = GridGeometry.CENTER_CELL_INDEX

        val Default = ClickerConfig(
            cellIndex = DEFAULT_CELL_INDEX,
            intervalMs = DEFAULT_INTERVAL_MS,
            hapticOnTap = true,
            flashOnTap = true,
        )

        /** Clamps every field, so no caller can create a config the engine cannot honour. */
        fun of(
            cellIndex: Int,
            intervalMs: Long,
            hapticOnTap: Boolean,
            flashOnTap: Boolean,
        ): ClickerConfig = ClickerConfig(
            cellIndex = cellIndex.coerceIn(0, GridGeometry.CELL_COUNT - 1),
            intervalMs = intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS),
            hapticOnTap = hapticOnTap,
            flashOnTap = flashOnTap,
        )

        fun ofSeconds(
            cellIndex: Int,
            intervalSeconds: Int,
            hapticOnTap: Boolean,
            flashOnTap: Boolean,
        ): ClickerConfig = of(
            cellIndex = cellIndex,
            intervalMs = intervalSeconds.toLong() * MS_PER_SECOND,
            hapticOnTap = hapticOnTap,
            flashOnTap = flashOnTap,
        )
    }
}
