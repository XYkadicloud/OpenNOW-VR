package com.opencloudgaming.opennow.vr

/** Rendering mode. CARDBOARD = stereo + lens distortion; SIMULATION = monocular, bare-phone. */
enum class VrViewerMode { CARDBOARD, SIMULATION }

/**
 * How the stereo picture occupies the PHYSICAL screen (the "全屏填充" control).
 *
 * DISTORTED = original Cardboard path. CardboardView.onRenderStereoFrame() calls
 * renderEyeToDisplay(), which warps both eyes through a lens-distortion mesh. That mesh is built
 * from a FOV clamped to the device params (lens_distortion.cc:189-194), so it only covers the
 * middle of the screen: large BLACK BANDS above/below and a gap between the two eyes. This is the
 * round/barrel look in the user's screenshot.
 *
 * FILL = keep BOTH eyes (real stereo), but bypass that distortion composite. onFinishFrame()
 * re-draws each eye into a rectangular half-screen viewport (w/2 x FULL height) using the very
 * same eye MVP the SDK used for the FBO. The FBO content is already full-height, so the result is
 * edge-to-edge vertically, with no rounded corners, no vignette and no centre gap.
 *
 * This is NOT "monocular / direct 2D display": both eye matrices are still used, so stereo depth
 * is preserved. Fully adjustable and revertible; DEFAULT is FILL because that is the requested
 * behaviour.
 */
enum class VrScreenFillMode { DISTORTED, FILL }

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
     * Effective interpupillary distance / 等效瞳距, as a multiplier of the Cardboard default.
     *
     * 1.0 = unchanged SDK rendering (no shift at all). Lower values pull the two eyes' images
     * together (weaker stereo, easier fusion); higher values push them apart (stronger stereo).
     *
     * Implemented as an equal-and-opposite screen-space shift of each eye's final clip-space x
     * (`VrMatrix4.applyIpdShiftInto`), NOT by scaling `Eye.getEyeFromHead()` — the latter also
     * feeds the off-axis projection and distortion mesh, so scaling it alone desynchronises the
     * camera from its frustum and breaks stereo fusion. Pure Kotlin, NO shader source change,
     * NO SDK device-parameter mutation. MONOCULAR/SIMULATION mode is unaffected.
     */
    val ipdScale: Float = 1.0f,
    /**
     * DISTORTED = original Cardboard stereo + lens-distortion mesh (round vignette, big black
     * bands top/bottom). FILL = both eyes retained, distortion composite bypassed, rectangular
     * half-screen viewports with FULL height — i.e. "上下填满 / 没有大黑边".
     */
    val screenFillMode: VrScreenFillMode = VrScreenFillMode.FILL,
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
        screenFillMode = screenFillMode,
    )

    companion object {
        val DEFAULT = VrSettings()
    }
}
