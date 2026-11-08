package com.opencloudgaming.opennow.vr

import android.content.Context

/** Persists VR settings. Only this class and VrSession touch SharedPreferences. */
class VrSettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("opennow_vr_settings", Context.MODE_PRIVATE)

    fun load(): VrSettings = VrSettings(
        screenScale = prefs.getFloat(K_SCALE, VrSettings.DEFAULT.screenScale),
        screenDistanceMeters = prefs.getFloat(K_DISTANCE, VrSettings.DEFAULT.screenDistanceMeters),
        brightness = prefs.getFloat(K_BRIGHTNESS, VrSettings.DEFAULT.brightness),
        curvature = prefs.getFloat(K_CURVATURE, VrSettings.DEFAULT.curvature),
        screenVerticalOffset = prefs.getFloat(K_OFFSET, VrSettings.DEFAULT.screenVerticalOffset),
        viewerMode = runCatching {
            VrViewerMode.valueOf(prefs.getString(K_MODE, VrSettings.DEFAULT.viewerMode.name) ?: "")
        }.getOrDefault(VrSettings.DEFAULT.viewerMode),
        headTrackingEnabled = prefs.getBoolean(K_HEAD, VrSettings.DEFAULT.headTrackingEnabled),
        headTrackingSensitivity = prefs.getFloat(K_HEAD_SENS, VrSettings.DEFAULT.headTrackingSensitivity),
        gamepadDeadZone = prefs.getFloat(K_DEAD_ZONE, VrSettings.DEFAULT.gamepadDeadZone),
        menuRepeatMs = prefs.getLong(K_REPEAT, VrSettings.DEFAULT.menuRepeatMs),
        showStatus = prefs.getBoolean(K_STATUS, VrSettings.DEFAULT.showStatus),
        showCenterAlignmentLine = prefs.getBoolean(
            K_ALIGN_LINE, VrSettings.DEFAULT.showCenterAlignmentLine,
        ),
        horizontalMirror = prefs.getBoolean(K_H_MIRROR, VrSettings.DEFAULT.horizontalMirror),
        verticalFlip = prefs.getBoolean(K_V_FLIP, VrSettings.DEFAULT.verticalFlip),
        backgroundEnabled = prefs.getBoolean(K_BG_ENABLED, VrSettings.DEFAULT.backgroundEnabled),
        backgroundImageUri = prefs.getString(K_BG_URI, VrSettings.DEFAULT.backgroundImageUri) ?: "",
        backgroundBrightness = prefs.getFloat(K_BG_BRIGHT, VrSettings.DEFAULT.backgroundBrightness),
        ipdScale = prefs.getFloat(K_IPD_SCALE, VrSettings.DEFAULT.ipdScale),
        screenFillMode = runCatching {
            VrScreenFillMode.valueOf(
                prefs.getString(K_FILL_MODE, VrSettings.DEFAULT.screenFillMode.name) ?: "",
            )
        }.getOrDefault(VrSettings.DEFAULT.screenFillMode),
        zoomScale = prefs.getFloat(K_ZOOM, VrSettings.DEFAULT.zoomScale),
    ).sanitized()

    fun save(settings: VrSettings) {
        val s = settings.sanitized()
        prefs.edit()
            .putFloat(K_SCALE, s.screenScale)
            .putFloat(K_DISTANCE, s.screenDistanceMeters)
            .putFloat(K_BRIGHTNESS, s.brightness)
            .putFloat(K_CURVATURE, s.curvature)
            .putFloat(K_OFFSET, s.screenVerticalOffset)
            .putString(K_MODE, s.viewerMode.name)
            .putBoolean(K_HEAD, s.headTrackingEnabled)
            .putFloat(K_HEAD_SENS, s.headTrackingSensitivity)
            .putFloat(K_DEAD_ZONE, s.gamepadDeadZone)
            .putLong(K_REPEAT, s.menuRepeatMs)
            .putBoolean(K_STATUS, s.showStatus)
            .putBoolean(K_ALIGN_LINE, s.showCenterAlignmentLine)
            .putBoolean(K_H_MIRROR, s.horizontalMirror)
            .putBoolean(K_V_FLIP, s.verticalFlip)
            .putBoolean(K_BG_ENABLED, s.backgroundEnabled)
            .putString(K_BG_URI, s.backgroundImageUri)
            .putFloat(K_BG_BRIGHT, s.backgroundBrightness)
            .putFloat(K_IPD_SCALE, s.ipdScale)
            .putString(K_FILL_MODE, s.screenFillMode.name)
            .putFloat(K_ZOOM, s.zoomScale)
            .apply()
    }

    private companion object {
        const val K_SCALE = "screenScale"
        const val K_DISTANCE = "screenDistance"
        const val K_BRIGHTNESS = "brightness"
        const val K_CURVATURE = "curvature"
        const val K_OFFSET = "verticalOffset"
        const val K_MODE = "viewerMode"
        const val K_HEAD = "headTracking"
        const val K_HEAD_SENS = "headSensitivity"
        const val K_DEAD_ZONE = "gamepadDeadZone"
        const val K_REPEAT = "menuRepeatMs"
        const val K_STATUS = "showStatus"
        const val K_ALIGN_LINE = "showCenterAlignmentLine"
        const val K_H_MIRROR = "horizontalMirror"
        const val K_V_FLIP = "verticalFlip"
        const val K_BG_ENABLED = "backgroundEnabled"
        const val K_BG_URI = "backgroundImageUri"
        const val K_BG_BRIGHT = "backgroundBrightness"
        const val K_IPD_SCALE = "ipdScale"
        const val K_FILL_MODE = "screenFillMode"
        const val K_ZOOM = "zoomScale"
    }
}
