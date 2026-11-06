package com.opencloudgaming.opennow.vr

import android.content.Context
import android.view.SurfaceView
import android.view.View
import com.google.cardboard.sdk.CardboardView

/**
 * Google Cardboard SDK view hosted inside MainActivity (NOT a separate Activity).
 * Keeping it in the same Activity preserves the existing NativeStreamInputRouter gamepad path.
 *
 * SURFACE LAYERING (why this class overrides so little):
 *  - The app's phone UI already contains `StreamVideoSurface`, whose children are SurfaceViews.
 *    SurfaceView layers are owned by SurfaceFlinger, not by the Compose hierarchy, so the 2D
 *    stream layer could stay above the Cardboard GL layer even though VrModeHost is drawn later.
 *    On the OPPO F9 that is exactly what happened: "VR" looked like the full-screen 2D stream.
 *  - The Cardboard GL surface is therefore promoted with `setZOrderOnTop(true)`. The VR scene is
 *    then authoritative and the 2D stream layer can never cover it.
 *  - `StreamVideoSurface.setVrActive(true)` additionally hides the phone's 2D surfaces, so they
 *    do not keep a full-screen composited layer (and a frozen frame) alive behind the VR scene.
 */
class VrTheaterView(context: Context) : CardboardView(context) {
    private val theaterRenderer = VrTheaterRenderer()

    init {
        setRenderer(theaterRenderer)
        promoteGlSurface()
        // Gamepad-only headset use means there is no touch to reset the screen timeout.
        keepScreenOn = true
        isFocusable = true
        isFocusableInTouchMode = true
        applySettings(VrSession.settings.value)
    }

    fun applySettings(value: VrSettings) {
        theaterRenderer.applySettings(value)
        val wantStereo = value.viewerMode == VrViewerMode.CARDBOARD
        if (isStereoRenderModeEnabled != wantStereo) setStereoRenderMode(wantStereo)
        hideTouchOverlay()
    }

    /**
     * Put Cardboard's GL SurfaceView on top of the window and hide the SDK's 2D touch overlay
     * (alignment marker, X and gear buttons), which is both unwanted for a gamepad-only design and
     * is a full-screen composited layer per frame on the OPPO F9.
     */
    private fun promoteGlSurface() {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is SurfaceView) {
                child.setZOrderOnTop(true)
                child.keepScreenOn = true
                child.isFocusable = true
                child.isFocusableInTouchMode = true
                child.requestFocus()
            } else {
                child.visibility = View.GONE
            }
        }
    }

    /**
     * `setStereoRenderMode(true)` calls `showAll()` on the SDK layout, so the hidden container must
     * be re-hidden after every settings change. Re-applying z-order is harmless and keeps the
     * VR layer above the stream if the SDK ever re-creates its GL view.
     */
    private fun hideTouchOverlay() = promoteGlSurface()

    fun start() = onResume()

    fun stop() = onPause()

    fun release() = onDestroy()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        promoteGlSurface()
        requestFocus()
    }

    override fun onDetachedFromWindow() {
        VrVideoSurfaceRegistry.clear()
        super.onDetachedFromWindow()
    }
}
