package com.opencloudgaming.opennow.vr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
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
 *  - Without a stream: a 2D Canvas panel (GL_TEXTURE_2D) shows status and gamepad hints, so
 *    entering VR is never a blank screen.
 *  - When the gamepad opens the settings menu (SELECT/START), the SAME quad shows a Canvas-drawn
 *    two-column menu driven exclusively by VrGamepadRouter commands. It is not a persistent
 *    overlay: it appears only while the user holds it open and disappears on B / SELECT.
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
 * LOW-END PERFORMANCE BUDGET (OPPO F9 / Helio P60 / Mali-G72 MP3; see HANDOFF.md section 9)
 *  - Zero heap allocation per NORMAL frame: reused matrices (multiplyInto / scaledRotationInto),
 *    reused direct FloatBuffers, reused Bitmap/Canvas/Paints, no string formatting in the hot
 *    path. The Cardboard SDK's Eye matrices are already preallocated and returned by reference.
 *  - Diagnostic logging (added to locate "VR shows nothing"): lifecycle events and state edges
 *    log once; the onDrawEye heartbeat logs only once every LOG_INTERVAL_FRAMES (120) frames.
 *    That single throttled line is the only per-frame-string cost and is dev-only; it does not
 *    affect steady-state throughput and can be silenced with logcat filters.
 *  - Flat screen => 4 vertices instead of 50 (24-segment strip only when curvature > 0).
 *  - Only ONE textured quad is drawn per eye, including while the menu is open.
 *  - Panel is redrawn only when its content actually changed (menu navigation / settings) and
 *    uploaded with texSubImage2D, so no texture is reallocated.
 *  - No blending, no depth test, no MSAA, no mipmaps, GL_DITHER disabled, LINEAR filtering only.
 *  - Brightness is a single multiply in the fragment shader, not an extra render pass.
 *  - The external-OES program is optional: if a driver cannot compile it, initialisation falls
 *    back to the 2D panel instead of crashing the GL thread.
 *  - The frame-available callback only flips an AtomicBoolean; it is not posted to the main
 *    thread, so 60 fps decoding does not enqueue 60 main-loop messages per second.
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
    private var forcePanelUpload = true

    private var panelBitmap: Bitmap? = null
    private var panelCanvas: Canvas? = null
    private var borderPaint: Paint? = null
    private var panelBackgroundPaint: Paint? = null
    private var titlePaint: Paint? = null
    private var bodyPaint: Paint? = null
    private var valuePaint: Paint? = null
    private var hintPaint: Paint? = null
    private var selectedPaint: Paint? = null
    private var selectedRect: RectF? = null

    @Volatile private var settings: VrSettings = VrSession.settings.value
    @Volatile private var vertexCount = 0
    @Volatile private var meshDirty = true
    private var lastAspect: Float = VrSession.videoAspect
    private var lastMenuOpen = false
    private var lastSelectedIndex = -1

    // Diagnostic-only logging state. All access is on the single GL thread, so plain fields are OK.
    // Per-frame logging is throttled to one line every LOG_INTERVAL_FRAMES; key state changes
    // (shader link, first video frame, useVideo flips) are logged once at the edge.
    private var logFrameCounter = 0
    private var logLastUseVideo: Boolean? = null
    private var logLastHandlesNull = false
    private var logLastStatusSkip = false
    private var logFirstFrameAvailable = false
    private var logSurfaceCreated = false

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
        // Geometry buffers are only touched on the GL thread (see onDrawEye); applySettings runs
        // on the main thread, so it just raises a flag instead of rebuilding in place.
        if (geometryChanged) meshDirty = true
        if (panelChanged) panelDirty = true
    }

    override fun onSurfaceCreated(config: EGLConfig) {
        logSurfaceCreated = true
        Log.d(TAG, "onSurfaceCreated: GL thread surfaced")
        // The SDK clears with the current clear colour once per frame; this makes the background
        // pure black with no extra full-screen clear of our own.
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_DITHER)

        // Shader compilation happens at runtime, not in CI. Neither failure may crash VR entry.
        panel = runCatching { createHandles(PANEL_FRAGMENT_SHADER) }.getOrElse {
            Log.e(TAG, "PANEL shader link FAILED", it)
            null
        }
        video = runCatching { createHandles(VIDEO_FRAGMENT_SHADER) }.getOrElse {
            Log.e(TAG, "VIDEO (external-OES) shader link FAILED — falling back to 2D panel", it)
            null
        }
        Log.d(TAG, "onSurfaceCreated: panelProgram=${panel != null} videoProgram=${video != null}")
        // If external-OES is unavailable on an old GLES2 driver, leave the decoder on the ordinary
        // 2D surface and keep showing the 2D panel instead of crashing the GL thread.

        panelTextureId = createPanelResources()
        setupVideoTexture()
        rebuildMesh()
        meshDirty = false
        // Draw the panel once up front so the very first frame is never an uninitialised texture.
        uploadPanel()
        Log.d(
            TAG,
            "onSurfaceCreated done: panelTextureId=$panelTextureId videoTextureId=$videoTextureId " +
                "vertexCount=$vertexCount",
        )
    }

    override fun onNewFrame(headTransform: HeadTransform) {
        headTransform.getQuaternion(headQuaternion, 0)
        if (VrVideoSurfaceRegistry.consumeFrameAvailable()) {
            if (!logFirstFrameAvailable) {
                logFirstFrameAvailable = true
                Log.d(TAG, "onNewFrame: FIRST video frame available from decoder")
            }
            val texture = videoSurfaceTexture
            if (texture != null) {
                try {
                    // updateTexImage() must run on the GL thread that created the SurfaceTexture.
                    texture.updateTexImage()
                    // SurfaceTexture.getTransformMatrix takes ONE argument on Android.
                    texture.getTransformMatrix(stMatrix)
                    hasVideoFrame = true
                } catch (e: RuntimeException) {
                    Log.w(TAG, "onNewFrame: updateTexImage() threw", e)
                    hasVideoFrame = false
                }
            } else {
                Log.w(TAG, "onNewFrame: frame available but videoSurfaceTexture is null")
            }
        }
    }

    override fun onDrawEye(eye: CardboardView.Eye) {
        // Deliberately NO glClear here - see the class KDoc.
        if (!logSurfaceCreated) {
            Log.w(TAG, "onDrawEye: called BEFORE onSurfaceCreated — renderer not initialised")
        }
        val current = settings
        val menuOpen = VrSession.menuOpen.value
        val selectedIndex = VrSession.selectedIndex.value
        if (menuOpen != lastMenuOpen) {
            lastMenuOpen = menuOpen
            lastSelectedIndex = selectedIndex
            meshDirty = true
            forcePanelUpload = true
            panelDirty = true
        } else if (menuOpen && selectedIndex != lastSelectedIndex) {
            lastSelectedIndex = selectedIndex
            forcePanelUpload = true
            panelDirty = true
        }

        val aspect = VrSession.videoAspect
        if (!menuOpen && aspect != lastAspect) {
            lastAspect = aspect
            meshDirty = true
        }
        if (meshDirty) {
            rebuildMesh()
            meshDirty = false
        }

        // While the menu is open it owns the quad; decoding keeps running underneath so closing
        // the menu returns to a live picture with no reconnection.
        val useVideo = !menuOpen && hasVideoFrame && video != null && videoTextureId != 0
        if (logLastUseVideo != useVideo) {
            Log.d(
                TAG,
                "onDrawEye: useVideo=$useVideo (menuOpen=$menuOpen hasVideoFrame=$hasVideoFrame " +
                    "videoProgram=${video != null} videoTextureId=$videoTextureId)",
            )
            logLastUseVideo = useVideo
        }

        if (!useVideo && (panelDirty || forcePanelUpload)) {
            uploadPanel()
            forcePanelUpload = false
        }

        // The three original early-returns are preserved in their original order; each also logs
        // the exact reason the eye draws NOTHING, but only at the state edge so a healthy render
        // loop stays log-silent.
        if (!useVideo && !menuOpen && !current.showStatus) {
            if (!logLastStatusSkip) {
                logLastStatusSkip = true
                Log.d(TAG, "onDrawEye: SKIP — panel suppressed by showStatus=false; nothing drawn")
            }
            return
        }
        logLastStatusSkip = false

        val handles = (if (useVideo) video else panel)
        if (handles == null) {
            if (!logLastHandlesNull) {
                logLastHandlesNull = true
                Log.w(TAG, "onDrawEye: SKIP — program handles null (useVideo=$useVideo); nothing drawn")
            }
            return
        }
        logLastHandlesNull = false
        if (vertexCount == 0) {
            Log.w(TAG, "onDrawEye: SKIP — vertexCount==0; nothing drawn")
            return
        }

        // A throttled heartbeat: proves onDrawEye is being called and reaching glDrawArrays,
        // without flooding logcat on a low-end device.
        if (logFrameCounter++ % LOG_INTERVAL_FRAMES == 0) {
            Log.d(
                TAG,
                "onDrawEye: draw frame#=$logFrameCounter useVideo=$useVideo vertexCount=$vertexCount " +
                    "showStatus=${current.showStatus} panelTextureId=$panelTextureId",
            )
        }

        // Use the INVERSE head rotation for the view matrix, matching the SDK's
        // HeadTransform.getHeadView() / Eye.applyHeadView(). The forward rotation would make the
        // screen follow the head (no spatial depth); the inverse keeps it anchored in the world.
        val head = if (current.headTrackingEnabled) {
            VrHeadPose.viewRotationInto(headRotation, headQuaternion, current.headTrackingSensitivity)
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
        // Apply horizontal mirror if setting is enabled.
        // Negating stMatrix[0] (x-scale) mirrors the texture left-right.
        // This is a pure Kotlin-side change — no GLSL shader modification required.
        if (s.horizontalMirror) {
            stMatrix[0] = -stMatrix[0]
        }
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
        Log.d(TAG, "onRendererShutdown: releasing GL resources")
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
        panelBackgroundPaint = null
        titlePaint = null
        bodyPaint = null
        valuePaint = null
        hintPaint = null
        selectedPaint = null
        selectedRect = null
        vertexCount = 0
        hasVideoFrame = false
        panelDirty = true
        forcePanelUpload = true
        meshDirty = true
        lastMenuOpen = false
        lastSelectedIndex = -1
        // Reset diagnostic state so a second VR entry logs its first frame / first draw again.
        logFrameCounter = 0
        logLastUseVideo = null
        logLastHandlesNull = false
        logFirstFrameAvailable = false
        logSurfaceCreated = false
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
        panelBackgroundPaint = Paint().apply { color = Color.rgb(8, 10, 14) }
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
        valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 165, 195)
            textSize = 15f
        }
        selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 92, 138)
        }
        selectedRect = RectF()
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
        // Only flips an AtomicBoolean. Do NOT post to the main looper: that would enqueue one
        // message per decoded frame on the UI thread of a low-end device.
        texture.setOnFrameAvailableListener { VrVideoSurfaceRegistry.markFrameAvailable() }
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

    private fun uploadPanel() {
        val canvas = panelCanvas ?: return
        val bitmap = panelBitmap ?: return
        if (VrSession.menuOpen.value) drawMenu(canvas, settings) else drawStatus(canvas, settings)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panelTextureId)
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        panelDirty = false
    }

    private fun drawStatus(canvas: Canvas, s: VrSettings) {
        val border = borderPaint ?: return
        val title = titlePaint ?: return
        val body = bodyPaint ?: return
        val hint = hintPaint ?: return
        canvas.drawPaint(panelBackgroundPaint ?: return)
        canvas.drawRect(2f, 2f, PANEL_W - 2f, PANEL_H - 2f, border)
        canvas.drawText("OpenNOW VR", 28f, 56f, title)
        canvas.drawText("2D 平面影院 · 纯黑背景", 28f, 96f, body)
        canvas.drawText("串流开始后，画面会显示在这块平面幕布上", 28f, 132f, body)
        canvas.drawText(
            String.format(
                Locale.US,
                "窗口 %.1fx    距离 %.1f m    亮度 %.2f",
                s.screenScale, s.screenDistanceMeters, s.brightness,
            ),
            28f, 236f, body,
        )
        canvas.drawText(
            String.format(Locale.US, "曲面 %.1f    偏移 %.1f    模式 %s", s.curvature, s.screenVerticalOffset,
                if (s.viewerMode == VrViewerMode.CARDBOARD) "纸盒立体" else "裸眼单屏"),
            28f, 274f, body,
        )
        canvas.drawText("手柄 SELECT / START：打开设置", 28f, 344f, hint)
        canvas.drawText("系统返回键或 L1+R1：退出 VR（串流不中断）", 28f, 370f, hint)
        canvas.drawText("不使用眼睛 / 凝视 / 头部确认", 28f, 400f, hint)

        // Draw center alignment line (single vertical line at panel center, from bottom to middle)
        if (s.showCenterAlignmentLine) {
            val linePaint = Paint().apply {
                color = Color.rgb(120, 165, 195)
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            val centerX = PANEL_W / 2f
            val bottomY = PANEL_H - 2f
            val middleY = PANEL_H / 2f
            canvas.drawLine(centerX, bottomY, centerX, middleY, linePaint)
        }
    }

    private fun drawMenu(canvas: Canvas, s: VrSettings) {
        val border = borderPaint ?: return
        val title = titlePaint ?: return
        val label = bodyPaint ?: return
        val value = valuePaint ?: return
        val hint = hintPaint ?: return
        val highlight = selectedPaint ?: return
        val rect = selectedRect ?: return
        canvas.drawPaint(panelBackgroundPaint ?: return)
        canvas.drawRect(2f, 2f, PANEL_W - 2f, PANEL_H - 2f, border)
        canvas.drawText("VR 设置", 26f, 50f, title)
        canvas.drawText("B 关闭", PANEL_W - 26f - hint.measureText("B 关闭"), 48f, hint)

        val selected = VrSession.selectedIndex.value
        VrMenuModel.items.forEachIndexed { index, item ->
            val column = if (index < MENU_ROWS_PER_COLUMN) 0 else 1
            val row = index % MENU_ROWS_PER_COLUMN
            val left = if (column == 0) 24f else 396f
            val baseline = 102f + row * MENU_ROW_HEIGHT
            if (index == selected) {
                rect.set(left - 4f, baseline - 28f, left + 336f, baseline + 9f)
                canvas.drawRoundRect(rect, 6f, 6f, highlight)
            }
            val textColor = if (index == selected) Color.WHITE else Color.rgb(205, 218, 232)
            label.color = textColor
            value.color = textColor
            val marker = if (index == selected) "▶ " else "   "
            canvas.drawText(marker + item.label, left + 4f, baseline, label)
            val display = VrMenuModel.display(s, item.id)
            canvas.drawText(display, left + 332f - value.measureText(display), baseline, value)
        }
        label.color = Color.rgb(205, 218, 232)
        value.color = Color.WHITE
        canvas.drawText("↑↓ 选择    ←→ 调整（按住 L1/R1 ×5）    A 确认", 26f, 408f, hint)
    }

    private fun rebuildMesh() {
        val s = settings
        // A perfectly flat plane needs 4 vertices. The 24-segment strip is only meaningful when
        // the user enables curvature, so low-end GPUs draw 4 vertices instead of 50 by default.
        val segments = if (s.curvature <= 0.001f) 1 else VrScreenGeometry.DEFAULT_SEGMENTS
        // The menu is a fixed 16:9 board; the stream quad follows the real decoded aspect.
        val aspect = if (VrSession.menuOpen.value) MENU_ASPECT else VrSession.videoAspect
        val mesh = VrScreenGeometry.build(s, aspect, segments)
        positionBuffer.clear()
        positionBuffer.put(mesh.positions)
        positionBuffer.position(0)
        texCoordBuffer.clear()
        texCoordBuffer.put(mesh.texCoords)
        texCoordBuffer.position(0)
        vertexCount = mesh.vertexCount
    }

    private companion object {
        const val TAG = "VrTheaterRenderer"
        /** One heartbeat line per this many onDrawEye calls (both eyes share one counter). */
        const val LOG_INTERVAL_FRAMES = 120

        const val PANEL_W = 768
        const val PANEL_H = 432
        const val MENU_ASPECT = 16f / 9f
        const val MENU_ROWS_PER_COLUMN = 7
        const val MENU_ROW_HEIGHT = 44f

        /**
         * VrScreenGeometry emits top v=0 / bottom v=1. GLUtils uploads the bitmap's first row at
         * t=0, so the quad's top vertex (v=0) already samples the top of the panel: no flip needed.
         * If a device reports upside-down text, change this single constant to 1.0f.
         */
        const val PANEL_FLIP_Y = 1.0f

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
