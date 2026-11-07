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

    const val BACKGROUND_BRIGHTNESS_MIN = 0.2f
    const val BACKGROUND_BRIGHTNESS_MAX = 1.5f
    const val BACKGROUND_BRIGHTNESS_STEP = 0.05f

    /** Interpupillary distance, expressed as a multiplier of the Cardboard device default IPD. */
    const val IPD_SCALE_MIN = 0.5f
    const val IPD_SCALE_MAX = 1.6f
    const val IPD_SCALE_STEP = 0.05f

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
    /** When true, draws ONE vertical center-alignment line as a FIXED SCREEN-SPACE overlay:
     *  it is NOT part of the VR quad / panel texture, does NOT follow head rotation, and is drawn
     *  once per eye viewport at the exact screen centre (from the bottom up to the middle).
     *  Used to confirm the two eyes are rendering stereoscopically. Adjustable. */
    val showCenterAlignmentLine: Boolean = false,
    /** When true, mirrors the STREAM only, left-right. Status panel, menu and 360° background are
     *  untouched. Implemented with quad texture coordinates, no shader source change. */
    val horizontalMirror: Boolean = false,
    /** When true, flips the STREAM only, top-bottom. Status panel, menu and 360° background are
     *  untouched. Independent of [horizontalMirror]; enabling both rotates the stream by 180°. */
    val verticalFlip: Boolean = false,
    /** When true, draws a 360° (equirectangular) image as the VR environment behind the screen.
     *  Off by default to keep the original pure-black behaviour and the low-end perf budget. */
    val backgroundEnabled: Boolean = false,
    /** Content URI or file path of the user-imported 360° image. Empty = built-in procedural
     *  starfield, so the background always shows something when enabled (no asset required). */
    val backgroundImageUri: String = "",
    /** Independent brightness for the 360° background only (the screen keeps its own brightness). */
    val backgroundBrightness: Float = 1.0f,
    /**
     * Interpupillary distance / 瞳距, as a multiplier of the Cardboard device default.
     *
     * 1.0 = exactly the device-calibrated IPD (unchanged behaviour). Lower values pull the two
     * eye viewpoints together (weaker stereo, easier fusion); higher values separate them
     * (stronger depth, more likely double image). Implemented by scaling the x eye-displacement
     * in the per-eye view matrix — pure Kotlin, NO shader change, NO SDK state mutation.
     */
    val ipdScale: Float = 1.0f,
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
        horizontalMirror = horizontalMirror,
        verticalFlip = verticalFlip,
        backgroundEnabled = backgroundEnabled,
        backgroundImageUri = backgroundImageUri,
        backgroundBrightness = backgroundBrightness.coerceIn(
            VrRanges.BACKGROUND_BRIGHTNESS_MIN, VrRanges.BACKGROUND_BRIGHTNESS_MAX,
        ),
        ipdScale = ipdScale.coerceIn(VrRanges.IPD_SCALE_MIN, VrRanges.IPD_SCALE_MAX),
    )

    companion object {
        val DEFAULT = VrSettings()
    }
}
