package com.opencloudgaming.opennow.vr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat

/**
 * Owns the REAL phone camera used by the VR "透视 / Passthrough" window.
 *
 * Design constraints (all deliberate):
 *
 *  1. NO WebRTC / CameraX dependency. The app already has a WebRTC-based recorder camera
 *     (`RecordingCameraCapture`) that feeds the stream; reusing it would couple passthrough to the
 *     encoder pipeline and steal the only camera client. This class uses the framework Camera2 API
 *     directly and writes frames into a [Surface] backed by the renderer's own
 *     `SurfaceTexture` (GL_TEXTURE_EXTERNAL_OES) — the same zero-copy pattern as the video stream.
 *  2. All camera work happens on ONE private [HandlerThread]. `start()` / `stop()` / `release()`
 *     only post messages and never block the GL thread or the UI thread. Camera2 callbacks are
 *     delivered to that same handler, so the state machine has no cross-thread races.
 *  3. FAILURE IS NEVER FATAL. Missing CAMERA permission, no matching lens, a busy device (e.g. the
 *     stream is already recording from the camera), or any CameraAccessException is reported via
 *     [Listener.onUnavailable]; the renderer then simply keeps the original pure-black / 360°
 *     background. VR never black-screens or crashes because of passthrough.
 *  4. [generation] invalidates late asynchronous callbacks: after stop()/release(), an in-flight
 *     `onOpened` closes the device instead of opening a session on a dead surface.
 *
 * The camera is ALWAYS released when the VR host leaves the composition (see
 * VrTheaterRenderer.onRendererShutdown), so the stream's own camera recording can take it back.
 */
