package com.opencloudgaming.opennow.vr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Locks the real-device regressions:
 *  - 2026-11-09: the passthrough window turned into a PORTRAIT 9:16 rect because a 90° rotation
 *    caused the quad ratio to be reciprocated; the camera picture was stretched instead of cropped.
 *  - 2026-11-10: after the user manually rotated the preview upright, the picture STILL looked
 *    stretched. centreCrop() works in upright CONTENT axes, but the renderer applies the crop to
 *    the raw-buffer UVs AFTER the rotation transform — a quarter turn transposes those axes, so
 *    the keep-fractions must be swapped. sampledAspect() is the no-stretch invariant that catches
 *    it without an emulator.
 */
class VrPassthroughGeometryTest {

    /** The sampled buffer region MUST have exactly the window's ratio after ANY rotation. */
    private fun assertNoStretch(rawW: Int, rawH: Int, fourThree: Boolean, rotation: Int) {
        val window = VrPassthroughGeometry.windowAspect(fourThree)
        val raw = VrPassthroughGeometry.previewAspect(rawW, rawH, window)
        val content = VrPassthroughGeometry.contentAspect(raw, rotation)
        val (keepU, keepV) = VrPassthroughGeometry.bufferCrop(content, window, rotation)
        assertTrue("keepU=$keepU out of (0,1]", keepU > 0f && keepU <= 1f + 1e-6f)
        assertTrue("keepV=$keepV out of (0,1]", keepV > 0f && keepV <= 1f + 1e-6f)
        val sampled = VrPassthroughGeometry.sampledAspect(raw, rotation, keepU, keepV)
        assertEquals(
            "buffer=${rawW}x${rawH} window=$window rotation=$rotation sampled=$sampled",
            window,
            sampled,
            1e-4f,
        )
    }

    @Test
    fun windowAspectIsAlwaysLandscapeAndNeverFollowsRotation() {
        val a43 = VrPassthroughGeometry.windowAspect(true)
        val a169 = VrPassthroughGeometry.windowAspect(false)
        assertEquals(4f / 3f, a43, 1e-5f)
        assertEquals(16f / 9f, a169, 1e-5f)
        assertTrue("4:3 window must be landscape", a43 > 1f)
        assertTrue("16:9 window must be landscape", a169 > 1f)
        // The exact old bug: 16:9 preview + 90° -> 1/(16/9) = 9/16 portrait.
        val oldPortrait = 1f / (16f / 9f)
        assertTrue(oldPortrait < 1f)
        assertTrue("window must never be that reciprocal", a169 > 1f && oldPortrait < 1f)
    }

    @Test
    fun previewAspectUsesTheRealBufferAxesIncludingPortrait() {
        assertEquals(16f / 9f, VrPassthroughGeometry.previewAspect(1280, 720, 4f / 3f), 1e-5f)
        assertEquals(4f / 3f, VrPassthroughGeometry.previewAspect(640, 480, 16f / 9f), 1e-5f)
        // A vendor PORTRAIT buffer is reported VERBATIM. Faking a landscape ratio here was the
        // second cause of the stretched picture: the renderer then cropped the wrong axis.
        assertEquals(9f / 16f, VrPassthroughGeometry.previewAspect(720, 1280, 4f / 3f), 1e-5f)
        // Before the first camera frame: fallback, still landscape. Extreme values are clamped.
        assertEquals(4f / 3f, VrPassthroughGeometry.previewAspect(0, 0, 4f / 3f), 1e-5f)
        assertEquals(8f, VrPassthroughGeometry.previewAspect(8000, 100, 4f / 3f), 1e-5f)
        assertEquals(0.25f, VrPassthroughGeometry.previewAspect(100, 8000, 4f / 3f), 1e-5f)
    }

    @Test
    fun autoRotationProducesNoCropWhenTierMatchesWindow() {
        val preview = VrPassthroughGeometry.previewAspect(640, 480, 4f / 3f)
        val window = VrPassthroughGeometry.windowAspect(true)
        val content = VrPassthroughGeometry.contentAspect(preview, 0)
        val (keepU, keepV) = VrPassthroughGeometry.bufferCrop(content, window, 0)
        assertEquals("AUTO must not rotate the content", preview, content, 1e-5f)
        assertEquals(1f, keepU, 1e-5f)
        assertEquals(1f, keepV, 1e-5f)
        assertEquals(window, VrPassthroughGeometry.sampledAspect(preview, 0, keepU, keepV), 1e-5f)
    }

