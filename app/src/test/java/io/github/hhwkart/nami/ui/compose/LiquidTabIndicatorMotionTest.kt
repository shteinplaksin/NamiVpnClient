package io.github.hhwkart.nami.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidTabIndicatorMotionTest {

    @Test
    fun stationaryIndicatorSettlesIntoCircle() {
        val motion = liquidTabIndicatorMotion(
            rawPosition = 2f,
            velocity = 0f,
            lastIndex = 4,
            isLtr = true,
            reduceMotion = false,
        )

        assertEquals(2f, motion.position)
        assertEquals(0f, motion.stretch)
        assertEquals(0f, motion.impact)
    }

    @Test
    fun fasterTravelDeformsIndicatorMore() {
        val slow = liquidTabIndicatorMotion(2f, 2f, 4, isLtr = true, reduceMotion = false)
        val fast = liquidTabIndicatorMotion(2f, 10f, 4, isLtr = true, reduceMotion = false)

        assertTrue(fast.stretch > slow.stretch)
        assertEquals(0f, fast.impact)
    }

    @Test
    fun springOvershootCompressesAgainstPhysicalEdge() {
        val motion = liquidTabIndicatorMotion(4.2f, 8f, 4, isLtr = true, reduceMotion = false)
        val rtlMotion = liquidTabIndicatorMotion(4.2f, 8f, 4, isLtr = false, reduceMotion = false)

        assertEquals(4f, motion.position)
        assertTrue(motion.impact > 0f)
        assertEquals(1f, motion.impactSide)
        assertEquals(-1f, rtlMotion.impactSide)
    }

    @Test
    fun reduceMotionSuppressesStretchAndImpact() {
        val motion = liquidTabIndicatorMotion(4.2f, 12f, 4, isLtr = true, reduceMotion = true)

        assertEquals(4f, motion.position)
        assertEquals(0f, motion.stretch)
        assertEquals(0f, motion.impact)
    }
}
