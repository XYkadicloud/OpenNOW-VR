package com.opencloudgaming.opennow.vr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VrMenuModelTest {

    @Test
    fun exposesAllRowsWithUniqueIds() {
        assertEquals(21, VrMenuModel.items.size)
        assertEquals(VrMenuModel.items.size, VrMenuModel.items.map { it.id }.toSet().size)
        assertEquals(VrMenuItemId.SCREEN_SCALE, VrMenuModel.items.first().id)
        assertEquals(VrMenuItemId.EXIT_VR, VrMenuModel.items.last().id)
    }

    @Test
    fun increasingScreenScaleUsesTheDeclaredStep() {
        val next = VrMenuModel.adjust(VrSettings.DEFAULT, VrMenuItemId.SCREEN_SCALE, 1)
        assertEquals(
            VrSettings.DEFAULT.screenScale + VrRanges.SCREEN_SCALE_STEP,
            next.screenScale,
            1e-5f,
        )
    }

    @Test
    fun adjustingClampsAtBothEnds() {
        val maxed = VrMenuModel.adjust(VrSettings.DEFAULT, VrMenuItemId.SCREEN_SCALE, 10_000)
        val minned = VrMenuModel.adjust(VrSettings.DEFAULT, VrMenuItemId.SCREEN_SCALE, -10_000)
        assertEquals(VrRanges.SCREEN_SCALE_MAX, maxed.screenScale, 0f)
        assertEquals(VrRanges.SCREEN_SCALE_MIN, minned.screenScale, 0f)
    }

    @Test
    fun zeroStepsIsANoOp() {
        val s = VrSettings.DEFAULT
        assertEquals(s, VrMenuModel.adjust(s, VrMenuItemId.BRIGHTNESS, 0))
    }

    @Test
    fun actionRowsDoNotChangeSettingsOnAdjust() {
        val s = VrSettings.DEFAULT
        assertEquals(s, VrMenuModel.adjust(s, VrMenuItemId.EXIT_VR, 5))
        assertEquals(s, VrMenuModel.adjust(s, VrMenuItemId.SCAN_QR, -5))
        assertEquals(s, VrMenuModel.adjust(s, VrMenuItemId.RESET_DEFAULTS, 1))
    }

    @Test
    fun enumAndToggleRowsFlip() {
        val s = VrSettings.DEFAULT
        val flipped = VrMenuModel.adjust(s, VrMenuItemId.VIEWER_MODE, 1)
        assertEquals(VrViewerMode.SIMULATION, flipped.viewerMode)
        assertEquals(
            VrViewerMode.CARDBOARD,
            VrMenuModel.adjust(flipped, VrMenuItemId.VIEWER_MODE, -1).viewerMode,
        )
        assertEquals(false, VrMenuModel.adjust(s, VrMenuItemId.HEAD_TRACKING, 1).headTrackingEnabled)
        assertEquals(false, VrMenuModel.adjust(s, VrMenuItemId.SHOW_STATUS, 1).showStatus)
    }

    @Test
    fun activateResetsDefaults() {
        val changed = VrSettings(screenScale = 2.5f, brightness = 0.5f)
        assertEquals(VrSettings.DEFAULT, VrMenuModel.activate(changed, VrMenuItemId.RESET_DEFAULTS))
    }

    @Test
    fun activateOnActionRowsOtherThanResetDoesNotMutateSettings() {
        val s = VrSettings.DEFAULT
        assertEquals(s, VrMenuModel.activate(s, VrMenuItemId.EXIT_VR))
        assertEquals(s, VrMenuModel.activate(s, VrMenuItemId.SCAN_QR))
        assertEquals(s, VrMenuModel.activate(s, VrMenuItemId.RECENTER))
    }

    @Test
    fun menuRepeatIsStoredAsLong() {
        val s = VrMenuModel.adjust(VrSettings.DEFAULT, VrMenuItemId.MENU_REPEAT, 1)
        assertEquals(VrSettings.DEFAULT.menuRepeatMs + VrRanges.REPEAT_STEP_MS, s.menuRepeatMs)
    }

    @Test
    fun newToggleRowsFlipAndAreReversible() {
        val s = VrSettings.DEFAULT
        assertEquals(true, VrMenuModel.adjust(s, VrMenuItemId.HORIZONTAL_MIRROR, 1).horizontalMirror)
        assertEquals(
            false,
            VrMenuModel.adjust(s.copy(horizontalMirror = true), VrMenuItemId.HORIZONTAL_MIRROR, 1)
                .horizontalMirror,
        )
        assertEquals(true, VrMenuModel.adjust(s, VrMenuItemId.VERTICAL_FLIP, 1).verticalFlip)
        assertEquals(
            false,
            VrMenuModel.adjust(s.copy(verticalFlip = true), VrMenuItemId.VERTICAL_FLIP, 1).verticalFlip,
        )
        assertEquals(true, VrMenuModel.adjust(s, VrMenuItemId.CENTER_ALIGN_LINE, 1).showCenterAlignmentLine)
        assertEquals(true, VrMenuModel.adjust(s, VrMenuItemId.BACKGROUND, 1).backgroundEnabled)
        assertEquals(false, VrMenuModel.adjust(s.copy(backgroundEnabled = true), VrMenuItemId.BACKGROUND, 1)
            .backgroundEnabled)
    }

    @Test
    fun backgroundBrightnessRangeIsClamped() {
        val high = VrMenuModel.adjust(VrSettings.DEFAULT, VrMenuItemId.BACKGROUND_BRIGHTNESS, 10_000)
        val low = VrMenuModel.adjust(VrSettings.DEFAULT, VrMenuItemId.BACKGROUND_BRIGHTNESS, -10_000)
        assertEquals(VrRanges.BACKGROUND_BRIGHTNESS_MAX, high.backgroundBrightness, 1e-4f)
        assertEquals(VrRanges.BACKGROUND_BRIGHTNESS_MIN, low.backgroundBrightness, 1e-4f)
    }

    @Test
    fun displayNeverReturnsBlank() {
        VrMenuItemId.values().forEach { id ->
            assertTrue("blank label for $id", VrMenuModel.display(VrSettings.DEFAULT, id).isNotBlank())
        }
    }

    @Test
    fun everyRangeItemStaysWithinItsDeclaredBounds() {
        VrMenuModel.items.filter { it.kind == VrMenuItemKind.RANGE }.forEach { item ->
            val high = VrMenuModel.adjust(VrSettings.DEFAULT, item.id, 10_000)
            val low = VrMenuModel.adjust(VrSettings.DEFAULT, item.id, -10_000)
            assertTrue("${item.id} upper", VrMenuModel.rawValue(high, item.id) <= item.max + 1e-4f)
            assertTrue("${item.id} lower", VrMenuModel.rawValue(low, item.id) >= item.min - 1e-4f)
        }
    }

    @Test
    fun rangeItemsUseTheirOwnSteps() {
        assertEquals(
            VrRanges.BRIGHTNESS_STEP,
            VrMenuModel.items.first { it.id == VrMenuItemId.BRIGHTNESS }.step,
            0f,
        )
        assertEquals(
            VrRanges.DISTANCE_STEP,
            VrMenuModel.items.first { it.id == VrMenuItemId.SCREEN_DISTANCE }.step,
            0f,
        )
    }

    @Test
    fun ipdScaleUsesItsOwnStepAndIsClamped() {
        val s = VrSettings.DEFAULT
        assertEquals(1.0f, s.ipdScale, 0f)
        assertEquals(
            1.0f + VrRanges.IPD_SCALE_STEP,
            VrMenuModel.adjust(s, VrMenuItemId.IPD_SCALE, 1).ipdScale,
            1e-5f,
        )
        assertEquals(
            VrRanges.IPD_SCALE_MAX,
            VrMenuModel.adjust(s, VrMenuItemId.IPD_SCALE, 10_000).ipdScale,
            1e-4f,
        )
        assertEquals(
            VrRanges.IPD_SCALE_MIN,
            VrMenuModel.adjust(s, VrMenuItemId.IPD_SCALE, -10_000).ipdScale,
            1e-4f,
        )
    }
}
