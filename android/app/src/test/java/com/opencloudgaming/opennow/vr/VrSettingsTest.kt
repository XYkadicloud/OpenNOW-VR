package com.opencloudgaming.opennow.vr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VrSettingsTest {

    @Test
    fun defaultsAreInsideEveryDeclaredRange() {
        val s = VrSettings.DEFAULT
        assertTrue(s.screenScale in VrRanges.SCREEN_SCALE_MIN..VrRanges.SCREEN_SCALE_MAX)
        assertTrue(s.screenDistanceMeters in VrRanges.DISTANCE_MIN..VrRanges.DISTANCE_MAX)
        assertTrue(s.brightness in VrRanges.BRIGHTNESS_MIN..VrRanges.BRIGHTNESS_MAX)
        assertTrue(s.curvature in VrRanges.CURVATURE_MIN..VrRanges.CURVATURE_MAX)
        assertTrue(s.screenVerticalOffset in VrRanges.VERTICAL_OFFSET_MIN..VrRanges.VERTICAL_OFFSET_MAX)
        assertTrue(s.headTrackingSensitivity in VrRanges.HEAD_SENSITIVITY_MIN..VrRanges.HEAD_SENSITIVITY_MAX)
        assertTrue(s.gamepadDeadZone in VrRanges.DEAD_ZONE_MIN..VrRanges.DEAD_ZONE_MAX)
        assertTrue(s.menuRepeatMs in VrRanges.REPEAT_MIN_MS..VrRanges.REPEAT_MAX_MS)
        assertTrue(s.ipdScale in VrRanges.IPD_SCALE_MIN..VrRanges.IPD_SCALE_MAX)
        assertTrue(s.zoomScale in VrRanges.ZOOM_MIN..VrRanges.ZOOM_MAX)
        assertEquals(1.0f, s.ipdScale, 0f)
        assertEquals(1.18f, s.zoomScale, 0f)
        assertEquals(VrViewerMode.CARDBOARD, s.viewerMode)
    }

    @Test
    fun sanitizeClampsOutOfRangeValues() {
        val wild = VrSettings(
            screenScale = 999f,
            screenDistanceMeters = -4f,
            brightness = 0f,
            curvature = 7f,
            screenVerticalOffset = -12f,
            headTrackingSensitivity = 100f,
            gamepadDeadZone = -1f,
            menuRepeatMs = 0L,
            ipdScale = 99f,
            zoomScale = -5f,
        )
        val s = wild.sanitized()
        assertEquals(VrRanges.SCREEN_SCALE_MAX, s.screenScale, 0f)
        assertEquals(VrRanges.DISTANCE_MIN, s.screenDistanceMeters, 0f)
        assertEquals(VrRanges.BRIGHTNESS_MIN, s.brightness, 0f)
        assertEquals(VrRanges.CURVATURE_MAX, s.curvature, 0f)
        assertEquals(VrRanges.VERTICAL_OFFSET_MIN, s.screenVerticalOffset, 0f)
        assertEquals(VrRanges.HEAD_SENSITIVITY_MAX, s.headTrackingSensitivity, 0f)
        assertEquals(VrRanges.DEAD_ZONE_MIN, s.gamepadDeadZone, 0f)
        assertEquals(VrRanges.REPEAT_MIN_MS, s.menuRepeatMs)
        assertEquals(VrRanges.ZOOM_MIN, s.zoomScale, 0f)
    }

    @Test
    fun sanitizeIsIdempotent() {
        val once = VrSettings(screenScale = 99f, brightness = -1f).sanitized()
        assertEquals(once, once.sanitized())
    }

    @Test
    fun nonNumericFieldsSurviveSanitize() {
        val s = VrSettings(viewerMode = VrViewerMode.SIMULATION, showStatus = false, headTrackingEnabled = false)
            .sanitized()
        assertEquals(VrViewerMode.SIMULATION, s.viewerMode)
        assertEquals(false, s.showStatus)
        assertEquals(false, s.headTrackingEnabled)
    }

    @Test
    fun streamOrientationAndOverlayFlagsDefaultOffAndSurviveSanitize() {
        val defaults = VrSettings.DEFAULT
        assertEquals(false, defaults.horizontalMirror)
        assertEquals(false, defaults.verticalFlip)
        assertEquals(false, defaults.showCenterAlignmentLine)
        val toggled = VrSettings(
            horizontalMirror = true,
            verticalFlip = true,
            showCenterAlignmentLine = true,
        ).sanitized()
        assertEquals(true, toggled.horizontalMirror)
        assertEquals(true, toggled.verticalFlip)
        assertEquals(true, toggled.showCenterAlignmentLine)
    }

    @Test
    fun screenFillModeDefaultsAndSurvivesSanitize() {
        val defaults = VrSettings.DEFAULT
        assertEquals(VrScreenFillMode.FILL, defaults.screenFillMode)
        val toggled = VrSettings(screenFillMode = VrScreenFillMode.DISTORTED).sanitized()
        assertEquals(VrScreenFillMode.DISTORTED, toggled.screenFillMode)
    }
}
