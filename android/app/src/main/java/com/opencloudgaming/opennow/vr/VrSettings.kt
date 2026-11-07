package com.opencloudgaming.opennow.vr

/** Rendering mode. CARDBOARD = stereo + lens distortion; SIMULATION = monocular, bare-phone. */
enum class VrViewerMode { CARDBOARD, SIMULATION }

/** Numeric bounds for every adjustable VR parameter. Kept here so UI, storage and tests agree. */
object VrRanges {
    const val SCREEN_SCALE_MIN = 0.5f
    const val SCREEN_SCALE_MAX = 3.0f
    const val SCREEN_SCALE_STEP = 0.1f

    const val DISTANCE_MIN = 0.8f
    const val DISTANCE_MAX = 6.0f
    const val DISTANCE_STEP = 0.2f

    const val BRIGHTNESS_MIN = 0.4f
    const val BRIGHTNESS_MAX = 1.4f
    const val BRIGHTNESS_STEP = 0.05f

    const val CURVATURE_MIN = 0.0f
    const val CURVATURE_MAX = 1.0f
    const val CURVATURE_STEP = 0.1f

    const val VERTICAL_OFFSET_MIN = -1.0f
    const val VERTICAL_OFFSET_MAX = 1.0f
    const val VERTICAL_OFFSET_STEP = 0.1f

    const val HEAD_SENSITIVITY_MIN = 0.0f
    const val HEAD_SENSITIVITY_MAX = 1.5f
    const val HEAD_SENSITIVITY_STEP = 0.1f

    const val DEAD_ZONE_MIN = 0.05f
    const val DEAD_ZONE_MAX = 0.5f
    const val DEAD_ZONE_STEP = 0.05f

    const val REPEAT_MIN_MS = 60L
    const val REPEAT_MAX_MS = 500L
    const val REPEAT_STEP_MS = 20L
}

/**
 * All user-adjustable VR parameters. Pure Kotlin, no Android imports — safe for JVM unit tests.
 *
 * screenScale is the headline feature: it enlarges the flat (2D) stream screen. The stream itself
 * is NEVER rendered as stereoscopic 3D; only the virtual environment is stereo.
 */
data class VrSettings(
    val screenScale: Float = 1.0f,
    val screenDistanceMeters: Float = 2.2f,
    val brightness: Float = 1.0f,
    val curvature: Float = 0.0f,
    val screenVerticalOffset: Float = 0.0f,
    val viewerMode: VrViewerMode = VrViewerMode.CARDBOARD,
    val headTrackingEnabled: Boolean = true,
    val headTrackingSensitivity: Float = 1.0f,
    val gamepadDeadZone: Float = 0.20f,
    val menuRepeatMs: Long = 140L,
    val showStatus: Boolean = true,
    /** When true, draws a single vertical center-alignment line on the VR status panel.
     *  Helps confirm eye position / screen centering. Can be toggled off in settings. */
    val showCenterAlignmentLine: Boolean = false,
) {
    /** Clamps every numeric field into its declared range. Idempotent. */
    fun sanitized(): VrSettings = copy(
        screenScale = screenScale.coerceIn(VrRanges.SCREEN_SCALE_MIN, VrRanges.SCREEN_SCALE_MAX),
        screenDistanceMeters = screenDistanceMeters.coerceIn(VrRanges.DISTANCE_MIN, VrRanges.DISTANCE_MAX),
        brightness = brightness.coerceIn(VrRanges.BRIGHTNESS_MIN, VrRanges.BRIGHTNESS_MAX),
        curvature = curvature.coerceIn(VrRanges.CURVATURE_MIN, VrRanges.CURVATURE_MAX),
        screenVerticalOffset = screenVerticalOffset.coerceIn(
            VrRanges.VERTICAL_OFFSET_MIN, VrRanges.VERTICAL_OFFSET_MAX,
        ),
        headTrackingSensitivity = headTrackingSensitivity.coerceIn(
            VrRanges.HEAD_SENSITIVITY_MIN, VrRanges.HEAD_SENSITIVITY_MAX,
        ),
        gamepadDeadZone = gamepadDeadZone.coerceIn(VrRanges.DEAD_ZONE_MIN, VrRanges.DEAD_ZONE_MAX),
        menuRepeatMs = menuRepeatMs.coerceIn(VrRanges.REPEAT_MIN_MS, VrRanges.REPEAT_MAX_MS),
        showCenterAlignmentLine = showCenterAlignmentLine,
    )

    companion object {
        val DEFAULT = VrSettings()
    }
}
