package com.activebook.autoclick.app.domain.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridGeometryTest {

    private val area = ScreenRect(left = 0f, top = 100f, right = 1080f, bottom = 2000f)

    @Test
    fun `cell rects tile the area without gaps or overlap`() {
        var coveredArea = 0f
        val rects = (0 until GridGeometry.CELL_COUNT).map { GridGeometry.cellRect(it, area) }

        rects.forEach { coveredArea += it.width * it.height }
        assertEquals(area.width * area.height, coveredArea, 0.5f)

        rects.forEachIndexed { firstIndex, first ->
            rects.forEachIndexed { secondIndex, second ->
                if (firstIndex == secondIndex) return@forEachIndexed
                val overlapWidth = minOf(first.right, second.right) - maxOf(first.left, second.left)
                val overlapHeight = minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)
                assertFalse(
                    "cells $firstIndex and $secondIndex overlap",
                    overlapWidth > 0.01f && overlapHeight > 0.01f,
                )
            }
        }
    }

    @Test
    fun `center cell is the center of the area`() {
        val center = GridGeometry.cellCenter(GridGeometry.CENTER_CELL_INDEX, area)
        assertEquals(area.left + area.width / 2f, center.x, 0.01f)
        assertEquals(area.top + area.height / 2f, center.y, 0.01f)
    }

    @Test
    fun `top left cell center sits one sixth in`() {
        val center = GridGeometry.cellCenter(0, area)
        assertEquals(180f, center.x, 0.01f)
        assertEquals(100f + 1900f / 6f, center.y, 0.01f)
    }

    @Test
    fun `bottom right cell center sits five sixths in`() {
        val center = GridGeometry.cellCenter(8, area)
        assertEquals(900f, center.x, 0.01f)
        assertEquals(100f + 1900f * 5f / 6f, center.y, 0.01f)
    }

    @Test
    fun `every cell center maps back to the same cell index`() {
        for (index in 0 until GridGeometry.CELL_COUNT) {
            val center = GridGeometry.cellCenter(index, area)
            assertEquals(index, GridGeometry.cellIndexAt(center, area))
        }
    }

    @Test
    fun `points outside the area clamp to the nearest edge cell`() {
        assertEquals(0, GridGeometry.cellIndexAt(ScreenPoint(-500f, -500f), area))
        assertEquals(8, GridGeometry.cellIndexAt(ScreenPoint(5000f, 5000f), area))
    }

    @Test
    fun `row and column are row major`() {
        assertEquals(1, GridGeometry.rowOf(3))
        assertEquals(0, GridGeometry.columnOf(3))
        assertEquals(2, GridGeometry.rowOf(7))
        assertEquals(1, GridGeometry.columnOf(7))
    }

    @Test
    fun `cell names are one based and mark the center`() {
        assertEquals("Row 1, column 1", GridGeometry.cellName(0))
        assertEquals("Row 2, column 2 (center)", GridGeometry.cellName(4))
        assertEquals("Row 3, column 3", GridGeometry.cellName(8))
    }

    @Test
    fun `empty area produces zero sized cells instead of NaN`() {
        val empty = ScreenRect(0f, 0f, 0f, 0f)
        assertTrue(empty.isEmpty)
        val center = GridGeometry.cellCenter(4, empty)
        assertEquals(0f, center.x, 0.0f)
        assertEquals(0f, center.y, 0.0f)
    }

    @Test
    fun `ofSize subtracts insets`() {
        val rect = ScreenRect.ofSize(
            width = 1080,
            height = 2400,
            insetLeft = 0,
            insetTop = 96,
            insetRight = 0,
            insetBottom = 132,
        )
        assertEquals(0f, rect.left, 0.0f)
        assertEquals(96f, rect.top, 0.0f)
        assertEquals(1080f, rect.right, 0.0f)
        assertEquals(2268f, rect.bottom, 0.0f)
        assertEquals(1080f, rect.width, 0.0f)
        assertEquals(2172f, rect.height, 0.0f)
    }
}