    @Test
    fun aManual90DegreeRotationKeepsTheWindowLandscapeAndCropsInsteadOfStretching() {
        val preview = VrPassthroughGeometry.previewAspect(1280, 720, 16f / 9f)
        val window = VrPassthroughGeometry.windowAspect(false)
        val content = VrPassthroughGeometry.contentAspect(preview, 90)
        assertTrue(content < 1f)
        assertEquals(16f / 9f, window, 1e-5f)
        val (cropX, cropY) = VrPassthroughGeometry.centreCrop(content, window)
        assertEquals(1f, cropX, 1e-5f)
        assertTrue("excess height must be cropped", cropY < 1f)
        assertEquals(content / window, cropY, 1e-5f)

        // The crop is in CONTENT axes; for a quarter turn the buffer axes are transposed.
        val (keepU, keepV) = VrPassthroughGeometry.bufferCrop(content, window, 90)
        assertEquals(cropY, keepU, 1e-5f)
        assertEquals(cropX, keepV, 1e-5f)
        assertEquals(
            "the sampled region must be exactly 16:9",
            window,
            VrPassthroughGeometry.sampledAspect(preview, 90, keepU, keepV),
            1e-5f,
        )
        // Regression proof: applying the UNSWAPPED content crop (the old renderer) really did
        // stretch the picture, so this fix is not a no-op.
        val oldSampled = VrPassthroughGeometry.sampledAspect(preview, 90, cropX, cropY)
        assertTrue("old unswapped crop must be visibly wrong", abs(oldSampled - window) > 0.5f)
    }

    @Test
    fun bufferCropSwapsAxesOnlyForQuarterTurns() {
        val content = 16f / 9f
        val window = 4f / 3f
        val (cropX, cropY) = VrPassthroughGeometry.centreCrop(content, window)
        assertTrue("need an asymmetric crop for this test", abs(cropX - cropY) > 0.1f)
        listOf(0, 180).forEach { deg ->
            val (u, v) = VrPassthroughGeometry.bufferCrop(content, window, deg)
            assertEquals("rotation=$deg", cropX, u, 1e-5f)
            assertEquals("rotation=$deg", cropY, v, 1e-5f)
        }
        listOf(90, 270).forEach { deg ->
            val (u, v) = VrPassthroughGeometry.bufferCrop(content, window, deg)
            assertEquals("rotation=$deg", cropY, u, 1e-5f)
            assertEquals("rotation=$deg", cropX, v, 1e-5f)
        }
    }

    @Test
    fun everyRotationSamplesAWindowShapedRegion() {
        val buffers = listOf(640 to 480, 1280 to 720, 1920 to 1080, 720 to 1280, 480 to 640)
        listOf(true, false).forEach { fourThree ->
            listOf(0, 90, 180, 270).forEach { rotation ->
                buffers.forEach { (w, h) -> assertNoStretch(w, h, fourThree, rotation) }
            }
        }
    }

    @Test
    fun aPortraitBufferCanStillFillALandscapeWindowWithoutStretching() {
        // Vendor advertises only 720x1280. A quarter turn makes the upright picture landscape;
        // the window stays landscape and the sampled region still matches it exactly.
        val raw = VrPassthroughGeometry.previewAspect(720, 1280, 4f / 3f)
        val window = VrPassthroughGeometry.windowAspect(true)
        assertTrue("raw buffer really is portrait", raw < 1f)
        val content = VrPassthroughGeometry.contentAspect(raw, 90)
        assertTrue("a quarter turn makes it upright landscape", content > 1f)
        val (keepU, keepV) = VrPassthroughGeometry.bufferCrop(content, window, 90)
        assertEquals(
            window,
            VrPassthroughGeometry.sampledAspect(raw, 90, keepU, keepV),
            1e-5f,
        )
    }

    @Test
    fun sixteenNineWindowCropsA43PreviewSymmetrically() {
        val preview = VrPassthroughGeometry.previewAspect(640, 480, 4f / 3f)
        val window = VrPassthroughGeometry.windowAspect(false)
        val (cu, cv) = VrPassthroughGeometry.centreCrop(
            VrPassthroughGeometry.contentAspect(preview, 0),
            window,
        )
        assertEquals(1f, cu, 1e-5f)
        assertEquals(preview / window, cv, 1e-5f)
        val lo = VrPassthroughGeometry.cropUv(0f, cv)
        val hi = VrPassthroughGeometry.cropUv(1f, cv)
        assertEquals("crop must be centred", 1f - hi, lo, 1e-5f)
        assertTrue(lo >= 0f && hi <= 1f)
        assertEquals("no-crop is identity", 0.5f, VrPassthroughGeometry.cropUv(0.5f, 1f), 1e-5f)
    }

    @Test
    fun cropUvAlwaysStaysInsideTheUnitRange() {
        listOf(0.25f, 0.42f, 0.75f, 1f).forEach { keep ->
            listOf(0f, 0.5f, 1f).forEach { c ->
                val v = VrPassthroughGeometry.cropUv(c, keep)
                assertTrue("keep=$keep c=$c -> $v", v >= 0f && v <= 1f)
            }
        }
    }
}
