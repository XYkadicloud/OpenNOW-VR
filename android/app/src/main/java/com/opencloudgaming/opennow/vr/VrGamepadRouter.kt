package com.opencloudgaming.opennow.vr

import kotlin.math.abs
import kotlin.math.max

/** Output of the gamepad router. The host decides what to do with each command. */
sealed class VrCommand {
    /** Let the original stream input pipeline handle this event unchanged. */
    object PassThrough : VrCommand()

    /** Swallowed by the VR layer (only happens while the VR settings panel is open). */
    object Consumed : VrCommand()

    object ToggleVrMode : VrCommand()
    object ToggleMenu : VrCommand()
    object CloseMenu : VrCommand()
    object Confirm : VrCommand()
    data class MoveSelection(val delta: Int) : VrCommand()
    data class Adjust(val steps: Int) : VrCommand()
}

/**
 * Pure state machine for PHYSICAL GAMEPAD input in VR mode.
 *
 * Design rules (from the user, do not "improve" these):
 *  - No gaze / eye / dwell / head-gesture activation of any kind. Head pose is view-only.
 *  - When the settings panel is closed, every key except the VR toggle combo and the menu key is
 *    passed straight through to the game, so existing controller behaviour is unchanged.
 *  - When the panel is open, D-pad / shoulder / face buttons / left stick drive the panel only.
 *
 * No Android framework types are used here; VrKeys holds plain Int constants so JVM unit tests run
 * without Robolectric or mocked Android stubs.
 */
class VrGamepadRouter {
    var vrEnabled: Boolean = false
    var menuOpen: Boolean = false
    var deadZone: Float = VrSettings.DEFAULT.gamepadDeadZone
    var repeatMs: Long = VrSettings.DEFAULT.menuRepeatMs

    private val pressed = HashSet<Int>()
    private var comboFired = false
    private var lastAxisMs = Long.MIN_VALUE
    private var lastKeyRepeatMs = Long.MIN_VALUE
    /** Time when both START+SELECT were first held together. Long.MIN_VALUE = not currently held. */
    private var menuComboStartMs = Long.MIN_VALUE

    fun reset() {
        pressed.clear()
        comboFired = false
        lastAxisMs = Long.MIN_VALUE
        lastKeyRepeatMs = Long.MIN_VALUE
        menuComboStartMs = Long.MIN_VALUE
    }

    fun onKey(keyCode: Int, action: Int, isGamepad: Boolean, timeMs: Long = 0L): VrCommand {
        if (!isGamepad) return VrCommand.PassThrough
        if (action == VrKeys.ACTION_UP) {
            pressed.remove(keyCode)
            lastKeyRepeatMs = Long.MIN_VALUE
            if (!comboPressed()) comboFired = false
            // If the menu long-press combo is broken before firing, reset its timer.
            if (!menuComboPressed()) menuComboStartMs = Long.MIN_VALUE
            return if (menuOpen) VrCommand.Consumed else VrCommand.PassThrough
        }
        if (action != VrKeys.ACTION_DOWN) return VrCommand.PassThrough

        val alreadyDown = !pressed.add(keyCode)
        // While the menu is open every button belongs to the panel. SELECT/START are also part
        // of the VR-toggle combo, so checking the combo here would exit VR (or swallow the key)
        // when the user simply wants to close the panel. The combo stays active outside the menu.
        if (!menuOpen && comboPressed()) {
            if (!comboFired) {
                comboFired = true
                return VrCommand.ToggleVrMode
            }
            return VrCommand.Consumed
        }
        // The OS repeats ACTION_DOWN while a key is held. Outside the menu that repeat must not be
        // consumed (the game may use it), so the original pass-through behaviour is unchanged.
        if (alreadyDown) {
            if (!menuOpen) {
                // Track long-press START+SELECT for menu toggle.
                if (menuComboPressed()) {
                    if (menuComboStartMs == Long.MIN_VALUE) menuComboStartMs = timeMs
                    else if (timeMs - menuComboStartMs >= VrKeys.MENU_LONG_PRESS_MS) {
                        menuComboStartMs = Long.MIN_VALUE
                        return VrCommand.ToggleMenu
                    }
                } else {
                    menuComboStartMs = Long.MIN_VALUE
                }
                return VrCommand.PassThrough
            }
            // In the menu only the D-pad repeats: holding A/B/SELECT must never confirm or close
            // repeatedly. Repeat cadence follows the user's "menu repeat" setting (>= 80 ms).
            if (keyCode !in REPEATABLE_MENU_KEYS) return VrCommand.Consumed
            val delay = repeatMs.coerceAtLeast(MIN_KEY_REPEAT_MS)
            if (lastKeyRepeatMs != Long.MIN_VALUE && timeMs - lastKeyRepeatMs < delay) {
                return VrCommand.Consumed
            }
            lastKeyRepeatMs = timeMs
            return menuKey(keyCode)
        }

        lastKeyRepeatMs = Long.MIN_VALUE
        if (menuOpen) return menuKey(keyCode)
        if (vrEnabled && VrKeys.MENU_KEYS.contains(keyCode)) return VrCommand.ToggleMenu
        return VrCommand.PassThrough
    }

