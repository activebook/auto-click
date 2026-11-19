package com.activebook.autoclick.app.domain.model

import com.activebook.autoclick.app.domain.geometry.RelativePoint
import com.activebook.autoclick.app.domain.geometry.ScreenRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapTargetTest {

    private val area = ScreenRect(0f, 120f, 1080f, 2280f)

    @Test
    fun `a cell target resolves to that cell's centre, exactly as before`() {
        val target = ClickerConfig.Default.target

        assertEquals(TargetMode.CELL, target.mode)
        // Middle column of a 1080-wide area, middle row of a 2160-tall one.
        val point = target.resolveIn(area)
        assertEquals(540f, point.x, 0.001f)
        assertEquals(120f + 2160f / 2f, point.y, 0.001f)
    }

    @Test
    fun `a point target resolves to the pixels the fraction describes`() {
        val config = ClickerConfig.of(
            cellIndex = 4,
            intervalMs = 1000L,
            stopAfterTaps = 0,
            hapticOnTap = true,
            flashOnTap = true,
            targetMode = TargetMode.POINT,
            point = RelativePoint.of(0.25f, 0.75f),
        )

        val point = config.target.resolveIn(area)
        assertEquals(270f, point.x, 0.001f)
        assertEquals(120f + 2160f * 0.75f, point.y, 0.001f)
        assertTrue(config.isExactPoint)
    }

    @Test
    fun `switching to a point keeps the last cell, so going back is one tap`() {
        val target = ClickerConfig.of(
            cellIndex = 7,
            intervalMs = 1000L,
            stopAfterTaps = 0,
            hapticOnTap = true,
            flashOnTap = true,
            targetMode = TargetMode.POINT,
            point = RelativePoint.of(0.1f, 0.9f),
        ).target

        assertEquals(7, target.cellIndex)
        assertEquals(TargetMode.POINT, target.mode)
    }

    @Test
    fun `defaults are a coarse centre cell — an upgrade changes nothing for existing users`() {
        assertEquals(TargetMode.CELL, ClickerConfig.Default.targetMode)
        assertEquals(RelativePoint.Center, ClickerConfig.Default.point)
        assertFalse(ClickerConfig.Default.isExactPoint)
        assertEquals(TapTarget.CenterCell, ClickerConfig.Default.target)
    }

    @Test
    fun `of clamps a stored fraction instead of trusting it`() {
        val config = ClickerConfig.of(
            cellIndex = 4,
            intervalMs = 1000L,
            stopAfterTaps = 0,
            hapticOnTap = true,
            flashOnTap = true,
            targetMode = TargetMode.POINT,
            point = RelativePoint(4f, -2f),
        )

        assertEquals(1f, config.point.x, 0.0f)
        assertEquals(0f, config.point.y, 0.0f)
    }

    @Test
    fun `a cell out of range is clamped, not thrown`() {
        val target = ClickerConfig.of(
            cellIndex = 99,
            intervalMs = 1000L,
            stopAfterTaps = 0,
            hapticOnTap = true,
            flashOnTap = true,
        ).target

        assertEquals(8, target.cellIndex)
        assertEquals(TargetMode.CELL, target.mode)
    }
}
