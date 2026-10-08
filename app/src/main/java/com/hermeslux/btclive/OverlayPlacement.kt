package com.hermeslux.btclive

import kotlin.math.roundToInt

enum class PlacementMode {
    AUTO,
    LEFT,
    RIGHT,
    BELOW,
    CUSTOM,
}

/**
 * Screen geometry in pixels. Cutout edges describe the union of cutouts that
 * intersect the status bar; leave them at 0 when there is no cutout.
 */
data class ScreenProfile(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val statusBarHeightPx: Int,
    val cutoutLeftPx: Int = 0,
    val cutoutRightPx: Int = 0,
    val cutoutTopPx: Int = 0,
    val cutoutBottomPx: Int = 0,
    val rtl: Boolean = false,
    val landscape: Boolean = false,
    val clockOnRight: Boolean = false,
)

data class OverlayLayout(
    val xPx: Int,
    val yPx: Int,
    val textSizeSp: Float,
    val chip: Boolean,
    val slotWidthPx: Int,
)

/**
 * Picks a spot for the live price that stays clear of the clock, system icons,
 * and camera cutout. Coordinates are physical screen pixels from the top-left.
 * When the status bar is too crowded, the result is a chip just below it.
 */
object OverlayPlacement {
    private const val EDGE_DP = 6f
    private const val GAP_DP = 4f
    private const val CLOCK_DP = 72f
    private const val ICONS_DP = 88f
    private const val CHIP_PAD_H_DP = 10f
    private const val BELOW_GAP_DP = 8f
    private const val MIN_STATUS_TEXT_SP = 9f
    private const val MAX_STATUS_TEXT_SP = 13f
    private const val CHIP_TEXT_SP = 15f

    fun placementFrom(name: String?): PlacementMode {
        return runCatching { PlacementMode.valueOf(name ?: "") }.getOrDefault(PlacementMode.AUTO)
    }

    fun estimateTextWidthPx(text: String, textSizeSp: Float, density: Float): Int {
        val px = textSizeSp * density
        return (text.length.coerceAtLeast(1) * 0.62f * px).roundToInt().coerceAtLeast(1)
    }

    fun estimateTextHeightPx(textSizeSp: Float, density: Float): Int {
        return (textSizeSp * density * 1.3f).roundToInt().coerceAtLeast(1)
    }

    fun resolve(
        screen: ScreenProfile,
        mode: PlacementMode,
        customXFraction: Float? = null,
        customYFraction: Float? = null,
        priceText: String = "$00,000",
    ): OverlayLayout {
        if (screen.widthPx <= 0 || screen.heightPx <= 0 || screen.density <= 0f) {
            return OverlayLayout(0, 0, CHIP_TEXT_SP, chip = true, slotWidthPx = 1)
        }
        if (mode == PlacementMode.BELOW) {
            return below(screen, priceText)
        }
        if (mode == PlacementMode.CUSTOM &&
            customXFraction != null && customXFraction.isFinite() &&
            customYFraction != null && customYFraction.isFinite()
        ) {
            return custom(screen, customXFraction, customYFraction, priceText)
        }

        val preferRight = when (mode) {
            PlacementMode.RIGHT -> true
            PlacementMode.LEFT -> false
            else -> screen.clockOnRight
        }
        val sides = when (mode) {
            PlacementMode.LEFT -> listOf(false)
            PlacementMode.RIGHT -> listOf(true)
            else -> if (preferRight) listOf(true, false) else listOf(false, true)
        }
        if (screen.statusBarHeightPx >= screen.px(18f)) {
            val placed = placeInStatusBar(screen, sides, priceText)
            if (placed != null) return placed
        }
        return below(screen, priceText)
    }

    private fun placeInStatusBar(
        screen: ScreenProfile,
        preferRightSides: List<Boolean>,
        priceText: String,
    ): OverlayLayout? {
        val statusDp = screen.statusBarHeightPx / screen.density
        var sp = (statusDp * 0.42f).coerceIn(MIN_STATUS_TEXT_SP, MAX_STATUS_TEXT_SP)
        while (sp >= MIN_STATUS_TEXT_SP - 0.01f) {
            val textWidth = estimateTextWidthPx(priceText, sp, screen.density)
            val textHeight = estimateTextHeightPx(sp, screen.density)
            if (textHeight <= screen.statusBarHeightPx) {
                val y = ((screen.statusBarHeightPx - textHeight) / 2).coerceAtLeast(0)
                val freeStart = reservedLeft(screen) + screen.px(GAP_DP)
                val freeEnd = screen.widthPx - reservedRight(screen) - screen.px(GAP_DP)
                for (right in preferRightSides) {
                    val x = if (right) freeEnd - textWidth else freeStart
                    val nudged = nudge(x, textWidth, screen, freeStart, freeEnd)
                    if (nudged != null) {
                        return OverlayLayout(
                            xPx = nudged,
                            yPx = y,
                            textSizeSp = sp,
                            chip = false,
                            slotWidthPx = textWidth,
                        )
                    }
                }
            }
            sp -= 0.5f
        }
        return null
    }