internal class VrPassthroughCamera(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        /** Called on the camera thread; the renderer only flips a volatile flag. */
        fun onUnavailable(reason: String)

        /**
         * Called on the camera thread once the device is open. [rotationDeg] is how far the sensor
         * picture must be rotated CLOCKWISE to appear upright at the current display rotation
         * (0/90/180/270); [mirror] asks for a horizontal flip so a front camera reads like a
         * mirror. Both are applied by the renderer to the window quad's TEX COORDS — no shader
         * change, no extra render pass. The renderer only stores these as volatile fields.
         */
        fun onPreviewOrientation(rotationDeg: Int, mirror: Boolean)
    }

    private companion object {
        const val TAG = "VrPassthroughCamera"
    }

    private val gate = Any()
    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    /** All fields below are touched ONLY on the camera thread. */
    private var running = false
    private var currentFacing: VrPassthroughFacing? = null
    private var currentSurface: Surface? = null
    /** Preview buffer size is part of the idempotency key: changing 4:3 <-> 16:9 MUST restart
     *  the capture session, otherwise the Camera2 stream keeps the old dimensions and stretches. */
    private var currentPreviewW = 0
    private var currentPreviewH = 0
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var generation = 0

    @Volatile
    private var released = false

    private fun ensureThread(): Handler = synchronized(gate) {
        val existing = handler
        if (existing != null) {
            existing
        } else {
            val t = HandlerThread(TAG).apply { start() }
            thread = t
            Handler(t.looper).also { handler = it }
        }
    }

    /**
     * Starts (or restarts with a different lens/surface) the preview. Idempotent for an unchanged
     * request, so `VrModeHost.update` calling applySettings() on every recomposition is harmless.
     */
    fun start(
        surface: Surface,
        facing: VrPassthroughFacing,
        previewWidth: Int,
        previewHeight: Int,
    ) {
        val h = ensureThread()
        h.post {
            if (released) return@post
            if (running && currentFacing == facing && currentSurface === surface &&
                currentPreviewW == previewWidth && currentPreviewH == previewHeight
            ) {
                return@post
            }
            stopLocked()
            if (cameraManager == null) {
                reportUnavailable("no CameraManager on this device")
                return@post
            }
            if (!hasCameraPermission()) {
                reportUnavailable("CAMERA runtime permission not granted")
                return@post
            }
            val id = pickCameraId(cameraManager, facing)
            if (id == null) {
                reportUnavailable("no camera device available")
                return@post
            }
            running = true
            currentFacing = facing
            currentSurface = surface
            currentPreviewW = previewWidth
            currentPreviewH = previewHeight
            val gen = ++generation
            try {
                cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (released || gen != generation || !running) {
                            camera.close()
                            return
                        }
                        cameraDevice = camera
                        // Camera2 does NOT rotate preview buffers for us. Report the correction
                        // once; the renderer bakes it into the window quad's tex coords.
                        listener.onPreviewOrientation(
                            previewRotationDeg(id),
                            facing == VrPassthroughFacing.FRONT,
                        )
                        openSession(camera, surface, gen)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (gen == generation) failLocked(gen, "camera disconnected")
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        if (gen == generation) failLocked(gen, "camera error code $error")
                    }
                }, h)
            } catch (e: Exception) {
                failLocked(gen, "openCamera failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun openSession(camera: CameraDevice, surface: Surface, gen: Int) {
        try {
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (released || gen != generation || !running) {
                            session.close()
                            return
                        }
                        captureSession = session
                        try {
                            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                .apply { addTarget(surface) }
                                .build()
                            session.setRepeatingRequest(request, null, handler)
                            Log.i(TAG, "passthrough preview started (${currentFacing})")
                        } catch (e: Exception) {
                            failLocked(gen, "repeating request failed: ${e.javaClass.simpleName}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        failLocked(gen, "capture session configure failed")
                    }
                },
                handler,
            )
        } catch (e: Exception) {
            failLocked(gen, "createCaptureSession failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Stops the preview but keeps the thread alive so a later start() is cheap. */
    fun stop() {
        val h = handler ?: return
        h.post { stopLocked() }
    }

    /** Camera-thread-only state reset. Closes session and device, invalidates pending callbacks. */
    private fun stopLocked() {
        generation++
        running = false
        currentFacing = null
        currentSurface = null
        currentPreviewW = 0
        currentPreviewH = 0
        runCatching { captureSession?.close() }
        captureSession = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
    }

    private fun failLocked(gen: Int, reason: String) {
        stopLocked()
        if (!released && gen != 0) reportUnavailable(reason)
    }

    /** Final teardown: quits the thread after draining the queued stop(). Never called twice. */
    fun release() {
        synchronized(gate) {
            if (released) return
            released = true
        }
        val h = handler
        val t = thread
        if (h != null && t != null) {
            h.post { stopLocked() }
            t.quitSafely()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Clockwise rotation the sensor picture needs to look upright at the CURRENT display rotation.
     * This is the standard Camera2 formula (sensor orientation minus display rotation for a rear
     * lens). The app forces sensor-landscape while VR is on, so displayDeg is normally 90 or 270.
     * Front lenses additionally get [Listener.onPreviewOrientation]'s mirror flag.
     *
     * Deliberately computed here rather than hardcoded: sensor orientation differs per device
     * (90 on most, 270 on some), and a fixed "rotate 90" would leave those devices sideways.
     */
    private fun previewRotationDeg(cameraId: String): Int {
        val sensor = try {
            cameraManager?.getCameraCharacteristics(cameraId)
                ?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        } catch (e: Exception) {
            90
        }
        val displayDeg = try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            when (wm?.defaultDisplay?.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
        return (sensor - displayDeg + 360) % 360
    }

    /**
     * Prefers the requested lens; if the device has no such lens (common on TV boxes / single-
     * camera phones) it falls back to the first available camera instead of failing outright.
     */
    private fun pickCameraId(manager: CameraManager, facing: VrPassthroughFacing): String? {
        val wanted = if (facing == VrPassthroughFacing.FRONT) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val ids = try {
            manager.cameraIdList
        } catch (e: Exception) {
            Log.w(TAG, "cameraIdList unavailable", e)
            return null
        }
        val exact = ids.firstOrNull { id ->
            try {
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == wanted
            } catch (e: Exception) {
                false
            }
        }
        return exact ?: ids.firstOrNull()
    }

    private fun reportUnavailable(reason: String) {
        Log.w(TAG, "passthrough unavailable: $reason")
        listener.onUnavailable(reason)
    }
}
