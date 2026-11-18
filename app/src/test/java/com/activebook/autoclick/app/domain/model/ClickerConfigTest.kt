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
        assertEquals(ClickerConfig.MIN_INTERVAL_MS, ClickerConfig.of(0, 0L, true, true).intervalMs)
        assertEquals(ClickerConfig.MIN_INTERVAL_MS, ClickerConfig.of(0, -12_000L, true, true).intervalMs)
        assertEquals(ClickerConfig.MAX_INTERVAL_MS, ClickerConfig.of(0, 999_999_999L, true, true).intervalMs)
    }

    @Test
    fun `cell index is clamped to the grid`() {
        assertEquals(0, ClickerConfig.of(-3, 1000L, true, true).cellIndex)
        assertEquals(8, ClickerConfig.of(12, 1000L, true, true).cellIndex)
    }

    @Test
    fun `seconds factory converts without float math`() {
        val config = ClickerConfig.ofSeconds(2, 45, true, false)
        assertEquals(45_000L, config.intervalMs)
        assertEquals(45, config.intervalSeconds)
        assertTrue(config.hapticOnTap)
        assertFalse(config.flashOnTap)
    }

    @Test
    fun `center flag follows the index`() {
        assertTrue(ClickerConfig.Default.isCenterCell)
        assertFalse(ClickerConfig.of(0, 1000L, true, true).isCenterCell)
    }
}
