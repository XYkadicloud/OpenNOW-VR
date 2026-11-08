package com.opencloudgaming.opennow.vr

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM tests: VrGamepadRouter deliberately avoids all android.view.* classes. */
class VrGamepadRouterTest {

    private fun press(r: VrGamepadRouter, key: Int, timeMs: Long = 0L): VrCommand =
        r.onKey(key, VrKeys.ACTION_DOWN, isGamepad = true, timeMs = timeMs)

    private fun release(r: VrGamepadRouter, key: Int, timeMs: Long = 0L): VrCommand =
        r.onKey(key, VrKeys.ACTION_UP, isGamepad = true, timeMs = timeMs)

    @Test
    fun nonGamepadEventsAlwaysPassThrough() {
        val r = VrGamepadRouter()
        assertEquals(
            VrCommand.PassThrough,
            r.onKey(VrKeys.BUTTON_L1, VrKeys.ACTION_DOWN, isGamepad = false),
        )
    }

    /** The original game must keep receiving every button while VR is off. */
    @Test
    fun keysPassThroughToTheGameWhenVrIsOffAndMenuIsClosed() {
        val r = VrGamepadRouter()
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.DPAD_UP))
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_A))
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_SELECT))
    }

    @Test
    fun shoulderComboTogglesVrExactlyOncePerHold() {
        val r = VrGamepadRouter()
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_L1))
        assertEquals(VrCommand.ToggleVrMode, press(r, VrKeys.BUTTON_R1))
        // OS key-repeat while both are held must not toggle again.
        assertEquals(VrCommand.Consumed, press(r, VrKeys.BUTTON_R1))
        assertEquals(VrCommand.PassThrough, release(r, VrKeys.BUTTON_L1))
        assertEquals(VrCommand.PassThrough, release(r, VrKeys.BUTTON_R1))

        r.vrEnabled = true
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_L1))
        assertEquals(VrCommand.ToggleVrMode, press(r, VrKeys.BUTTON_R1))
    }

    /**
     * START+SELECT is reserved for the menu long-press; it must NOT exit VR any more.
     *
     * The 500 ms timer starts when the combination first becomes held (the initial ACTION_DOWN at
     * 1000L), NOT on the first OS key-repeat. If it started on the repeat (1200L) instead, the
     * device's repeat delay would be added on top of the threshold and the 1600L event below would
     * still be only 400 ms in. The 1499L/1600L pair pins that contract down.
     */
    @Test
    fun startSelectDoesNotToggleVrButLongPressOpensMenu() {
        val r = VrGamepadRouter().also { it.vrEnabled = true }
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_SELECT, 1000L))
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_START, 1000L))
        // First OS key-repeat: 200 ms elapsed, still below threshold.
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_START, 1200L))
        // 499 ms since the initial press: must not fire yet.
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_START, 1499L))
        // 600 ms since the initial press: fires exactly once.
        assertEquals(VrCommand.ToggleMenu, press(r, VrKeys.BUTTON_START, 1600L))
    }

    @Test
    fun menuLongPressOpensOnlyInsideVr() {
        val r = VrGamepadRouter().also { it.vrEnabled = true }
        // Short press of SELECT/START does NOT open menu (passes through to game).
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_SELECT, 1000L))
        assertEquals(VrCommand.PassThrough, release(r, VrKeys.BUTTON_SELECT, 1100L))
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_START, 1200L))
        assertEquals(VrCommand.PassThrough, release(r, VrKeys.BUTTON_START, 1300L))
        // Long-press START+SELECT together opens menu. The timer starts here at 2000L, when the
        // second key of the combination goes down (not at the first OS repeat).
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_START, 2000L))
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_SELECT, 2000L))
        // 100 ms in: still pass-through.
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_START, 2100L))
        // 600 ms in: past the 500 ms threshold, toggles menu.
        assertEquals(VrCommand.ToggleMenu, press(r, VrKeys.BUTTON_START, 2600L))
        r.menuOpen = true
        assertEquals(VrCommand.Confirm, press(r, VrKeys.BUTTON_A))
        assertEquals(VrCommand.CloseMenu, press(r, VrKeys.BUTTON_B))
    }

    /** HOME/MODE also opens the menu only after a long press, never on a short tap. */
    @Test
    fun homeKeyRequiresLongPress() {
        val r = VrGamepadRouter().also { it.vrEnabled = true }
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_MODE, 1000L))
        assertEquals(VrCommand.PassThrough, release(r, VrKeys.BUTTON_MODE, 1100L))
        // Hold again: the timer starts at this initial press (2000L), so the first OS repeat below
        // is already 100 ms in. 500 ms after 2000L it fires exactly once.
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_MODE, 2000L))
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_MODE, 2100L))
        assertEquals(VrCommand.ToggleMenu, press(r, VrKeys.BUTTON_MODE, 2600L))
        // Key-repeat after firing must not toggle the menu back closed.
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_MODE, 3200L))
        // Releasing clears the latch, so a fresh long press can toggle again.
        assertEquals(VrCommand.PassThrough, release(r, VrKeys.BUTTON_MODE, 3300L))
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_MODE, 4000L))
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_MODE, 4100L))
        assertEquals(VrCommand.ToggleMenu, press(r, VrKeys.BUTTON_MODE, 4600L))
    }

    /** SELECT/START/MODE close the panel when it is already open (single press). */
    @Test
    fun menuKeyClosesThePanelWhenItIsAlreadyOpen() {
        val r = VrGamepadRouter().also { it.vrEnabled = true; it.menuOpen = true }
        assertEquals(VrCommand.CloseMenu, press(r, VrKeys.BUTTON_SELECT))
        assertEquals(VrCommand.CloseMenu, press(r, VrKeys.BUTTON_START))
        assertEquals(VrCommand.CloseMenu, press(r, VrKeys.BUTTON_MODE))
    }

    /**
     * LB / RB are the second-level PAGE keys (user request). The L1+R1 VR toggle is suppressed
     * while the panel is open, so the same physical shoulders can safely own both roles.
     */
    @Test
    fun shoulderButtonsPageTheMenuInsteadOfTogglingVr() {
        val r = VrGamepadRouter().also { it.vrEnabled = true; it.menuOpen = true }
        assertEquals(VrCommand.ChangePage(-1), press(r, VrKeys.BUTTON_L1))
        assertEquals(VrCommand.ChangePage(1), press(r, VrKeys.BUTTON_R1))
        // Even with both shoulders held at once, the VR toggle must NOT fire inside the menu.
        assertEquals(VrCommand.Consumed, press(r, VrKeys.BUTTON_L1))
        assertEquals(VrCommand.Consumed, press(r, VrKeys.BUTTON_R1))
    }

    @Test
    fun openMenuRoutesDpadAndFaceButtonsOnlyToThePanel() {
        val r = VrGamepadRouter().also { it.vrEnabled = true; it.menuOpen = true }
        assertEquals(VrCommand.MoveSelection(-1), press(r, VrKeys.DPAD_UP))
        assertEquals(VrCommand.MoveSelection(1), press(r, VrKeys.DPAD_DOWN))
        // D-pad LEFT/RIGHT are deliberately inert: they must neither page nor change a value,
        // and must never fall through to the hidden 2D Compose UI.
        assertEquals(VrCommand.Consumed, press(r, VrKeys.DPAD_LEFT))
        assertEquals(VrCommand.Consumed, press(r, VrKeys.DPAD_RIGHT))
        assertEquals(VrCommand.ChangePage(-1), press(r, VrKeys.PAGE_PREV_KEY))
        assertEquals(VrCommand.ChangePage(1), press(r, VrKeys.PAGE_NEXT_KEY))
        assertEquals(VrCommand.Confirm, press(r, VrKeys.DPAD_CENTER))
        assertEquals(VrCommand.Consumed, press(r, VrKeys.BUTTON_X))
        assertEquals(VrCommand.Consumed, release(r, VrKeys.BUTTON_X))
    }

    /** Holding a shoulder must not fling the user across several category pages. */
    @Test
    fun heldShoulderDoesNotRepeatPageChanges() {
        val r = VrGamepadRouter().also { it.vrEnabled = true; it.menuOpen = true }
        assertEquals(VrCommand.ChangePage(1), press(r, VrKeys.PAGE_NEXT_KEY, 1000L))
        assertEquals(VrCommand.Consumed, press(r, VrKeys.PAGE_NEXT_KEY, 1100L))
        assertEquals(VrCommand.Consumed, press(r, VrKeys.PAGE_NEXT_KEY, 5000L))
        assertEquals(VrCommand.Consumed, release(r, VrKeys.PAGE_NEXT_KEY, 5100L))
        // A genuinely new press changes exactly one more page.
        assertEquals(VrCommand.ChangePage(1), press(r, VrKeys.PAGE_NEXT_KEY, 5200L))
    }

    @Test
    fun verticalStickAdjustsCoarsely() {
        val r = VrGamepadRouter().also { it.menuOpen = true }
        // Vertical stick deflection uses coarse steps (5). Space the events past menuRepeatMs so
        // the router's rate limiter does not swallow the second one.
        assertEquals(VrCommand.Adjust(VrGamepadRouter.STICK_COARSE_STEPS), r.onAxis(0f, 0.9f, 0L))
        assertEquals(VrCommand.Adjust(-VrGamepadRouter.STICK_COARSE_STEPS), r.onAxis(0f, -0.9f, 1000L))
    }

    @Test
    fun horizontalStickAdjustsFinely() {
        val r = VrGamepadRouter().also { it.menuOpen = true }
        // Horizontal stick deflection uses one fine step.
        assertEquals(VrCommand.Adjust(-1), r.onAxis(-0.9f, 0f, 0L))
        assertEquals(VrCommand.Adjust(1), r.onAxis(0.9f, 0f, 1000L))
    }

    @Test
    fun stickAdjustIsConsumedButOnlyChangesValueWhenEditingArmed() {
        val r = VrGamepadRouter().also { it.menuOpen = true }
        // The router emits Adjust; VrSession drops it unless editing is armed.
        assertEquals(VrCommand.Adjust(1), r.onAxis(0.9f, 0f, 0L))
    }

    @Test
    fun axisIsIgnoredWhenMenuIsClosed() {
        val r = VrGamepadRouter()
        assertEquals(VrCommand.PassThrough, r.onAxis(1f, 0f, 1000L))
    }

    @Test
    fun axisBelowDeadZoneIsConsumedWithoutProducingACommand() {
        val r = VrGamepadRouter().also { it.menuOpen = true }
        assertEquals(VrCommand.Consumed, r.onAxis(0.1f, 0f, 1000L))
    }

    @Test
    fun customDeadZoneIsHonoured() {
        val r = VrGamepadRouter().also { it.menuOpen = true; it.deadZone = 0.5f }
        assertEquals(VrCommand.Consumed, r.onAxis(0.3f, 0f, 0L))
    }

    @Test
    fun verticalAxisCoarselyAdjustsWithRepeatDelay() {
        val r = VrGamepadRouter().also { it.menuOpen = true; it.repeatMs = 140L }
        val coarse = VrCommand.Adjust(VrGamepadRouter.STICK_COARSE_STEPS)
        assertEquals(coarse, r.onAxis(0f, 0.9f, 1000L))
        assertEquals(VrCommand.Consumed, r.onAxis(0f, 0.9f, 1050L))
        assertEquals(coarse, r.onAxis(0f, 0.9f, 1200L))
    }

    @Test
    fun horizontalAxisAdjustsValue() {
        val r = VrGamepadRouter().also { it.menuOpen = true }
        assertEquals(VrCommand.Adjust(-1), r.onAxis(-0.9f, 0f, 0L))
    }

    @Test
    fun returningStickToCentreResetsRepeatTimer() {
        val r = VrGamepadRouter().also { it.menuOpen = true }
        r.onAxis(0f, 0.9f, 1000L)
        assertEquals(VrCommand.Consumed, r.onAxis(0f, 0f, 1050L))
        assertEquals(
            VrCommand.Adjust(VrGamepadRouter.STICK_COARSE_STEPS),
            r.onAxis(0f, 0.9f, 1060L),
        )
    }

    @Test
    fun resetClearsHeldKeysAndTheComboLatch() {
        val r = VrGamepadRouter()
        press(r, VrKeys.BUTTON_L1)
        assertEquals(VrCommand.ToggleVrMode, press(r, VrKeys.BUTTON_R1))
        r.reset()
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_L1))
        assertEquals(VrCommand.ToggleVrMode, press(r, VrKeys.BUTTON_R1))
    }

    /** Holding a D-pad direction must scroll the panel, not just move one row. */
    @Test
    fun heldDpadRepeatsNavigationAtTheConfiguredCadence() {
        val r = VrGamepadRouter().also {
            it.vrEnabled = true
            it.menuOpen = true
            it.repeatMs = 140L
        }
        assertEquals(VrCommand.MoveSelection(1), press(r, VrKeys.DPAD_DOWN, 1000L))
        // The first OS repeat is accepted immediately...
        assertEquals(VrCommand.MoveSelection(1), press(r, VrKeys.DPAD_DOWN, 1060L))
        // ...then repeats are rate limited to menuRepeatMs.
        assertEquals(VrCommand.Consumed, press(r, VrKeys.DPAD_DOWN, 1100L))
        assertEquals(VrCommand.MoveSelection(1), press(r, VrKeys.DPAD_DOWN, 1210L))
    }

    /** OS key-repeat on A/B/SELECT must never confirm or close the panel repeatedly. */
    @Test
    fun heldActionButtonsDoNotRepeatInTheMenu() {
        val r = VrGamepadRouter().also { it.vrEnabled = true; it.menuOpen = true }
        assertEquals(VrCommand.Confirm, press(r, VrKeys.BUTTON_A, 1000L))
        assertEquals(VrCommand.Consumed, press(r, VrKeys.BUTTON_A, 2000L))
        assertEquals(VrCommand.CloseMenu, press(r, VrKeys.BUTTON_B, 3000L))
        assertEquals(VrCommand.Consumed, press(r, VrKeys.BUTTON_B, 4000L))
    }

    @Test
    fun keyRepeatHonoursTheSafetyFloor() {
        val r = VrGamepadRouter().also {
            it.vrEnabled = true
            it.menuOpen = true
            it.repeatMs = 10L
        }
        assertEquals(VrCommand.MoveSelection(-1), press(r, VrKeys.DPAD_UP, 1000L))
        assertEquals(VrCommand.MoveSelection(-1), press(r, VrKeys.DPAD_UP, 1050L))
        // 30 ms is below the 80 ms floor even though repeatMs says 10 ms.
        assertEquals(VrCommand.Consumed, press(r, VrKeys.DPAD_UP, 1080L))
        assertEquals(VrCommand.MoveSelection(-1), press(r, VrKeys.DPAD_UP, 1140L))
    }
}
