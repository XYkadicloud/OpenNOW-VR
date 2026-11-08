package com.opencloudgaming.opennow.vr

/** Rendering mode. CARDBOARD = stereo + lens distortion; SIMULATION = monocular, bare-phone. */
enum class VrViewerMode { CARDBOARD, SIMULATION }

/**
 * How the stereo picture occupies the PHYSICAL screen (the "全屏填充" control).
 *
 * DISTORTED = original Cardboard path, unchanged. CardboardView.onRenderStereoFrame() calls
 * renderEyeToDisplay(), which warps both eyes through a lens-distortion mesh. That mesh is built
 * from a FOV clamped to the device params (lens_distortion.cc:189-194), so it only covers the
 * middle of the screen: the round/barrel eye windows with BLACK BANDS above/below in the user's
 * screenshot. This is the exact "之前那样" rendering and the one-click rollback.
 *
 * FILL = "单纯放大". The SDK stereo + lens-distortion rendering is left COMPLETELY intact (both
 * eye matrices, head pose, IPD shift, barrel correction, 360° background — everything). Once it
 * has been composited onto the default framebuffer, onFinishFrame() copies that finished picture
 * into a texture and blits it back with a centred, UNIFORM zoom of [VrSettings.zoomScale]. The
 * black bands are simply pushed off the edges; both eyes grow together so they still fuse in a
 * viewer. No FOV change, no projection change, no new geometry, no shader change, no SDK C++
 * change. Only the vertical direction is GUARANTEED to fill; the horizontal edges are allowed to
 * keep a small black margin ("不是说全部填满只是上下至少要填满").
 *
 * Adjustable and revertible: zoomScale = 1.00 is a no-op, and DISTORTED skips the post-process
 * entirely. DEFAULT is FILL because that is the requested behaviour.
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

    /** Uniform post-process zoom used by VrScreenFillMode.FILL. 1.00 = original SDK output. */
    const val ZOOM_MIN = 1.00f
    const val ZOOM_MAX = 2.00f
    const val ZOOM_STEP = 0.02f

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
     * DISTORTED = original Cardboard stereo + lens-distortion mesh (round windows, black bands
     * top/bottom). FILL = the SAME stereo + distortion, uniformly enlarged afterwards until the
     * vertical direction is filled. One-click rollback to DISTORTED at any time.
     */
    val screenFillMode: VrScreenFillMode = VrScreenFillMode.FILL,
    /**
     * "单纯放大": uniform zoom applied to the FINISHED stereo frame in onFinishFrame().
     * 1.00 = no zoom at all (bit-identical to the original SDK output). 1.18 pushes the typical
     * Cardboard barrel window's top/bottom black bands off the screen while still leaving a small
     * horizontal margin; raise it if your viewer/QR profile has wider bands. Pure post-process:
     * no FOV, projection, geometry, shader or SDK C++ change, and both eyes stay fused.
     */
    val zoomScale: Float = 1.18f,
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
        zoomScale = zoomScale.coerceIn(VrRanges.ZOOM_MIN, VrRanges.ZOOM_MAX),
    )

    companion object {
        val DEFAULT = VrSettings()
    }
}
