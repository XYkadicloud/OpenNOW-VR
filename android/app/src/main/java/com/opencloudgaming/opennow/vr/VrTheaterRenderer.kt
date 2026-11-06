package com.opencloudgaming.opennow.vr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import com.google.cardboard.sdk.CardboardView
import com.google.cardboard.sdk.HeadTransform
import com.google.cardboard.sdk.Viewport
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig

/**
 * Renders the "half VR" theatre on CardboardView.Renderer.
 *
 * WHAT IS ON SCREEN
 *  - Background: pure black (set via glClearColor; the SDK performs the actual clear).
 *  - Exactly ONE flat 2D quad in front of the viewer. No 3D scene, no geometry, no skybox.
 *  - With a stream: the decoder SurfaceTexture (GL_TEXTURE_EXTERNAL_OES) is the quad's texture.
 *    Zero-copy: the video never passes through a Bitmap or a second render target.
 *  - Without a stream: a small 2D Canvas panel (GL_TEXTURE_2D) shows status, current settings and
 *    gamepad hints, so entering VR is never a blank screen.
 *  - Both eyes sample the SAME quad and the SAME texture. This is a virtual cinema screen, NOT
 *    stereoscopic 3D video. No SBS, no parallax, no per-eye content.
 *
 * CORRECTNESS NOTE (do not "simplify" this away)
 *  CardboardView.onRenderStereoFrame() binds ONE shared FBO, clears it once, then separates the
 *  eyes only with glViewport() - there is NO scissor test. Calling glClear() inside onDrawEye()
 *  would therefore erase the left eye when the right eye is drawn. The black background is
 *  produced by glClearColor() in onSurfaceCreated() plus the SDK's own clear. Do NOT add a clear
 *  to onDrawEye().
 *
 * LOW-END PERFORMANCE BUDGET (see docs/VR_IMPLEMENTATION_PLAN.md section 13)
 *  - Zero heap allocation per frame: reused matrices (multiplyInto / scaledRotationInto), reused
 *    direct FloatBuffers, reused Bitmap/Canvas/Paints, no string formatting or logging in onDrawEye.
 *    The Cardboard SDK's Eye matrices are already preallocated and returned by reference.
 *  - Flat screen => 4 vertices instead of 50 (24-segment strip only when curvature > 0).
 *  - Panel is redrawn at most once per PANEL_THROTTLE_MS and only when settings actually changed.
 *  - No blending, no depth test, no MSAA, no mipmaps, GL_DITHER disabled, LINEAR filtering only.
 *  - Brightness is a single multiply in the fragment shader, not an extra render pass.
 *  - The external-OES program is optional: if a driver cannot compile it, initialisation falls
 *    back to the 2D panel instead of crashing the GL thread.
 *
 * All in-VR control remains physical gamepad input. No gaze / eye / dwell / reticle logic exists.
 */
class VrTheaterRenderer : CardboardView.Renderer {

    private class ProgramHandles(
        val program: Int,
        val position: Int,
        val texCoord: Int,
        val mvp: Int,
        val texture: Int,
        val brightness: Int,
        val stMatrix: Int,
        val flip: Int,
    )

    private var panel: ProgramHandles? = null
    private var video: ProgramHandles? = null

    private var panelTextureId = 0
    private var videoTextureId = 0
    private var videoSurfaceTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null

    @Volatile private var hasVideoFrame = false
    @Volatile private var panelDirty = true
    private var lastPanelUploadMs = 0L

    private var panelBitmap: Bitmap? = null
    private var panelCanvas: Canvas? = null
    private var borderPaint: Paint? = null
    private var titlePaint: Paint? = null
    private var bodyPaint: Paint? = null
    private var hintPaint: Paint? = null

    @Volatile private var settings: VrSettings = VrSession.settings.value
    @Volatile private var vertexCount = 0
    private var lastAspect: Float = VrSession.videoAspect

