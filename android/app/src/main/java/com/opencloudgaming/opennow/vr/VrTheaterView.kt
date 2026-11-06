package com.opencloudgaming.opennow.vr

import android.content.Context
import com.google.cardboard.sdk.CardboardView

/**
 * Google Cardboard SDK view hosted inside MainActivity (NOT a separate Activity).
 * Keeping it in the same Activity preserves the existing NativeStreamInputRouter gamepad path.
 */
class VrTheaterView(context: Context) : CardboardView(context) {
    private val theaterRenderer = VrTheaterRenderer()

    init {
        setRenderer(theaterRenderer)
        applySettings(VrSession.settings.value)
        // TODO(P3): hide the SDK's CardboardLayout touch overlay (settings/back buttons) so the
        // in-VR UI stays purely gamepad-driven. setStereoRenderMode(true) calls showAll() on it.
    }

    fun applySettings(value: VrSettings) {
        theaterRenderer.applySettings(value)
        val wantStereo = value.viewerMode == VrViewerMode.CARDBOARD
        if (isStereoRenderModeEnabled != wantStereo) setStereoRenderMode(wantStereo)
    }

    fun start() = onResume()
    fun stop() = onPause()
    fun release() = onDestroy()

    override fun onDetachedFromWindow() {
        VrVideoSurfaceRegistry.clear()
        super.onDetachedFromWindow()
    }
}
