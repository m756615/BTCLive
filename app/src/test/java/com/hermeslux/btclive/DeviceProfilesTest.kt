package com.hermeslux.btclive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfilesTest {
    @Test
    fun clockSideFollowsTheBrand() {
        assertTrue(prefersRightStatusClock("Samsung"))
        assertTrue(prefersRightStatusClock("Xiaomi", "Redmi"))
        assertTrue(prefersRightStatusClock("HUAWEI"))
        assertFalse(prefersRightStatusClock("Google"))
        assertFalse(prefersRightStatusClock("OnePlus"))
        assertFalse(prefersRightStatusClock("motorola"))
    }

    @Test
    fun xiaomiGetsAutostartStepsAndPixelDoesNot() {
        val xiaomi = oemGuidance("Xiaomi")
        assertEquals("Xiaomi", xiaomi.brand)
        assertTrue(xiaomi.autostartTargets.isNotEmpty())
        assertTrue(xiaomi.batterySteps.contains("Battery saver"))

        val pixel = oemGuidance("Google")
        assertTrue(pixel.autostartTargets.isEmpty())
        assertTrue(pixel.batterySteps.contains("Unrestricted"))
    }

    @Test
    fun hintNamesTheBrand() {
        assertTrue(clockSideHint("samsung").contains("right"))
        assertTrue(clockSideHint("Google").contains("left"))
        assertFalse(clockSideHint("Google").contains("this phone phones"))
    }
}
