package com.opencloudgaming.opennow

import org.webrtc.EncodedImage
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoDecoder

/**
 * Runtime switch between the direct Surface decoder and the EGL-texture decoder.
 *
 * Original upstream behaviour was one-way (surface -> texture) and only when the surface failed.
 * The VR theatre needs the opposite direction too: when CardboardView creates its SurfaceTexture
 * *after* the stream is already running, the decoder must switch from texture to surface without
 * reconnecting the cloud session.
 *
 * Rules kept from upstream:
 *  - A failed surface is never retried over and over inside one VR entry. `surfaceSwitchBlocked`
 *    is cleared as soon as surface output is no longer requested, so the next VR entry may retry.
 *  - No allocation and no logging on the decode path.
 */
internal class SurfaceOrTextureVideoDecoder(
    private val surfaceDecoder: VideoDecoder,
    private val textureDecoder: VideoDecoder,
    private val useSurface: () -> Boolean,
    private val surfaceFailure: () -> String?,
    private val requestTexture: (String) -> Unit,
) : VideoDecoder {
    private var settings: VideoDecoder.Settings? = null
    private var callback: VideoDecoder.Callback? = null
    private var active: VideoDecoder? = null
    private var usingTexture = false
    private var surfaceSwitchBlocked = false

    internal val canBorrowEncodedInput: Boolean
        get() = surfaceDecoder.supportsBorrowedNvstInput() && textureDecoder.supportsBorrowedNvstInput()

    override fun initDecode(settings: VideoDecoder.Settings?, decodeCallback: VideoDecoder.Callback?): VideoCodecStatus {
        if (settings == null || decodeCallback == null) return VideoCodecStatus.ERR_PARAMETER
        if (active != null) return VideoCodecStatus.ERROR
        this.settings = settings
        callback = decodeCallback
        usingTexture = !useSurface()
        surfaceSwitchBlocked = false
        active = if (usingTexture) textureDecoder else surfaceDecoder
        val result = active!!.initDecode(settings, decodeCallback)
        if (!usingTexture && result != VideoCodecStatus.OK) return switchToTexture("surface initialization failed")
        if (result != VideoCodecStatus.OK) {
            active?.release()
            active = null
        }
        return result
    }

    override fun decode(frame: EncodedImage?, info: VideoDecoder.DecodeInfo?): VideoCodecStatus {
        if (active == null) return VideoCodecStatus.UNINITIALIZED
        // Leaving surface mode (VR closed) re-arms the one-shot switch for the next VR entry.
        if (!useSurface()) surfaceSwitchBlocked = false

        if (usingTexture) {
            if (useSurface() && !surfaceSwitchBlocked) switchToSurface()
        } else {
            val initialFailure = surfaceFailure()
            if (!useSurface() || initialFailure != null) {
                val initialized = switchToTexture(initialFailure ?: "texture frames requested")
                if (initialized != VideoCodecStatus.OK) return initialized
            }
        }

        val current = active ?: return VideoCodecStatus.UNINITIALIZED
        val result = current.decode(frame, info)
        if (!usingTexture) {
            val decodeFailure = surfaceFailure()
            if (decodeFailure != null) {
                val initialized = switchToTexture(decodeFailure)
                return if (initialized == VideoCodecStatus.OK) textureDecoder.decode(frame, info) else initialized
            }
        }
        return result
    }

    /**
     * Switches an already initialized texture decoder to the surface decoder. The first frames
     * offered to the new MediaCodec may be delta frames; [MediaCodecSurfaceVideoDecoder] drops
     * them with NO_OUTPUT until the next keyframe, so WebRTC/NVST request a keyframe instead of
     * treating the session as broken.
     *
     * If surface initialization fails, the texture decoder is restarted so 2D playback keeps
     * working and the switch is not retried until surface output is released once.
     */
    private fun switchToSurface() {
        val released = active?.release()
        if (released != null && released != VideoCodecStatus.OK) {
            active = null
            surfaceSwitchBlocked = true
            return
        }
        active = surfaceDecoder
        val initialized = surfaceDecoder.initDecode(settings, callback)
        if (initialized == VideoCodecStatus.OK) {
            usingTexture = false
            surfaceSwitchBlocked = false
            return
        }
        surfaceDecoder.release()
        active = null
        val textureRestarted = textureDecoder.initDecode(settings, callback)
        if (textureRestarted == VideoCodecStatus.OK) {
            active = textureDecoder
            usingTexture = true
        } else {
            textureDecoder.release()
        }
        surfaceSwitchBlocked = true
    }

    private fun switchToTexture(reason: String): VideoCodecStatus {
        usingTexture = true
        surfaceSwitchBlocked = true
        requestTexture(reason)
        val released = active?.release()
        if (released != null && released != VideoCodecStatus.OK) {
            active = null
            return released
        }
        active = textureDecoder
        // The ordinary hardware texture decoder requests a keyframe after init. Its recovery
        // stays inside the current WebRTC/NVST session rather than reconnecting signaling.
        val result = textureDecoder.initDecode(settings, callback)
        if (result != VideoCodecStatus.OK) {
            textureDecoder.release()
            active = null
        }
        return result
    }

    override fun release(): VideoCodecStatus {
        val previous = active
        active = null
        settings = null
        callback = null
        return previous?.release() ?: VideoCodecStatus.OK
    }

    override fun getImplementationName(): String =
        active?.implementationName ?: "${surfaceDecoder.implementationName}+texture-fallback"
}
