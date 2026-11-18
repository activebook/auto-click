package com.activebook.autoclick.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickerConfigTest {

    @Test
    fun `default is center cell at one second`() {
        assertEquals(4, ClickerConfig.Default.cellIndex)
        assertEquals(1000L, ClickerConfig.Default.intervalMs)
        assertEquals(1, ClickerConfig.Default.intervalSeconds)
    }

    @Test
    fun `interval is clamped to the allowed range`() {
        assertEquals(ClickerConfig.MIN_INTERVAL_MS, ClickerConfig.of(0, 0L, 0, true, true).intervalMs)
        assertEquals(ClickerConfig.MIN_INTERVAL_MS, ClickerConfig.of(0, -12_000L, 0, true, true).intervalMs)
        assertEquals(ClickerConfig.MAX_INTERVAL_MS, ClickerConfig.of(0, 999_999_999L, 0, true, true).intervalMs)
    }

    @Test
    fun `cell index is clamped to the grid`() {
        assertEquals(0, ClickerConfig.of(-3, 1000L, 0, true, true).cellIndex)
        assertEquals(8, ClickerConfig.of(12, 1000L, 0, true, true).cellIndex)
    }

    @Test
    fun `seconds factory converts without float math`() {
        val config = ClickerConfig.ofSeconds(2, 45, 0, true, false)
        assertEquals(45_000L, config.intervalMs)
        assertEquals(45, config.intervalSeconds)
        assertTrue(config.hapticOnTap)
        assertFalse(config.flashOnTap)
    }

    @Test
    fun `center flag follows the index`() {
        assertTrue(ClickerConfig.Default.isCenterCell)
        assertFalse(ClickerConfig.of(0, 1000L, 0, true, true).isCenterCell)
    }

    @Test
    fun `tap budget defaults to none and is clamped`() {
        assertEquals(ClickerConfig.NO_TAP_BUDGET, ClickerConfig.Default.stopAfterTaps)
        assertEquals(ClickerConfig.NO_TAP_BUDGET, ClickerConfig.of(0, 1000L, -5, true, true).stopAfterTaps)
        assertEquals(
            ClickerConfig.MAX_STOP_AFTER_TAPS,
            ClickerConfig.of(0, 1000L, 5_000_000, true, true).stopAfterTaps,
        )
        assertEquals(500, ClickerConfig.ofSeconds(4, 1, 500, true, true).stopAfterTaps)
    }

    @Test
    fun `copy keeps the budget unless it is changed`() {
        val config = ClickerConfig.ofSeconds(4, 1, 300, true, true)
        assertEquals(300, config.copy(cellIndex = 0).stopAfterTaps)
    }
}
