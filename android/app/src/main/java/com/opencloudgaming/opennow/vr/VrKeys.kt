package com.opencloudgaming.opennow.vr

/**
 * Plain Int constants mirroring AOSP KeyEvent / InputEvent values.
 *
 * These deliberately do NOT reference android.view.KeyEvent: the JVM unit tests must be able to
 * exercise VrGamepadRouter without Android framework stubs ("Method ... not mocked").
 * Values are from AOSP KeyEvent and are stable across API levels.
 */
object VrKeys {
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1

    const val BACK = 4
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23

    const val BUTTON_A = 96
    const val BUTTON_B = 97
    const val BUTTON_X = 99
    const val BUTTON_Y = 100
    const val BUTTON_L1 = 102
    const val BUTTON_R1 = 103
    const val BUTTON_L2 = 104
    const val BUTTON_R2 = 105
    const val BUTTON_START = 108
    const val BUTTON_SELECT = 109
    const val BUTTON_MODE = 110

    const val SOURCE_DPAD = 0x00000201
    const val SOURCE_GAMEPAD = 0x00000401
    const val SOURCE_JOYSTICK = 0x01000010

    const val AXIS_X = 0
    const val AXIS_Y = 1

    /** Both pairs toggle VR mode on/off. Held together, not a sequence. */
    val VR_TOGGLE_COMBOS = listOf(
        intArrayOf(BUTTON_L1, BUTTON_R1),
        intArrayOf(BUTTON_SELECT, BUTTON_START),
    )

    /** Opens/closes the in-VR settings panel. */
    val MENU_KEYS = intArrayOf(BUTTON_SELECT, BUTTON_START, BUTTON_MODE)

    val FAST_ADJUST_KEYS = intArrayOf(BUTTON_L1, BUTTON_R1, BUTTON_L2, BUTTON_R2)
}
