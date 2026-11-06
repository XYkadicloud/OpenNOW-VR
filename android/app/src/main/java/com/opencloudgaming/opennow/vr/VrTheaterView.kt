package com.opencloudgaming.opennow.vr

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import android.view.View
import com.google.cardboard.sdk.CardboardView

/**
 * Google Cardboard SDK view hosted inside MainActivity (NOT a separate Activity).
 * Keeping it in the same Activity preserves the existing NativeStreamInputRouter gamepad path.
 *
 * LAYERING / setZOrderOnTop — REVERTED (2026-11-06):
 *  - An earlier build (3cfcade) promoted the Cardboard GL SurfaceView with setZOrderOnTop(true)
 *    to stop the 2D stream SurfaceView from covering the VR scene. On the OPPO F9 running that
 *    build, entering VR instead showed the ORIGINAL non-VR phone UI — the VR layer did not appear
 *    at all. setZOrderOnTop makes the GL layer opaque and top-most, but it MUST be applied before
 *    the surface is created; calling it from init / onAttachedToWindow while iterating children
 *    gave no reliable result and is suspected of leaving the GL layer invisible/transparent, so
 *    the 2D UI underneath shows through. It is removed here to return to the known-good layering
 *    while the real cause is located with lifecycle logs. See docs/HANDOFF.md.
 *  - The 2D stream SurfaceViews are deliberately LEFT ALIVE (not hidden/GONE): hiding them would
 *    destroy their native Surface and force a decoder restart / black frame on VR exit.
 *
 * The SDK 2D touch overlay (alignment marker, X / gear buttons) is hidden: this project is
 * gamepad-only, and setStereoRenderMode(true) calls showAll() on that overlay, so it is re-hidden
 * after every settings change and on attach.
 *
 * DIAGNOSTIC LOGGING: every lifecycle edge logs under TAG "VrTheaterView". These are one-shot
 * events (never in the per-frame loop), so they cost nothing on a low-end device. Filter with
 * `logcat -s VrSession,VrModeHost,VrTheaterView,VrTheaterRenderer`.
 */
class VrTheaterView(context: Context) : CardboardView(context) {
    companion object { private const val TAG = "VrTheaterView" }

    private val theaterRenderer = VrTheaterRenderer()

    init {
        Log.d(TAG, "init: constructing VrTheaterView; childCount=$childCount")
        setRenderer(theaterRenderer)
        // Headset use has no touch input to reset the screen-off timer.
        setKeepScreenOn(true)
        applySettings(VrSession.settings.value)
    }

    fun applySettings(value: VrSettings) {
        theaterRenderer.applySettings(value)
        val wantStereo = value.viewerMode == VrViewerMode.CARDBOARD
        Log.d(
            TAG,
            "applySettings: viewerMode=${value.viewerMode} wantStereo=$wantStereo " +
                "wasStereo=$isStereoRenderModeEnabled showStatus=${value.showStatus}",
        )
        if (isStereoRenderModeEnabled != wantStereo) setStereoRenderMode(wantStereo)
        hideTouchOverlay()
    }

    /** Hides the SDK's non-SurfaceView touch overlay (gamepad-only project). */
    private fun hideTouchOverlay() {
        var hidden = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child !is SurfaceView) {
                child.visibility = View.GONE
                hidden++
            }
        }
        Log.d(TAG, "hideTouchOverlay: childCount=$childCount hiddenNonSurface=$hidden")
    }

    fun start() {
        Log.d(TAG, "start: onResume()")
        onResume()
    }

    fun stop() {
        Log.d(TAG, "stop: onPause()")
        onPause()
    }

    fun release() {
        Log.d(TAG, "release: onDestroy()")
        onDestroy()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "onAttachedToWindow: childCount=$childCount width=$width height=$height")
        // setStereoRenderMode(true) re-shows the SDK overlay, so re-hide it on every attach.
        hideTouchOverlay()
    }

    override fun onDetachedFromWindow() {
        Log.d(TAG, "onDetachedFromWindow")
        VrVideoSurfaceRegistry.clear()
        super.onDetachedFromWindow()
    }
}
