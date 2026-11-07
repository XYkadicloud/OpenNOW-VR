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
 *  - L1+R1 toggles VR on/off. START+SELECT and HOME/MODE are NOT single-press shortcuts: the
 *    settings panel opens only after a LONG PRESS (>= VrKeys.MENU_LONG_PRESS_MS) so a stray tap
 *    in game can never open it. Short presses pass straight through to the game unchanged.
 *  - When the panel is open, D-pad / shoulder / face buttons / left stick drive the panel only,
 *    and a single press of B / SELECT / START / MODE closes it.
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
    /** Time when a menu long-press hold began. Long.MIN_VALUE = no hold in progress. */
    private var menuHoldStartMs = Long.MIN_VALUE
    /** Latches once a long-press fired, until the hold is released, so repeats cannot re-toggle. */
    private var menuHoldFired = false

    fun reset() {
        pressed.clear()
        comboFired = false
        lastAxisMs = Long.MIN_VALUE
        lastKeyRepeatMs = Long.MIN_VALUE
        menuHoldStartMs = Long.MIN_VALUE
        menuHoldFired = false
    }

    fun onKey(keyCode: Int, action: Int, isGamepad: Boolean, timeMs: Long = 0L): VrCommand {
        if (!isGamepad) return VrCommand.PassThrough
        if (action == VrKeys.ACTION_UP) {
            pressed.remove(keyCode)
            lastKeyRepeatMs = Long.MIN_VALUE
            if (!comboPressed()) comboFired = false
            // Releasing any key of the hold cancels the timer and clears the latch, so the user
            // must deliberately press-and-hold again to toggle the menu a second time.
            if (!menuHoldActive()) clearMenuHold()
            return if (menuOpen) VrCommand.Consumed else VrCommand.PassThrough
        }
        if (action != VrKeys.ACTION_DOWN) return VrCommand.PassThrough

        val alreadyDown = !pressed.add(keyCode)
        // L1+R1 toggles VR. It is disabled while the menu is open (shoulders are fast-adjust there).
        if (!menuOpen && comboPressed()) {
            if (!comboFired) {
                comboFired = true
                return VrCommand.ToggleVrMode
            }
            return VrCommand.Consumed
        }
        // The OS repeats ACTION_DOWN while a key is held. Outside the menu those repeats must stay
        // pass-through so the game keeps its original behaviour; we only watch them for the
        // long-press timer (START+SELECT together, or HOME/MODE alone).
        if (alreadyDown) {
            if (!menuOpen) {
                if (vrEnabled && menuHoldActive()) {
                    if (menuHoldStartMs == Long.MIN_VALUE) {
                        menuHoldStartMs = timeMs
                    } else if (!menuHoldFired &&
                        timeMs - menuHoldStartMs >= VrKeys.MENU_LONG_PRESS_MS
                    ) {
                        menuHoldFired = true
                        return VrCommand.ToggleMenu
                    }
                } else {
                    clearMenuHold()
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
        // No single key opens the menu. The long-press timer starts on the first OS key-repeat.
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
        // B, SELECT, START and MODE all close the panel so a user without a B button is not trapped.
        VrKeys.BUTTON_B -> VrCommand.CloseMenu
        in VrKeys.MENU_CLOSE_KEYS -> VrCommand.CloseMenu
        else -> VrCommand.Consumed
    }

    private fun fastMultiplier(): Int =
        if (VrKeys.FAST_ADJUST_KEYS.any { pressed.contains(it) }) 5 else 1

    private fun comboPressed(): Boolean = VrKeys.VR_TOGGLE_COMBOS.any { (a, b) ->
        pressed.contains(a) && pressed.contains(b)
    }

    /** True while START+SELECT are both held, OR the HOME/MODE key is held. */
    private fun menuHoldActive(): Boolean {
        val combo = VrKeys.MENU_LONG_PRESS_COMBO
        val bothHeld = pressed.contains(combo[0]) && pressed.contains(combo[1])
        return bothHeld || pressed.contains(VrKeys.BUTTON_MODE)
    }

    private fun clearMenuHold() {
        menuHoldStartMs = Long.MIN_VALUE
        menuHoldFired = false
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
