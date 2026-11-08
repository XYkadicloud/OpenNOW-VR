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

    /** Active second-level settings page (category chip). */
    private val _menuPage = MutableStateFlow(VrMenuModel.pages.first())
    val menuPage: StateFlow<VrMenuPage> = _menuPage.asStateFlow()

    /** True only after A armed value editing on a RANGE row; the left stick is gated on this. */
    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

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
        if (!value) setEditMode(false)
    }

    /** Switches the second-level page and lands selection on its first row. */
    fun setMenuPage(page: VrMenuPage) {
        _menuPage.value = page
        setEditMode(false)
        _selectedIndex.value = VrMenuModel.firstIndexOf(page)
    }

    /**
     * Single source of truth for A-armed editing. Mirrors the flag into the router (which decides
     * whether the stick NAVIGATES or ADJUSTS) and restarts the stick's repeat cadence, so the new
     * mode responds immediately instead of waiting out the previous mode's throttle window.
     */
    private fun setEditMode(value: Boolean) {
        _editMode.value = value
        router.editing = value
        router.resetAxisHold()
    }

    fun changePage(delta: Int) {
        val index = VrMenuModel.pageIndex(_menuPage.value) + delta
        val clamped = index.coerceIn(0, VrMenuModel.pages.lastIndex)
        setMenuPage(VrMenuModel.pages[clamped])
    }

    /** Selection never leaves the active page: D-pad cannot jump into another category. */
    fun setSelectedIndex(index: Int) {
        _selectedIndex.value = VrMenuModel.clampToPage(index, _menuPage.value)
    }

    fun updateSettings(value: VrSettings) {
        val clean = value.sanitized()
        _settings.value = clean
        store?.save(clean)
        syncRouter()
    }

    /**
     * Decides every physical key while VR is on.
     *
     * TWO real-device bugs fixed here (2026-11-08):
     *  1. Only `isGamepadSource()` events used to reach the router. Several Bluetooth pads report
     *     their face buttons / D-pad as SOURCE_KEYBOARD, so every menu key was handed to the 2D
     *     Compose UI instead: rows could not be selected and pages could not be switched. While
     *     VR is enabled we therefore do NOT gate on source — the router only emits commands for a
     *     small explicit key whitelist and leaves everything else alone.
     *  2. KEYCODE_BACK (which is what many pads map B to) used to be swallowed on ACTION_DOWN
     *     while VR was turned off in the same call. ACTION_UP then saw VR==false, fell through to
     *     the 2D back stack and FINISHED THE ACTIVITY. We now consume the whole down+up sequence
     *     and act only on the up.
     *
     * MainActivity additionally drops all non-VR keys once this returns false, so nothing can
     * reach the 2D UI behind the headset.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        if (_enabled.value) {
            // Back-like keys are owned by the VR layer for BOTH actions, unconditionally.
            if (keyCode == VrKeys.BACK) {
                if (action == VrKeys.ACTION_UP) {
                    if (_menuOpen.value) setMenuOpen(false) else setEnabled(false)
                }
                return true
            }
            val command = router.onKey(keyCode, action, isGamepad = true, event.eventTime)
            if (router.menuOpen) {
                // The settings panel owns input exclusively: even a key the router does not know
                // must not reach the 2D UI or the stream game.
                if (command == VrCommand.PassThrough) return true
                return handle(command)
            }
            return handle(command)
        }

        // VR off: only real controller events may trigger L1+R1 to ENTER VR. Keep the source gate
        // here so the phone's soft keyboard and the normal 2D UI are completely untouched.
        if (!isGamepadSource(event.source)) return false
        return handle(router.onKey(keyCode, action, isGamepad = true, event.eventTime))
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        // No source gate while the VR menu is open: some pads report axes from a keyboard-ish
        // source, which used to make the left stick silently do nothing.
        if (!_enabled.value || !router.menuOpen) return false
        val x = event.getAxisValue(VrKeys.AXIS_X)
        val y = event.getAxisValue(VrKeys.AXIS_Y)
        // A lot of Bluetooth pads send the physical D-pad as AXIS_HAT_Y, not KEYCODE_DPAD_*.
        // Since MainActivity deliberately stops forwarding generic motion to the 2D Compose UI
        // while VR is on, those events must be handled HERE or the D-pad looks completely dead.
        val hat = router.onHat(event.getAxisValue(VrKeys.AXIS_HAT_Y))
        val command = if (hat is VrCommand.MoveSelection) hat else router.onAxis(x, y, event.eventTime)
        return handle(command)
    }

    private fun handle(command: VrCommand): Boolean = when (command) {
        VrCommand.PassThrough -> false
        VrCommand.Consumed -> true
        VrCommand.ToggleVrMode -> {
            // L1+R1 is ENTRY-ONLY (user request). Exiting must be explicit: the phone's system
            // BACK key, or the "退出 VR" ACTION row in the settings panel. A slipped shoulder
            // combo can therefore never terminate a running session.
            if (!_enabled.value) toggle()
            true
        }
        VrCommand.ToggleMenu -> { setMenuOpen(!_menuOpen.value); true }
        VrCommand.CloseMenu -> { setMenuOpen(false); true }
        VrCommand.Confirm -> { confirmSelected(); true }
        is VrCommand.MoveSelection -> {
            setSelectedIndex(_selectedIndex.value + command.delta)
            true
        }
        is VrCommand.ChangePage -> {
            changePage(command.delta)
            true
        }
        is VrCommand.Adjust -> {
            // Numeric values move ONLY while A has armed editing (see VrMenuModel.adjustIfEditing).
            // While disarmed the gate returns the SAME object, so we skip the SharedPreferences
            // write entirely instead of hammering disk with every analogue-stick motion event.
            val before = _settings.value
            val id = VrMenuModel.items[_selectedIndex.value].id
            val after = VrMenuModel.adjustIfEditing(before, id, command.steps, _editMode.value)
            if (after !== before && after != before) updateSettings(after)
            true
        }
    }

    private fun confirmSelected() {
        val id = VrMenuModel.items[_selectedIndex.value].id
        when (id) {
            // These two have host side effects; the rest are pure settings changes.
            VrMenuItemId.SCAN_QR -> { onScanQr?.invoke(); return }
            VrMenuItemId.EXIT_VR -> { setEnabled(false); return }
            else -> {
                val result = VrMenuModel.confirm(_settings.value, id, _editMode.value)
                updateSettings(result.settings)
                setEditMode(result.editing)
            }
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
