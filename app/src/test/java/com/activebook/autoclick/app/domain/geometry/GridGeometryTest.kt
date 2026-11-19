package com.activebook.autoclick.app.domain.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridGeometryTest {

    private val area = ScreenRect(left = 0f, top = 100f, right = 1080f, bottom = 2000f)

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
}
