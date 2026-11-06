package com.opencloudgaming.opennow.vr

import android.content.Context
import android.view.SurfaceView
import android.view.View
import com.google.cardboard.sdk.CardboardView

/**
 * Google Cardboard SDK view hosted inside MainActivity (NOT a separate Activity).
 * Keeping it in the same Activity preserves the existing NativeStreamInputRouter gamepad path.
 *
 * WHY setZOrderOnTop (the fix for "VR just shows the full-screen 2D picture"):
 *  - The phone UI owns `StreamVideoSurface`, whose children are SurfaceViews. SurfaceView layers
 *    are composited by SurfaceFlinger, NOT by the Compose/View draw order, so VrModeHost being
 *    drawn *after* OpenNowApp does not put the Cardboard GL layer above the 2D stream layer.
 *    On the OPPO F9 the 2D stream layer stayed on top and "VR" looked like the flat full-screen
 *    stream instead of a black room with a 2D screen in it.
 *  - Promoting the Cardboard GL SurfaceView with setZOrderOnTop(true) makes the VR scene
 *    authoritative. setZOrderOnTop is most reliable before the surface is created, so it is
 *    applied in init and again in onAttachedToWindow (both before GLSurfaceView.onResume()).
 *  - The 2D stream SurfaceViews are deliberately LEFT ALIVE (not hidden/GONE). During VR the
 *    decoder writes into the VR SurfaceTexture (VrVideoSurfaceRegistry), not into the 2D surfaces.
 *    Hiding them would DESTROY their native Surface (surfaceDestroyed) and force a decoder
 *    restart / black frame on exit. Keeping them attached makes phone <-> VR switching seamless.
 *    VrModeHost removes this whole CardboardView when VR is off, revealing the 2D layer again.
 *
 * The SDK 2D touch overlay (alignment marker, X / gear buttons) is hidden: this project is
 * gamepad-only, and setStereoRenderMode(true) calls showAll() on that overlay, so it is re-hidden
 * after every settings change.
 */
class VrTheaterView(context: Context) : CardboardView(context) {
    private val theaterRenderer = VrTheaterRenderer()

    init {
        setRenderer(theaterRenderer)
        // Headset use has no touch input to reset the screen-off timer.
        setKeepScreenOn(true)
        promoteGlSurface()
        applySettings(VrSession.settings.value)
    }

    fun applySettings(value: VrSettings) {
        theaterRenderer.applySettings(value)
        val wantStereo = value.viewerMode == VrViewerMode.CARDBOARD
        if (isStereoRenderModeEnabled != wantStereo) setStereoRenderMode(wantStereo)
        hideTouchOverlay()
    }

    /** Only called while the GL surface does not exist yet (init / attach), never on recomposition. */
    private fun promoteGlSurface() {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is SurfaceView) child.setZOrderOnTop(true)
        }
    }

    private fun hideTouchOverlay() {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child !is SurfaceView) child.visibility = View.GONE
        }
    }

    fun start() = onResume()

    fun stop() = onPause()

    fun release() = onDestroy()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        promoteGlSurface()
        hideTouchOverlay()
    }

    override fun onDetachedFromWindow() {
        VrVideoSurfaceRegistry.clear()
        super.onDetachedFromWindow()
    }
}
