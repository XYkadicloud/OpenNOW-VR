package com.opencloudgaming.opennow.vr

import android.opengl.GLES20
import com.google.cardboard.sdk.CardboardView
import com.google.cardboard.sdk.HeadTransform
import com.google.cardboard.sdk.Viewport
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig

/**
 * Renders the "half VR" theatre using Google Cardboard SDK's CardboardView.Renderer.
 *
 * CURRENT STATE (framework skeleton):
 *  - Draws the flat 2D stream screen geometry as a solid placeholder quad, once per eye.
 *  - Uses real Cardboard eye matrices + adjustable head-pose sensitivity.
 *  - All adjustable settings already affect the geometry / colour / view.
 *
 * TODO(P1): create a GL_TEXTURE_EXTERNAL_OES SurfaceTexture in onSurfaceCreated(), register it
 *           with VrVideoSurfaceRegistry, and sample it with samplerExternalOES + uSTMatrix.
 * TODO(P3): draw the gamepad-driven settings panel as a Canvas Bitmap texture in front of the
 *           screen. No gaze / eye / dwell selection — see docs/VR_IMPLEMENTATION_PLAN.md.
 *
 * The SDK already handles head tracking, lens distortion, FBO and per-eye viewports; do not
 * duplicate any of that here.
 */
class VrTheaterRenderer : CardboardView.Renderer {

    private var program = 0
    private var positionHandle = 0
    private var mvpHandle = 0
    private var colorHandle = 0

    @Volatile private var meshBuffer: FloatBuffer? = null
    @Volatile private var vertexCount = 0
    @Volatile private var settings: VrSettings = VrSession.settings.value

    private val headQuaternion = FloatArray(4).also { it[3] = 1f }
    private val stMatrix = FloatArray(16)

    fun applySettings(value: VrSettings) {
        settings = value.sanitized()
        rebuildMesh()
    }

    override fun onSurfaceCreated(config: EGLConfig) {
        GLES20.glClearColor(0.02f, 0.03f, 0.05f, 1f)
        program = VrGlProgram.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        rebuildMesh()

        // TODO(P1): install the decoder SurfaceTexture here, on this GL thread:
        //   val tex = IntArray(1); GLES20.glGenTextures(1, tex, 0)
        //   GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        //   ... LINEAR / CLAMP_TO_EDGE ...
        //   val st = SurfaceTexture(tex[0])
        //   st.setOnFrameAvailableListener { VrVideoSurfaceRegistry.markFrameAvailable() }
        //   VrVideoSurfaceRegistry.install(tex[0], st, Surface(st))
    }

    override fun onNewFrame(headTransform: HeadTransform) {
        headTransform.getQuaternion(headQuaternion, 0)
        if (VrVideoSurfaceRegistry.consumeFrameAvailable()) {
            // TODO(P1): these calls are already on the correct GL thread; the texture is null
            // until install() is implemented above, so this is currently a safe no-op.
            VrVideoSurfaceRegistry.currentTexture()?.let { texture ->
                texture.updateTexImage()
                // SurfaceTexture.getTransformMatrix(float[]) takes ONE argument on Android.
                // The two-argument form does not exist and would fail compilation.
                texture.getTransformMatrix(stMatrix)
            }
        }
    }

    override fun onDrawEye(eye: CardboardView.Eye) {
        val current = settings
        val buffer = meshBuffer ?: return

        val headRotation = if (current.headTrackingEnabled) {
            VrHeadPose.scaledRotation(headQuaternion, current.headTrackingSensitivity)
        } else {
            VrMatrix4.identity()
        }
        // eyeFromHead * headRotation is equivalent to Eye.getEyeView(), but lets us apply
        // sensitivity and the on/off switch. See Eye.applyHeadView() in the SDK.
        val view = VrMatrix4.multiply(eye.getEyeFromHead(), headRotation)
        val mvp = VrMatrix4.multiply(eye.getPerspective(0.1f, 100f), view)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform4f(
            colorHandle,
            0.05f * current.brightness,
            0.13f * current.brightness,
            0.24f * current.brightness,
            1f,
        )
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(positionHandle)

        // TODO(P3): draw the VR settings panel quad here (GL_TEXTURE_2D + alpha blending).
    }

    override fun onFinishFrame(viewport: Viewport) = Unit

    override fun onSurfaceChanged(width: Int, height: Int) = Unit

    override fun onRendererShutdown() {
        VrVideoSurfaceRegistry.clear()
        if (program != 0) GLES20.glDeleteProgram(program)
        program = 0
        meshBuffer = null
    }

    private fun rebuildMesh() {
        val mesh = VrScreenGeometry.build(settings, VrSession.videoAspect)
        meshBuffer = ByteBuffer.allocateDirect(mesh.positions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(mesh.positions); position(0) }
        vertexCount = mesh.vertexCount
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            uniform mat4 uMVP;
            void main() { gl_Position = uMVP * aPosition; }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() { gl_FragColor = uColor; }
        """
    }
}
