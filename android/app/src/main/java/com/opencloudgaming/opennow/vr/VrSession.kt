package com.opencloudgaming.opennow.vr

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-wide VR state and the single entry point for physical-gamepad interception.
 *
 * MainActivity calls onKeyEvent()/onMotionEvent() BEFORE NativeStreamInputRouter. Returning true
 * means the VR layer consumed the event; otherwise the original stream input path is untouched.
 *
 * IMPORTANT: there is no gaze/eye/dwell input anywhere in this file. Head pose only reaches
 * VrTheaterRenderer as a view matrix.
 */
object VrSession {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _settings = MutableStateFlow(VrSettings.DEFAULT)
    val settings: StateFlow<VrSettings> = _settings.asStateFlow()

    private val _menuOpen = MutableStateFlow(false)
    val menuOpen: StateFlow<Boolean> = _menuOpen.asStateFlow()

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    /** Real decoder frame aspect, written by Streaming.kt.onFrameResolutionChanged. */
    @Volatile var videoAspect: Float = VrScreenGeometry.DEFAULT_ASPECT
        private set

    /** Guards against 0/NaN/rotated extremes; the renderer rebuilds its mesh when this changes. */
    fun setVideoAspect(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val aspect = width.toFloat() / height.toFloat()
        if (aspect.isNaN() || aspect.isInfinite()) return
        videoAspect = aspect.coerceIn(0.25f, 8f)
    }

    /** Set by VrModeHost so the "scan Cardboard QR" menu row can reach the SDK view. */
    @Volatile var onScanQr: (() -> Unit)? = null

    /** Set by VrModeHost so the VR menu can trigger head-tracking recenter. */
    @Volatile var onRecenter: (() -> Unit)? = null

    private var store: VrSettingsStore? = null
    private val router = VrGamepadRouter()

    fun attach(context: Context) {
        store = VrSettingsStore(context)
        _settings.value = store!!.load()
        syncRouter()
    }

    fun setEnabled(value: Boolean) {
        if (_enabled.value == value) return
        Log.d("VrSession", "setEnabled($value): toggling VR; router.vrEnabled=$value")
        _enabled.value = value
        router.vrEnabled = value
        if (!value) {
            setMenuOpen(false)
            router.reset()
            VrVideoSurfaceRegistry.clear()
        }
    }

    fun toggle() = setEnabled(!_enabled.value)

    /** Triggers head-tracking recenter: current head orientation becomes the new "forward". */
    fun recenter() {
        onRecenter?.invoke()
    }

    fun setMenuOpen(value: Boolean) {
        _menuOpen.value = value
        router.menuOpen = value
    }

    fun setSelectedIndex(index: Int) {
        _selectedIndex.value = index.coerceIn(0, VrMenuModel.items.lastIndex)
    }

    fun updateSettings(value: VrSettings) {
        val clean = value.sanitized()
        _settings.value = clean
        store?.save(clean)
        syncRouter()
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val isGamepad = isGamepadSource(event.source)
        return handle(router.onKey(event.keyCode, event.action, isGamepad, event.eventTime))
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (!router.menuOpen || !isGamepadSource(event.source)) return false
        val x = event.getAxisValue(VrKeys.AXIS_X)
        val y = event.getAxisValue(VrKeys.AXIS_Y)
        return handle(router.onAxis(x, y, event.eventTime))
    }

    private fun handle(command: VrCommand): Boolean = when (command) {
        VrCommand.PassThrough -> false
        VrCommand.Consumed -> true
        VrCommand.ToggleVrMode -> { toggle(); true }
        VrCommand.ToggleMenu -> { setMenuOpen(!_menuOpen.value); true }
        VrCommand.CloseMenu -> { setMenuOpen(false); true }
        VrCommand.Confirm -> { confirmSelected(); true }
        is VrCommand.MoveSelection -> {
            setSelectedIndex(_selectedIndex.value + command.delta)
            true
        }
        is VrCommand.Adjust -> {
            val id = VrMenuModel.items[_selectedIndex.value].id
            updateSettings(VrMenuModel.adjust(_settings.value, id, command.steps))
            true
        }
    }

    private fun confirmSelected() {
        val id = VrMenuModel.items[_selectedIndex.value].id
        when (id) {
            VrMenuItemId.SCAN_QR -> onScanQr?.invoke()
            VrMenuItemId.RESET_DEFAULTS -> updateSettings(VrSettings.DEFAULT)
            VrMenuItemId.EXIT_VR -> setEnabled(false)
            else -> updateSettings(VrMenuModel.activate(_settings.value, id))
        }
    }

    private fun syncRouter() {
        router.deadZone = _settings.value.gamepadDeadZone
        router.repeatMs = _settings.value.menuRepeatMs
    }

    private fun isGamepadSource(source: Int): Boolean =
        (source and VrKeys.SOURCE_GAMEPAD) == VrKeys.SOURCE_GAMEPAD ||
            (source and VrKeys.SOURCE_JOYSTICK) == VrKeys.SOURCE_JOYSTICK ||
            (source and VrKeys.SOURCE_DPAD) == VrKeys.SOURCE_DPAD
}
