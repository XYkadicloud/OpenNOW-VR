package com.opencloudgaming.opennow.vr

/**
 * Pure geometry maths for the camera-passthrough window. Deliberately has NO Android / OpenGL
 * dependency, so JVM unit tests can lock the real-device regressions:
 *
 *  1. (2026-11-09) The window must ALWAYS be LANDSCAPE (4:3 or 16:9). The old code took the
 *     reciprocal of the preview aspect whenever the effective rotation was 90/270, which turned a
 *     normal 16:9 preview into a portrait 9:16 window — the "被强制拉伸成了竖屏的 9:16 比例" report.
 *  2. (2026-11-10) Camera pixels must NEVER be stretched, INCLUDING when a manual 90°/270°
 *     rotation is used to make the picture upright ("我是已经开了摄像头的翻转...但比例有拉伸").
 *     [centreCrop] works in UPRIGHT CONTENT axes, but the renderer applies the crop to the RAW
 *     BUFFER UV axes AFTER the rotation transform. For a quarter turn those axes are TRANSPOSED,
 *     so the two keep-fractions must be swapped. [bufferCrop] is that mapping; [sampledAspect]
 *     lets the JVM tests prove the invariant `sampledAspect == windowAspect` for every rotation.
 *  3. The REAL Camera2 buffer ratio is reported verbatim (it may be portrait). Hiding it behind a
 *     long-edge/short-edge normalisation made the camera and the renderer disagree about which
 *     axis was wide, which is another way to stretch the picture.
 *
 * Rotation only ever changes which pixels are sampled; it NEVER changes the shape of the window.
 * Keeping this separate from [VrTheaterRenderer] also means the rules survive a GL context loss
 * and can be verified without an emulator.
 */
internal object VrPassthroughGeometry {
    const val ASPECT_4_3 = 4f / 3f
    const val ASPECT_16_9 = 16f / 9f

    /** Window ratio chosen by the user. NEVER portrait, NEVER derived from the rotation. */
    fun windowAspect(fourThree: Boolean): Float = if (fourThree) ASPECT_4_3 else ASPECT_16_9

    /**
     * REAL raw preview buffer ratio W / H, exactly as reported by Camera2. It CAN be < 1 when a
     * vendor only advertises portrait preview sizes; the geometry below handles that instead of
     * pretending the buffer is landscape. [fallback] is used only before the camera has opened.
     */
    fun previewAspect(width: Int, height: Int, fallback: Float): Float {
        if (width <= 0 || height <= 0) return fallback.coerceIn(0.25f, 8f)
        return (width.toFloat() / height.toFloat()).coerceIn(0.25f, 8f)
    }

    /** Ratio of the UPRIGHT picture after a clockwise rotation of [rotationDeg]. */
    fun contentAspect(previewAspect: Float, rotationDeg: Int): Float =
        if (rotationDeg == 90 || rotationDeg == 270) {
            (1f / previewAspect).coerceIn(0.25f, 8f)
        } else {
            previewAspect.coerceIn(0.25f, 8f)
        }

    /**
     * Fraction of the sampled UV range to KEEP on each axis OF THE UPRIGHT CONTENT. Exactly one
     * axis is < 1f: the excess of the wider dimension is cropped symmetrically, so every pixel
     * keeps its original aspect. A perfect match (AUTO + 4:3 preview + 4:3 window) returns (1, 1).
     */
    fun centreCrop(contentAspect: Float, windowAspect: Float): Pair<Float, Float> {
        val c = contentAspect.coerceIn(0.25f, 8f)
        val w = windowAspect.coerceIn(0.25f, 8f)
        return if (c > w) Pair(w / c, 1f) else Pair(1f, c / w)
    }

    /**
     * Same crop, expressed on the RAW BUFFER UV axes the renderer actually samples.
     *
     * VrTheaterRenderer rotates the quad UVs first: for 0/180° the buffer u axis still follows the
     * window's horizontal axis, but for 90/270° the two axes are TRANSPOSED (buffer u follows the
     * window's VERTICAL axis and vice versa). Applying the content-space (cropX, cropY) directly
     * to (su, sv) in those cases samples a tall, thin sliver and blows it up into a wide window —
     * exactly the real-device "方向正常但比例拉伸" report. Swapping the pair restores
     * `sampledAspect(...) == windowAspect` for every angle. Mirroring is an axis flip, not a
     * transpose, so it needs no special case.
     */
    fun bufferCrop(
        contentAspect: Float,
        windowAspect: Float,
        rotationDeg: Int,
    ): Pair<Float, Float> {
        val (cropX, cropY) = centreCrop(contentAspect, windowAspect)
        return if (rotationDeg == 90 || rotationDeg == 270) {
            Pair(cropY, cropX)
        } else {
            Pair(cropX, cropY)
        }
    }

    /**
     * Aspect ratio the sampled buffer region ACTUALLY has once it is drawn on the window.
     * `previewAspect` is the raw W/H, `keepU/keepV` are the [bufferCrop] results.
     *
     * This is the no-stretch invariant: it must equal the window ratio for 0/90/180/270, landscape
     * OR portrait buffers. Units are normalised (buffer height = 1, width = previewAspect).
     */
    fun sampledAspect(
        previewAspect: Float,
        rotationDeg: Int,
        keepU: Float,
        keepV: Float,
    ): Float {
        val p = previewAspect.coerceIn(0.25f, 8f)
        val regionW = keepU * p
        val regionH = keepV
        return if (rotationDeg == 90 || rotationDeg == 270) {
            regionH / regionW
        } else {
            regionW / regionH
        }
    }

    /** Apply a centred crop to a UV coordinate in [0,1]; the result always stays in [0,1]. */
    fun cropUv(coord: Float, keep: Float): Float = (1f - keep) / 2f + coord * keep
}