    /**
     * Left analogue stick while the menu is open. Axis events are consumed (never forwarded to
     * the game) so adjusting a slider does not also move the in-game camera.
     */
    fun onAxis(x: Float, y: Float, timeMs: Long): VrCommand {
        if (!menuOpen) return VrCommand.PassThrough
        val ax = abs(x)
        val ay = abs(y)
        if (max(ax, ay) < deadZone) {
            lastAxisMs = Long.MIN_VALUE
            return VrCommand.Consumed
        }
        if (lastAxisMs != Long.MIN_VALUE && timeMs - lastAxisMs < repeatMs) return VrCommand.Consumed
        lastAxisMs = timeMs
        return if (ay >= ax) {
            VrCommand.MoveSelection(if (y < 0f) -1 else 1)
        } else {
            VrCommand.Adjust(if (x < 0f) -1 else 1)
        }
    }

    private fun menuKey(keyCode: Int): VrCommand = when (keyCode) {
        VrKeys.DPAD_UP -> VrCommand.MoveSelection(-1)
        VrKeys.DPAD_DOWN -> VrCommand.MoveSelection(1)
        VrKeys.DPAD_LEFT -> VrCommand.Adjust(-fastMultiplier())
        VrKeys.DPAD_RIGHT -> VrCommand.Adjust(fastMultiplier())
        VrKeys.BUTTON_A, VrKeys.DPAD_CENTER -> VrCommand.Confirm
        VrKeys.BUTTON_B -> VrCommand.CloseMenu
        // SELECT/START opens the menu; pressing it again must close it, otherwise a user without
        // a B button would be trapped in the panel.
        in VrKeys.MENU_KEYS -> VrCommand.CloseMenu
        else -> VrCommand.Consumed
    }

    private fun fastMultiplier(): Int =
        if (VrKeys.FAST_ADJUST_KEYS.any { pressed.contains(it) }) 5 else 1

    private fun comboPressed(): Boolean = VrKeys.VR_TOGGLE_COMBOS.any { (a, b) ->
        pressed.contains(a) && pressed.contains(b)
    }

    /** True if both START and SELECT are currently held (for long-press menu toggle). */
    private fun menuComboPressed(): Boolean {
        val combo = VrKeys.MENU_LONG_PRESS_COMBO
        return pressed.contains(combo[0]) && pressed.contains(combo[1])
    }

    private companion object {
        /**
         * Only navigation directions repeat while held. A/B/SELECT must not: OS key-repeat would
         * otherwise confirm a setting or close the panel dozens of times per second.
         */
        val REPEATABLE_MENU_KEYS = intArrayOf(
            VrKeys.DPAD_UP,
            VrKeys.DPAD_DOWN,
            VrKeys.DPAD_LEFT,
            VrKeys.DPAD_RIGHT,
        )

        /** Safety floor so a very small menuRepeatMs cannot turn into a CPU-bound spin. */
        const val MIN_KEY_REPEAT_MS = 80L
    }
}
