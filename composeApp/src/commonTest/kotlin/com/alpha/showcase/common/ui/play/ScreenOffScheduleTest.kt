package com.alpha.showcase.common.ui.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenOffScheduleTest {

    @Test
    fun windowInsideTheSameDayCoversOnlyItsOwnHours() {
        val start = 9 * 60
        val end = 17 * 60
        assertFalse(isScreenOffActive(8 * 60 + 59, start, end))
        assertTrue(isScreenOffActive(9 * 60, start, end))
        assertTrue(isScreenOffActive(12 * 60, start, end))
        assertTrue(isScreenOffActive(16 * 60 + 59, start, end))
        assertFalse(isScreenOffActive(17 * 60, start, end))
    }

    @Test
    fun windowCrossingMidnightStaysActiveOnBothSides() {
        val start = 23 * 60
        val end = 7 * 60
        assertTrue(isScreenOffActive(23 * 60, start, end))
        assertTrue(isScreenOffActive(23 * 60 + 59, start, end))
        assertTrue(isScreenOffActive(0, start, end))
        assertTrue(isScreenOffActive(6 * 60 + 59, start, end))
        assertFalse(isScreenOffActive(7 * 60, start, end))
        assertFalse(isScreenOffActive(12 * 60, start, end))
    }

    @Test
    fun emptyWindowIsNeverActive() {
        assertFalse(isScreenOffActive(0, 480, 480))
        assertFalse(isScreenOffActive(480, 480, 480))
        assertFalse(isScreenOffActive(1439, 480, 480))
    }
}

class PhotoOrientationTest {

    @Test
    fun uprightPhotosReportTheirOwnOrientation() {
        assertEquals(DeviceOrientation.Portrait, photoOrientation(1080, 1920))
        assertEquals(DeviceOrientation.Landscape, photoOrientation(1920, 1080))
    }

    @Test
    fun squareAndUnknownSizesAreNeverFiltered() {
        assertEquals(null, photoOrientation(1000, 1000))
        assertEquals(null, photoOrientation(0, 1000))
        assertEquals(null, photoOrientation(1000, 0))
    }
}
