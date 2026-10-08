package com.hermeslux.btclive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPlacementTest {
    private val price = "$100,000"

    private fun phone(
        width: Int = 1000,
        height: Int = 2000,
        status: Int = 48,
        clockOnRight: Boolean = false,
        cutoutLeft: Int = 0,
        cutoutRight: Int = 0,
        cutoutTop: Int = 0,
        cutoutBottom: Int = 0,
    ) = ScreenProfile(
        widthPx = width,
        heightPx = height,
        density = 1f,
        statusBarHeightPx = status,
        cutoutLeftPx = cutoutLeft,
        cutoutRightPx = cutoutRight,
        cutoutTopPx = cutoutTop,
        cutoutBottomPx = cutoutBottom,
        clockOnRight = clockOnRight,
    )

    @Test
    fun leftClockSitsJustAfterTheClock() {
        val layout = OverlayPlacement.resolve(phone(), PlacementMode.AUTO, priceText = price)
        assertFalse(layout.chip)
        assertEquals(13f, layout.textSizeSp)
        assertEquals(82, layout.xPx)
        assertEquals(15, layout.yPx)
        assertTrue(layout.yPx + OverlayPlacement.estimateTextHeightPx(layout.textSizeSp, 1f) <= 48)
    }

    @Test
    fun rightClockSitsClearOfTheClockAndIcons() {
        val layout = OverlayPlacement.resolve(phone(clockOnRight = true), PlacementMode.AUTO, priceText = price)
        val width = OverlayPlacement.estimateTextWidthPx(price, layout.textSizeSp, 1f)
        assertFalse(layout.chip)
        assertEquals(766, layout.xPx)
        assertTrue(layout.xPx + width < 1000 - 72)
    }

    @Test
    fun cutoutPushesThePricePastTheCameraHole() {
        val screen = phone(cutoutLeft = 70, cutoutRight = 200, cutoutTop = 0, cutoutBottom = 40)
        val layout = OverlayPlacement.resolve(screen, PlacementMode.LEFT, priceText = price)
        val width = OverlayPlacement.estimateTextWidthPx(price, layout.textSizeSp, 1f)
        assertFalse(layout.chip)
        assertTrue(layout.xPx >= 200)
        assertFalse(layout.xPx < 200 && layout.xPx + width > 70)
    }

    @Test
    fun narrowStatusBarFallsBackToAChip() {
        val layout = OverlayPlacement.resolve(phone(width = 200, status = 24), PlacementMode.AUTO, priceText = price)
        assertTrue(layout.chip)
        assertTrue(layout.yPx >= 24)
        assertTrue(layout.xPx >= 0)
        assertTrue(layout.xPx + layout.slotWidthPx <= 200)
    }

    @Test
    fun belowModeIsCenteredUnderTheStatusBar() {
        val layout = OverlayPlacement.resolve(phone(), PlacementMode.BELOW, priceText = price)
        assertTrue(layout.chip)
        assertEquals(56, layout.yPx)
        assertTrue(layout.xPx > 200)
        assertTrue(layout.xPx < 800)
    }

    @Test
    fun customFractionStaysOnScreen() {
        val layout = OverlayPlacement.resolve(
            phone(),
            PlacementMode.CUSTOM,
            customXFraction = 0.99f,
            customYFraction = 0.5f,
            priceText = price,
        )
        assertTrue(layout.chip)
        assertTrue(layout.xPx + layout.slotWidthPx <= 1000)
        assertEquals(1000, layout.yPx)
    }

    @Test
    fun missingCustomFractionsUseAuto() {
        val layout = OverlayPlacement.resolve(phone(), PlacementMode.CUSTOM, priceText = price)
        val auto = OverlayPlacement.resolve(phone(), PlacementMode.AUTO, priceText = price)
        assertEquals(auto, layout)
    }
}