    // Preallocated render-loop state. Nothing below is allocated inside onDrawEye().
    private val headQuaternion = FloatArray(4).also { it[3] = 1f }
    private val stMatrix = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }
    private val identityMatrix = VrMatrix4.identity()
    private val headRotation = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private val positionBuffer: FloatBuffer = newFloatBuffer(MAX_VERTICES * 3)
    private val texCoordBuffer: FloatBuffer = newFloatBuffer(MAX_VERTICES * 2)

    fun applySettings(value: VrSettings) {
        val next = value.sanitized()
        val geometryChanged = next.screenScale != settings.screenScale ||
            next.screenDistanceMeters != settings.screenDistanceMeters ||
            next.screenVerticalOffset != settings.screenVerticalOffset ||
            next.curvature != settings.curvature
        val panelChanged = next != settings
        settings = next
        if (geometryChanged) rebuildMesh()
        if (panelChanged) panelDirty = true
    }

    override fun onSurfaceCreated(config: EGLConfig) {
        // The SDK clears with the current clear colour once per frame; this makes the background
        // pure black with no extra full-screen clear of our own.
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_DITHER)

        // Shader compilation happens at runtime, not in CI. Neither failure may crash VR entry.
        panel = runCatching { createHandles(PANEL_FRAGMENT_SHADER) }.getOrNull()
        video = runCatching { createHandles(VIDEO_FRAGMENT_SHADER) }.getOrNull()
        // If external-OES is unavailable on an old GLES2 driver, leave the decoder on the ordinary
        // 2D surface and keep showing the 2D panel instead of crashing the GL thread.

        panelTextureId = createPanelResources()
        setupVideoTexture()
        rebuildMesh()
        // Draw the panel once up front so the very first frame is never an uninitialised texture.
        uploadPanel(SystemClock.uptimeMillis())
    }

    override fun onNewFrame(headTransform: HeadTransform) {
        headTransform.getQuaternion(headQuaternion, 0)
        if (VrVideoSurfaceRegistry.consumeFrameAvailable()) {
            val texture = videoSurfaceTexture
            if (texture != null) {
                try {
                    // updateTexImage() must run on the GL thread that created the SurfaceTexture.
                    texture.updateTexImage()
                    // SurfaceTexture.getTransformMatrix takes ONE argument on Android.
                    texture.getTransformMatrix(stMatrix)
                    hasVideoFrame = true
                } catch (e: RuntimeException) {
                    hasVideoFrame = false
                }
            }
        }
    }

    override fun onDrawEye(eye: CardboardView.Eye) {
        // Deliberately NO glClear here - see the class KDoc.
        val current = settings
        val aspect = VrSession.videoAspect
        if (aspect != lastAspect) {
            lastAspect = aspect
            rebuildMesh()
        }

        val useVideo = hasVideoFrame && video != null && videoTextureId != 0
        if (!useVideo && panelDirty) maybeUploadPanel()

        val handles = (if (useVideo) video else panel) ?: return
        if (vertexCount == 0) return

        val head = if (current.headTrackingEnabled) {
            VrHeadPose.scaledRotationInto(headRotation, headQuaternion, current.headTrackingSensitivity)
        } else {
            identityMatrix
        }
        VrMatrix4.multiplyInto(viewMatrix, eye.getEyeFromHead(), head)
        VrMatrix4.multiplyInto(mvpMatrix, eye.getPerspective(0.1f, 100f), viewMatrix)

        GLES20.glUseProgram(handles.program)
        GLES20.glUniformMatrix4fv(handles.mvp, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(handles.brightness, current.brightness)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(handles.texture, 0)

        if (useVideo) {
            GLES20.glUniformMatrix4fv(handles.stMatrix, 1, false, stMatrix, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        } else {
            GLES20.glUniform1f(handles.flip, PANEL_FLIP_Y)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panelTextureId)
        }

        positionBuffer.position(0)
        GLES20.glEnableVertexAttribArray(handles.position)
        GLES20.glVertexAttribPointer(handles.position, 3, GLES20.GL_FLOAT, false, 12, positionBuffer)
        texCoordBuffer.position(0)
        GLES20.glEnableVertexAttribArray(handles.texCoord)
        GLES20.glVertexAttribPointer(handles.texCoord, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(handles.position)
        GLES20.glDisableVertexAttribArray(handles.texCoord)
    }

    override fun onFinishFrame(viewport: Viewport) = Unit

    override fun onSurfaceChanged(width: Int, height: Int) = Unit

    override fun onRendererShutdown() {
        VrVideoSurfaceRegistry.clear()
        releaseVideoTexture()
        if (panelTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(panelTextureId), 0)
            panelTextureId = 0
        }
        panel?.let { GLES20.glDeleteProgram(it.program) }
        video?.let { GLES20.glDeleteProgram(it.program) }
        panel = null
        video = null
        panelBitmap?.recycle()
        panelBitmap = null
        panelCanvas = null
        borderPaint = null
        titlePaint = null
        bodyPaint = null
        hintPaint = null
        vertexCount = 0
        hasVideoFrame = false
        panelDirty = true
    }

    private fun createHandles(fragmentSource: String): ProgramHandles {
        val program = VrGlProgram.createProgram(VERTEX_SHADER, fragmentSource)
        return ProgramHandles(
            program = program,
            position = GLES20.glGetAttribLocation(program, "aPosition"),
            texCoord = GLES20.glGetAttribLocation(program, "aTexCoord"),
            mvp = GLES20.glGetUniformLocation(program, "uMVP"),
            texture = GLES20.glGetUniformLocation(program, "uTexture"),
            brightness = GLES20.glGetUniformLocation(program, "uBrightness"),
            stMatrix = GLES20.glGetUniformLocation(program, "uSTMatrix"),
            flip = GLES20.glGetUniformLocation(program, "uFlip"),
        )
    }

    private fun createPanelResources(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val bitmap = Bitmap.createBitmap(PANEL_W, PANEL_H, Bitmap.Config.ARGB_8888)
        panelBitmap = bitmap
        panelCanvas = Canvas(bitmap)
        borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.rgb(90, 200, 235)
        }
        titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(205, 218, 232)
            textSize = 19f
        }
        hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 165, 195)
            textSize = 16f
        }
        // Allocate texture storage once. Later refreshes use texSubImage2D (no reallocation).
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return ids[0]
    }

    private fun setupVideoTexture() {
        releaseVideoTexture()
        if (video == null) {
            // Without external-OES support the decoder must stay on the ordinary 2D surface.
            VrVideoSurfaceRegistry.clear()
            return
        }
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        videoTextureId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val texture = SurfaceTexture(videoTextureId)
        texture.setOnFrameAvailableListener(
            { VrVideoSurfaceRegistry.markFrameAvailable() },
            Handler(Looper.getMainLooper()),
        )
        val output = Surface(texture)
        videoSurfaceTexture = texture
        videoSurface = output
        VrVideoSurfaceRegistry.install(videoTextureId, texture, output)
    }

    private fun releaseVideoTexture() {
        videoSurface?.release()
        videoSurface = null
        videoSurfaceTexture?.release()
        videoSurfaceTexture = null
        if (videoTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(videoTextureId), 0)
            videoTextureId = 0
        }
        hasVideoFrame = false
    }

    private fun maybeUploadPanel() {
        val now = SystemClock.uptimeMillis()
        if (now - lastPanelUploadMs < PANEL_THROTTLE_MS) return
        uploadPanel(now)
    }

    private fun uploadPanel(now: Long) {
        val canvas = panelCanvas ?: return
        val bitmap = panelBitmap ?: return
        val border = borderPaint ?: return
        val title = titlePaint ?: return
        val body = bodyPaint ?: return
        val hint = hintPaint ?: return
        val s = settings
        canvas.drawColor(Color.rgb(8, 10, 14))
        canvas.drawRect(2f, 2f, PANEL_W - 2f, PANEL_H - 2f, border)
        canvas.drawText("OpenNOW VR", 24f, 52f, title)
        canvas.drawText("2D 平面影院 · 黑色背景", 24f, 92f, body)
        canvas.drawText("串流开始后，画面会显示在这块平面幕布上", 24f, 124f, body)
        canvas.drawText(
            String.format(
                Locale.US,
                "窗口 %.1fx    距离 %.1f m    亮度 %.2f",
                s.screenScale, s.screenDistanceMeters, s.brightness,
            ),
            24f, 160f, body,
        )
        canvas.drawText(
            String.format(Locale.US, "曲面 %.1f   偏移 %.1f   模式 %s", s.curvature, s.screenVerticalOffset,
                if (s.viewerMode == VrViewerMode.CARDBOARD) "Cardboard" else "单目省电"),
            24f, 192f, body,
        )
        canvas.drawText("手柄：方向键调整 · A 设置 · B 返回", 24f, 240f, hint)
        canvas.drawText("不使用眼睛 / 凝视 / 头部确认", 24f, 264f, hint)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panelTextureId)
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        lastPanelUploadMs = now
        panelDirty = false
    }

    private fun rebuildMesh() {
        val s = settings
        // A perfectly flat plane needs 4 vertices. The 24-segment strip is only meaningful when
        // the user enables curvature, so low-end GPUs draw 4 vertices instead of 50 by default.
        val segments = if (s.curvature <= 0.001f) 1 else VrScreenGeometry.DEFAULT_SEGMENTS
        val mesh = VrScreenGeometry.build(s, VrSession.videoAspect, segments)
        positionBuffer.clear()
        positionBuffer.put(mesh.positions)
        positionBuffer.position(0)
        texCoordBuffer.clear()
        texCoordBuffer.put(mesh.texCoords)
        texCoordBuffer.position(0)
        vertexCount = mesh.vertexCount
    }

    private companion object {
        const val PANEL_W = 512
        const val PANEL_H = 288
        const val PANEL_THROTTLE_MS = 200L

        /**
         * VrScreenGeometry emits top v=0 / bottom v=1. GLUtils uploads the bitmap's first row at
         * t=0, so the quad's top vertex (v=0) already samples the top of the panel: no flip needed.
         * If a device reports upside-down text, change this single constant to 1.0f.
         */
        const val PANEL_FLIP_Y = 0.0f

        const val MAX_VERTICES = (VrScreenGeometry.DEFAULT_SEGMENTS + 1) * 2

        fun newFloatBuffer(capacityFloats: Int): FloatBuffer =
            ByteBuffer.allocateDirect(capacityFloats * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uMVP;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVP * aPosition;
                vTexCoord = aTexCoord;
            }
        """

        const val PANEL_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform float uBrightness;
            uniform float uFlip;
            varying vec2 vTexCoord;
            void main() {
                float v = mix(vTexCoord.y, 1.0 - vTexCoord.y, uFlip);
                vec4 c = texture2D(uTexture, vec2(vTexCoord.x, v));
                gl_FragColor = vec4(c.rgb * uBrightness, 1.0);
            }
        """

        // #extension MUST be the first directive in a GLES ES 1.00 fragment shader, so it goes on
        // line 0 with no leading newline/indentation (const val cannot call trimIndent()).
        val VIDEO_FRAGMENT_SHADER = """#extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            uniform mat4 uSTMatrix;
            uniform float uBrightness;
            varying vec2 vTexCoord;
            void main() {
                vec2 uv = (uSTMatrix * vec4(vTexCoord.x, vTexCoord.y, 0.0, 1.0)).xy;
                vec4 c = texture2D(uTexture, uv);
                gl_FragColor = vec4(c.rgb * uBrightness, 1.0);
            }
            """.trimIndent()
    }
}
