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

    @Test
    fun selectPlusStartComboAlsoTogglesVr() {
        val r = VrGamepadRouter()
        assertEquals(VrCommand.PassThrough, press(r, VrKeys.BUTTON_SELECT))
        assertEquals(VrCommand.ToggleVrMode, press(r, VrKeys.BUTTON_START))
    }

    @Test
    fun menuKeyOnlyWorksInsideVr() {
        val r = VrGamepadRouter().also { it.vrEnabled = true }
        assertEquals(VrCommand.ToggleMenu, press(r, VrKeys.BUTTON_SELECT))
        r.menuOpen = true
        assertEquals(VrCommand.Confirm, press(r, VrKeys.BUTTON_A))
        assertEquals(VrCommand.CloseMenu, press(r, VrKeys.BUTTON_B))
    }

    /** SELECT/START are also the exit-VR combo; inside the menu they must only close it. */
    @Test
    fun menuKeyClosesThePanelWhenItIsAlreadyOpen() {
        val r = VrGamepadRouter().also { it.vrEnabled = true; it.menuOpen = true }
        assertEquals(VrCommand.CloseMenu, press(r, VrKeys.BUTTON_SELECT))
        assertEquals(VrCommand.CloseMenu, press(r, VrKeys.BUTTON_START))
    }

    /** The L1+R1 toggle combo is disabled while the menu is open (shoulders are fast-adjust there). */
    @Test
    fun shoulderComboDoesNotToggleVrWhileTheMenuIsOpen() {
        val r = VrGamepadRouter().also { it.vrEnabled = true; it.menuOpen = true }
        assertEquals(VrCommand.Consumed, press(r, VrKeys.BUTTON_L1))
        assertEquals(VrCommand.Consumed, press(r, VrKeys.BUTTON_R1))
    }

    @Test
    fun openMenuRoutesDpadAndFaceButtonsOnlyToThePanel() {
        val r = VrGamepadRouter().also { it.vrEnabled = true; it.menuOpen = true }
        assertEquals(VrCommand.MoveSelection(-1), press(r, VrKeys.DPAD_UP))
        assertEquals(VrCommand.MoveSelection(1), press(r, VrKeys.DPAD_DOWN))
        assertEquals(VrCommand.Adjust(-1), press(r, VrKeys.DPAD_LEFT))
        assertEquals(VrCommand.Adjust(1), press(r, VrKeys.DPAD_RIGHT))
        assertEquals(VrCommand.Confirm, press(r, VrKeys.DPAD_CENTER))
        assertEquals(VrCommand.Consumed, press(r, VrKeys.BUTTON_X))
        assertEquals(VrCommand.Consumed, release(r, VrKeys.BUTTON_X))
    }

    @Test
    fun shoulderHeldMultipliesAdjustmentByFive() {
        val r = VrGamepadRouter().also { it.vrEnabled = true; it.menuOpen = true }
        // L2 is a fast-adjust modifier, not a VR-toggle combo key: it is swallowed by the panel.
        assertEquals(VrCommand.Consumed, press(r, VrKeys.BUTTON_L2))
        assertEquals(VrCommand.Adjust(-5), press(r, VrKeys.DPAD_LEFT))
        assertEquals(VrCommand.Adjust(5), press(r, VrKeys.DPAD_RIGHT))
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
    fun verticalAxisMovesSelectionWithRepeatDelay() {
        val r = VrGamepadRouter().also { it.menuOpen = true; it.repeatMs = 140L }
        assertEquals(VrCommand.MoveSelection(1), r.onAxis(0f, 0.9f, 1000L))
        assertEquals(VrCommand.Consumed, r.onAxis(0f, 0.9f, 1050L))
        assertEquals(VrCommand.MoveSelection(1), r.onAxis(0f, 0.9f, 1200L))
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
        assertEquals(VrCommand.MoveSelection(1), r.onAxis(0f, 0.9f, 1060L))
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
