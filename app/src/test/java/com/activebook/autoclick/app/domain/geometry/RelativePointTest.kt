package com.activebook.autoclick.app.domain.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativePointTest {

    private val phone = ScreenRect(left = 0f, top = 90f, right = 1080f, bottom = 2280f)
    private val phoneWidth = 1080f
    private val phoneHeight = 2190f

    @Test
    fun `resolving the middle lands in the middle of the usable area`() {
        val point = RelativePoint.Center.resolveIn(phone)

        assertEquals(540f, point.x, 0.001f)
        assertEquals(90f + phoneHeight / 2f, point.y, 0.001f)
    }

    @Test
    fun `resolving honours the area origin, not the screen origin`() {
        // The status bar is 90 px tall: a fraction of 0 must sit below it, not at y = 0.
        val point = RelativePoint.of(0f, 0f).resolveIn(phone)

        assertEquals(0f, point.x, 0.001f)
        assertEquals(90f, point.y, 0.001f)
    }

    @Test
    fun `a round trip through pixels loses less than a pixel`() {
        val original = ScreenPoint(271f, 1377f)
        val fraction = RelativePoint.ofPoint(original, phone)
        val back = fraction.resolveIn(phone)

        assertEquals(original.x, back.x, 0.5f)
        assertEquals(original.y, back.y, 0.5f)
    }

    @Test
    fun `points outside the area clamp onto its edge instead of aiming off-screen`() {
        val fraction = RelativePoint.ofPoint(ScreenPoint(-400f, 99_999f), phone)

        assertEquals(0f, fraction.x, 0.0f)
        assertEquals(1f, fraction.y, 0.0f)
        val resolved = fraction.resolveIn(phone)
        assertEquals(0f, resolved.x, 0.001f)
        assertEquals(2280f, resolved.y, 0.001f)
    }

    @Test
    fun `a fraction is clamped when it is constructed, not when it is used`() {
        assertEquals(0f, RelativePoint.of(-3f, 0.5f).x, 0.0f)
        assertEquals(1f, RelativePoint.of(0.5f, 4f).y, 0.0f)
    }

    @Test
    fun `NaN falls back to the middle instead of poisoning every later resolution`() {
        val fraction = RelativePoint.of(Float.NaN, Float.NaN)

        assertEquals(0.5f, fraction.x, 0.0f)
        assertEquals(0.5f, fraction.y, 0.0f)
        val resolved = fraction.resolveIn(phone)
        assertEquals(540f, resolved.x, 0.001f)
    }

    @Test
    fun `an empty area resolves to a finite point rather than dividing by zero`() {
        val fraction = RelativePoint.ofPoint(ScreenPoint(10f, 20f), ScreenRect(0f, 0f, 0f, 0f))

        assertEquals(RelativePoint.Center, fraction)
    }
}