    private fun nudge(
        x: Int,
        textWidth: Int,
        screen: ScreenProfile,
        freeStart: Int,
        freeEnd: Int,
    ): Int? {
        val candidates = ArrayList<Int>(3)
        candidates.add(x)
        if (hasCutout(screen)) {
            candidates.add(screen.cutoutRightPx + screen.px(GAP_DP))
            candidates.add(screen.cutoutLeftPx - screen.px(GAP_DP) - textWidth)
        }
        return candidates
            .filter { fits(it, textWidth, screen, freeStart, freeEnd) }
            .minByOrNull { kotlin.math.abs(it - x) }
    }

    private fun fits(
        x: Int,
        textWidth: Int,
        screen: ScreenProfile,
        freeStart: Int,
        freeEnd: Int,
    ): Boolean {
        if (x < freeStart || x + textWidth > freeEnd) return false
        return !overlapsCutout(x, textWidth, screen)
    }

    private fun overlapsCutout(x: Int, textWidth: Int, screen: ScreenProfile): Boolean {
        if (!hasCutout(screen)) return false
        val vertical = screen.cutoutTopPx < screen.statusBarHeightPx && screen.cutoutBottomPx > 0
        if (!vertical) return false
        return x < screen.cutoutRightPx && x + textWidth > screen.cutoutLeftPx
    }

    private fun hasCutout(screen: ScreenProfile): Boolean {
        return screen.cutoutRightPx > screen.cutoutLeftPx && screen.cutoutBottomPx > screen.cutoutTopPx
    }

    private fun reservedLeft(screen: ScreenProfile): Int {
        return if (screen.clockOnRight) screen.px(EDGE_DP) else screen.px(EDGE_DP) + screen.px(CLOCK_DP)
    }

    private fun reservedRight(screen: ScreenProfile): Int {
        val icons = screen.px(EDGE_DP) + screen.px(ICONS_DP)
        return if (screen.clockOnRight) icons + screen.px(CLOCK_DP) else icons
    }

    private fun below(screen: ScreenProfile, priceText: String): OverlayLayout {
        val textWidth = estimateTextWidthPx(priceText, CHIP_TEXT_SP, screen.density)
        val pad = screen.px(CHIP_PAD_H_DP) * 2
        val viewWidth = textWidth + pad
        val x = ((screen.widthPx - viewWidth) / 2).coerceAtLeast(0)
        val y = (screen.statusBarHeightPx + screen.px(BELOW_GAP_DP))
            .coerceAtMost((screen.heightPx - estimateTextHeightPx(CHIP_TEXT_SP, screen.density)).coerceAtLeast(0))
        return OverlayLayout(
            xPx = clampX(x, viewWidth, screen.widthPx),
            yPx = y.coerceAtLeast(0),
            textSizeSp = CHIP_TEXT_SP,
            chip = true,
            slotWidthPx = viewWidth,
        )
    }

    private fun custom(
        screen: ScreenProfile,
        xFraction: Float,
        yFraction: Float,
        priceText: String,
    ): OverlayLayout {
        val y = (yFraction.coerceIn(0f, 1f) * screen.heightPx).roundToInt()
        val chip = y >= screen.statusBarHeightPx - screen.px(2f)
        val sp = if (chip) CHIP_TEXT_SP else {
            val statusDp = (screen.statusBarHeightPx / screen.density).coerceAtLeast(18f)
            (statusDp * 0.42f).coerceIn(MIN_STATUS_TEXT_SP, MAX_STATUS_TEXT_SP)
        }
        val textWidth = estimateTextWidthPx(priceText, sp, screen.density)
        val pad = if (chip) screen.px(CHIP_PAD_H_DP) * 2 else 0
        val viewWidth = textWidth + pad
        val textHeight = estimateTextHeightPx(sp, screen.density) + if (chip) screen.px(8f) else 0
        val x = (xFraction.coerceIn(0f, 1f) * screen.widthPx).roundToInt()
        return OverlayLayout(
            xPx = clampX(x, viewWidth, screen.widthPx),
            yPx = y.coerceIn(0, (screen.heightPx - textHeight).coerceAtLeast(0)),
            textSizeSp = sp,
            chip = chip,
            slotWidthPx = viewWidth,
        )
    }

    private fun clampX(x: Int, viewWidth: Int, screenWidth: Int): Int {
        return x.coerceIn(0, (screenWidth - viewWidth).coerceAtLeast(0))
    }

    private fun ScreenProfile.px(dp: Float): Int = (dp * density).roundToInt()
}
