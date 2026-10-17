package com.alpha.showcase.common.ui.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrientationPlaybackGateTest {

    @Test
    fun `plays the next photo when it agrees with the device`() {
        val probe = firstPlayablePage(from = 0, itemCount = 10, lookahead = 3) { it == 1 }
        assertEquals(1, probe.page)
        assertEquals(0, probe.rejections)
    }

    @Test
    fun `steps over every disagreeing photo inside the lookahead`() {
        val probe = firstPlayablePage(from = 0, itemCount = 10, lookahead = 3) { it == 3 }
        assertEquals(3, probe.page)
        assertEquals(2, probe.rejections)
    }

    @Test
    fun `stops on an undecided photo instead of guessing`() {
        val probe = firstPlayablePage(from = 0, itemCount = 10, lookahead = 3) { page ->
            when (page) {
                1 -> false
                else -> null
            }
        }
        assertNull(probe.page)
        assertEquals(1, probe.rejections)
    }

    @Test
    fun `wraps from the last photo back to the first`() {
        val probe = firstPlayablePage(from = 9, itemCount = 10, lookahead = 3) { page -> true }
        assertEquals(0, probe.page)
        assertEquals(0, probe.rejections)
    }

    @Test
    fun `gives up when every photo in range disagrees`() {
        val probe = firstPlayablePage(from = 0, itemCount = 10, lookahead = 3) { false }
        assertNull(probe.page)
        assertEquals(3, probe.rejections)
    }

    @Test
    fun `never probes past a single photo set`() {
        val probe = firstPlayablePage(from = 0, itemCount = 1, lookahead = 3) { true }
        assertNull(probe.page)
    }

    @Test
    fun `cycle advances and wraps`() {
        assertEquals(1, nextPageInCycle(0, 4))
        assertEquals(0, nextPageInCycle(3, 4))
        assertEquals(0, nextPageInCycle(0, 0))
    }
}
