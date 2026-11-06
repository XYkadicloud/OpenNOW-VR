package com.opencloudgaming.opennow.vr

import android.graphics.SurfaceTexture
import android.view.Surface
import com.opencloudgaming.opennow.DecoderSurfaceTarget
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridge between the streaming decoder and the VR GL thread.
 *
 * Streaming.kt asks for a decoder Surface on every frame:
 *   decoderSurface = { VrVideoSurfaceRegistry.target() ?: renderer?.decoderTarget }
 * MediaCodecSurfaceVideoDecoder detects a changed target and reconfigures the codec, so VR mode
 * can be toggled mid-stream without restarting the cloud session.
 *
 * Lifecycle rule: install()/clear() may be called from any thread, but updateTexImage() and
 * getTransformMatrix() MUST be called on the GL thread that created the SurfaceTexture.
 */
object VrVideoSurfaceRegistry {
    @Volatile private var target: DecoderSurfaceTarget? = null
    @Volatile private var surfaceTexture: SurfaceTexture? = null
    @Volatile private var surface: Surface? = null
    @Volatile private var textureId: Int = 0

    private val frameAvailable = AtomicBoolean(false)

    val isActive: Boolean get() = target != null

    fun target(): DecoderSurfaceTarget? = target

    fun currentTexture(): SurfaceTexture? = surfaceTexture

    fun currentTextureId(): Int = textureId

    /** Called by the SurfaceTexture listener (decoder thread). */
    fun markFrameAvailable() {
        frameAvailable.set(true)
    }

    /** Returns true at most once per newly available frame. */
    fun consumeFrameAvailable(): Boolean = frameAvailable.compareAndSet(true, false)

    /** Called from VrTheaterRenderer.onSurfaceCreated (GL thread). */
    fun install(id: Int, texture: SurfaceTexture, output: Surface) {
        clear()
        textureId = id
        surfaceTexture = texture
        surface = output
        target = DecoderSurfaceTarget(output)
    }

    /**
     * Detaches the decoder target. The GL thread owns the actual SurfaceTexture release; clearing
     * here only drops references so the decoder immediately falls back to the normal 2D surface.
     * TODO(P1): also release the Surface and delete the GL texture in onRendererShutdown.
     */
    fun clear() {
        target = null
        surface = null
        surfaceTexture = null
        textureId = 0
        frameAvailable.set(false)
    }
}
