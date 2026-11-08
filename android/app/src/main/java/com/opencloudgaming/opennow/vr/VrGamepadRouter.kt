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
    /** D-pad LEFT/RIGHT switch the second-level settings PAGE (never change a value). */
    data class ChangePage(val delta: Int) : VrCommand()
    /** Left stick only. VrSession gated by "A armed editing"; no D-pad ever emits this. */
    data class Adjust(val steps: Int) : VrCommand()
}

/**
 * Pure state machine for PHYSICAL GAMEPAD input in VR mode.
 *
 * Design rules (from the user, do not "improve" these):
 *  - No gaze / eye / dwell / head-gesture activation of any kind. Head pose is view-only.
 *  - L1+R1 toggles VR on/off. START+SELECT and HOME/MODE are NOT single-press shortcuts: the
 *    settings panel opens only after a LONG PRESS (>= VrKeys.MENU_LONG_PRESS_MS) so a stray tap
 *    in game can never open it. Short presses pass straight through to the game unchanged. The
 *    hold timer starts on the initial ACTION_DOWN that completes the combination, NOT on the first
 *    OS key-repeat (see the note in onKey for why).
 *  - When the panel is open, D-pad UP/DOWN only SELECTS a row, D-pad LEFT/RIGHT only switches the
 *    second-level PAGE, A arms value editing / flips a switch, B closes the panel. NUMERIC VALUES
 *    are changed ONLY by the left analogue stick AND ONLY while A has armed editing (that gate is
 *    VrMenuModel.adjustIfEditing, enforced in VrSession). This is deliberate: it makes accidental
 *    slider drift impossible.
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
        // L1+R1 toggles VR. It is disabled while the menu is open (shoulders are swallowed there).
        if (!menuOpen && comboPressed()) {
            if (!comboFired) {
                comboFired = true
                return VrCommand.ToggleVrMode
            }
            return VrCommand.Consumed
        }
        // The OS repeats ACTION_DOWN while a key is held. Outside the menu those repeats must stay
        // pass-through so the game keeps its original behaviour. A repeat is also the only clock
        // tick this pure-JVM state machine gets: the timer is normally armed on the initial
        // ACTION_DOWN (see below) and a repeat merely checks whether 500 ms have elapsed. The
        // MIN_VALUE arm below is only a defensive fallback for a hold that became valid later.
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
        // Arm the long-press timer as soon as the hold becomes active: the initial ACTION_DOWN of
        // the second START/SELECT key, or of MODE. Starting it on the first OS key-repeat instead
        // would add that controller's repeat delay (~400 ms, varies per device) on top of the
        // user's 500 ms hold. Repeats are still required to FIRE (this class has no timer/Handler
        // of its own), they just no longer start the clock.
        if (vrEnabled && menuHoldActive()) {
            if (menuHoldStartMs == Long.MIN_VALUE) {
                menuHoldStartMs = timeMs
            }
        } else {
            clearMenuHold()
        }
        // No single key opens the menu on its own.
        return VrCommand.PassThrough
    }

    /**
     * Left analogue stick while the menu is open. It ONLY adjusts the numeric value of the
     * selected row: horizontal = one step, vertical = five steps (coarse). VrSession drops the
     * command unless A has armed editing, so merely resting a thumb on the stick changes nothing.
     * Axis events are consumed (never forwarded to the game) so adjusting a slider does not also
     * move the in-game camera.
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
            VrCommand.Adjust(if (y < 0f) -STICK_COARSE_STEPS else STICK_COARSE_STEPS)
        } else {
            VrCommand.Adjust(if (x < 0f) -1 else 1)
        }
    }

    private fun menuKey(keyCode: Int): VrCommand = when (keyCode) {
        VrKeys.DPAD_UP -> VrCommand.MoveSelection(-1)
        VrKeys.DPAD_DOWN -> VrCommand.MoveSelection(1)
        VrKeys.DPAD_LEFT -> VrCommand.ChangePage(-1)
        VrKeys.DPAD_RIGHT -> VrCommand.ChangePage(1)
        VrKeys.BUTTON_A, VrKeys.DPAD_CENTER -> VrCommand.Confirm
        // B, SELECT, START and MODE all close the panel so a user without a B button is not trapped.
        VrKeys.BUTTON_B -> VrCommand.CloseMenu
        in VrKeys.MENU_CLOSE_KEYS -> VrCommand.CloseMenu
        else -> VrCommand.Consumed
    }

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

    companion object {
        /** Vertical stick deflection is a coarse adjustment; horizontal is one fine step. */
        const val STICK_COARSE_STEPS = 5

        /**
         * Only UP/DOWN repeat while held (scrolling rows). A/B/SELECT must not, and neither may
         * LEFT/RIGHT: those switch the second-level PAGE, and OS key-repeat would otherwise fling
         * the user to the first/last category from a single held press.
         */
        val REPEATABLE_MENU_KEYS = intArrayOf(
            VrKeys.DPAD_UP,
            VrKeys.DPAD_DOWN,
        )

        /** Safety floor so a very small menuRepeatMs cannot turn into a CPU-bound spin. */
        const val MIN_KEY_REPEAT_MS = 80L
    }
}
