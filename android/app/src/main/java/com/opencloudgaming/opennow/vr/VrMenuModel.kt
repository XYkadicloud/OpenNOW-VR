package com.opencloudgaming.opennow.vr

import java.util.Locale

/** Every adjustable/actionable row of the in-VR settings panel (gamepad-driven only). */
enum class VrMenuItemId {
    // 画面
    SCREEN_SCALE,
    SCREEN_DISTANCE,
    BRIGHTNESS,
    CURVATURE,
    VERTICAL_OFFSET,
    IPD_SCALE,
    SCREEN_FILL_MODE,
    HORIZONTAL_MIRROR,
    VERTICAL_FLIP,
    // 观看
    VIEWER_MODE,
    HEAD_TRACKING,
    HEAD_SENSITIVITY,
    CENTER_ALIGN_LINE,
    RECENTER,
    // 操作
    GAMEPAD_DEAD_ZONE,
    MENU_REPEAT,
    SHOW_STATUS,
    // 环境
    BACKGROUND,
    BACKGROUND_BRIGHTNESS,
    // 快捷
    SCAN_QR,
    RESET_DEFAULTS,
    EXIT_VR,
}

enum class VrMenuItemKind { RANGE, TOGGLE, ENUM, ACTION }

data class VrMenuItem(
    val id: VrMenuItemId,
    val label: String,
    val kind: VrMenuItemKind,
    val min: Float = 0f,
    val max: Float = 0f,
    val step: Float = 0f,
)

/** One second-level page (category chip) of the in-VR settings panel. */
data class VrMenuPage(
    val id: String,
    val title: String,
    val subtitle: String,
)

/** Outcome of pressing A on a row: the new settings and whether value-editing is now armed. */
data class VrConfirmResult(val settings: VrSettings, val editing: Boolean)

object VrMenuModel {
    val pages: List<VrMenuPage> = listOf(
        VrMenuPage("display", "画面", "幕布几何、亮度与填充方式"),
        VrMenuPage("view", "观看", "视角模式、头部追踪与辅助线"),
        VrMenuPage("input", "操作", "手柄与菜单响应"),
        VrMenuPage("env", "环境", "360° 背景"),
        VrMenuPage("quick", "快捷", "校准、恢复默认与退出"),
    )

    /**
     * Flat list, grouped by [pages] in order. Kept flat because VrSession.selectedIndex is a
     * global index; [pageRanges] maps it back onto the second-level pages.
     */
    val items: List<VrMenuItem> = listOf(
        // 画面 (0..8)
        VrMenuItem(VrMenuItemId.SCREEN_SCALE, "串流窗口大小", VrMenuItemKind.RANGE,
            VrRanges.SCREEN_SCALE_MIN, VrRanges.SCREEN_SCALE_MAX, VrRanges.SCREEN_SCALE_STEP),
        VrMenuItem(VrMenuItemId.SCREEN_DISTANCE, "幕布距离 (m)", VrMenuItemKind.RANGE,
            VrRanges.DISTANCE_MIN, VrRanges.DISTANCE_MAX, VrRanges.DISTANCE_STEP),
        VrMenuItem(VrMenuItemId.BRIGHTNESS, "画面亮度", VrMenuItemKind.RANGE,
            VrRanges.BRIGHTNESS_MIN, VrRanges.BRIGHTNESS_MAX, VrRanges.BRIGHTNESS_STEP),
        VrMenuItem(VrMenuItemId.CURVATURE, "幕布曲率", VrMenuItemKind.RANGE,
            VrRanges.CURVATURE_MIN, VrRanges.CURVATURE_MAX, VrRanges.CURVATURE_STEP),
        VrMenuItem(VrMenuItemId.VERTICAL_OFFSET, "画面高低 (m)", VrMenuItemKind.RANGE,
            VrRanges.VERTICAL_OFFSET_MIN, VrRanges.VERTICAL_OFFSET_MAX, VrRanges.VERTICAL_OFFSET_STEP),
        VrMenuItem(VrMenuItemId.IPD_SCALE, "瞳距 / IPD", VrMenuItemKind.RANGE,
            VrRanges.IPD_SCALE_MIN, VrRanges.IPD_SCALE_MAX, VrRanges.IPD_SCALE_STEP),
        VrMenuItem(VrMenuItemId.SCREEN_FILL_MODE, "画面填充", VrMenuItemKind.ENUM),
        VrMenuItem(VrMenuItemId.HORIZONTAL_MIRROR, "串流左右镜像", VrMenuItemKind.TOGGLE),
        VrMenuItem(VrMenuItemId.VERTICAL_FLIP, "串流上下翻转", VrMenuItemKind.TOGGLE),
        // 观看 (9..13)
        VrMenuItem(VrMenuItemId.VIEWER_MODE, "视角模式", VrMenuItemKind.ENUM),
        VrMenuItem(VrMenuItemId.HEAD_TRACKING, "头部追踪", VrMenuItemKind.TOGGLE),
        VrMenuItem(VrMenuItemId.HEAD_SENSITIVITY, "追踪灵敏度", VrMenuItemKind.RANGE,
            VrRanges.HEAD_SENSITIVITY_MIN, VrRanges.HEAD_SENSITIVITY_MAX, VrRanges.HEAD_SENSITIVITY_STEP),
        VrMenuItem(VrMenuItemId.CENTER_ALIGN_LINE, "中心辅助线", VrMenuItemKind.TOGGLE),
        VrMenuItem(VrMenuItemId.RECENTER, "重置中心点", VrMenuItemKind.ACTION),
        // 操作 (14..16)
        VrMenuItem(VrMenuItemId.GAMEPAD_DEAD_ZONE, "手柄死区", VrMenuItemKind.RANGE,
            VrRanges.DEAD_ZONE_MIN, VrRanges.DEAD_ZONE_MAX, VrRanges.DEAD_ZONE_STEP),
        VrMenuItem(VrMenuItemId.MENU_REPEAT, "摇杆重复 (ms)", VrMenuItemKind.RANGE,
            VrRanges.REPEAT_MIN_MS.toFloat(), VrRanges.REPEAT_MAX_MS.toFloat(), VrRanges.REPEAT_STEP_MS.toFloat()),
        VrMenuItem(VrMenuItemId.SHOW_STATUS, "显示状态", VrMenuItemKind.TOGGLE),
        // 环境 (17..18)
        VrMenuItem(VrMenuItemId.BACKGROUND, "360° 背景图", VrMenuItemKind.TOGGLE),
        VrMenuItem(VrMenuItemId.BACKGROUND_BRIGHTNESS, "背景亮度", VrMenuItemKind.RANGE,
            VrRanges.BACKGROUND_BRIGHTNESS_MIN, VrRanges.BACKGROUND_BRIGHTNESS_MAX,
            VrRanges.BACKGROUND_BRIGHTNESS_STEP),
        // 快捷 (19..21)
        VrMenuItem(VrMenuItemId.SCAN_QR, "纸盒扫码校准", VrMenuItemKind.ACTION),
        VrMenuItem(VrMenuItemId.RESET_DEFAULTS, "恢复默认", VrMenuItemKind.ACTION),
        VrMenuItem(VrMenuItemId.EXIT_VR, "退出 VR", VrMenuItemKind.ACTION),
    )

    /** Index range of each page inside [items]. Verified at class-init so a bad split fails fast. */
    private val pageRanges: List<IntRange> = listOf(0..8, 9..13, 14..16, 17..18, 19..21)

    init {
        require(pageRanges.size == pages.size) { "page count mismatch" }
        require(pageRanges.first().first == 0 && pageRanges.last().last == items.lastIndex) {
            "page ranges must cover exactly all items"
        }
        for (i in 1 until pageRanges.size) {
            require(pageRanges[i].first == pageRanges[i - 1].last + 1) { "page ranges must be contiguous" }
        }
    }

    fun pageIndex(page: VrMenuPage): Int = pages.indexOf(page).coerceAtLeast(0)

    fun itemsFor(page: VrMenuPage): List<VrMenuItem> {
        val range = pageRanges[pageIndex(page)]
        return items.subList(range.first, range.last + 1)
    }

    fun firstIndexOf(page: VrMenuPage): Int = pageRanges[pageIndex(page)].first

    fun lastIndexOf(page: VrMenuPage): Int = pageRanges[pageIndex(page)].last

    /** Clamps a global selection index into the given page, so D-pad never jumps categories. */
    fun clampToPage(index: Int, page: VrMenuPage): Int =
        index.coerceIn(firstIndexOf(page), lastIndexOf(page))

    fun rawValue(settings: VrSettings, id: VrMenuItemId): Float = when (id) {
        VrMenuItemId.SCREEN_SCALE -> settings.screenScale
        VrMenuItemId.SCREEN_DISTANCE -> settings.screenDistanceMeters
        VrMenuItemId.BRIGHTNESS -> settings.brightness
        VrMenuItemId.CURVATURE -> settings.curvature
        VrMenuItemId.VERTICAL_OFFSET -> settings.screenVerticalOffset
        VrMenuItemId.IPD_SCALE -> settings.ipdScale
        VrMenuItemId.SCREEN_FILL_MODE -> if (settings.screenFillMode == VrScreenFillMode.FILL) 1f else 0f
        VrMenuItemId.VIEWER_MODE -> if (settings.viewerMode == VrViewerMode.CARDBOARD) 0f else 1f
        VrMenuItemId.HEAD_TRACKING -> if (settings.headTrackingEnabled) 1f else 0f
        VrMenuItemId.HEAD_SENSITIVITY -> settings.headTrackingSensitivity
        VrMenuItemId.GAMEPAD_DEAD_ZONE -> settings.gamepadDeadZone
        VrMenuItemId.MENU_REPEAT -> settings.menuRepeatMs.toFloat()
        VrMenuItemId.SHOW_STATUS -> if (settings.showStatus) 1f else 0f
        VrMenuItemId.HORIZONTAL_MIRROR -> if (settings.horizontalMirror) 1f else 0f
        VrMenuItemId.VERTICAL_FLIP -> if (settings.verticalFlip) 1f else 0f
        VrMenuItemId.CENTER_ALIGN_LINE -> if (settings.showCenterAlignmentLine) 1f else 0f
        VrMenuItemId.BACKGROUND -> if (settings.backgroundEnabled) 1f else 0f
        VrMenuItemId.BACKGROUND_BRIGHTNESS -> settings.backgroundBrightness
        else -> 0f
    }

    fun display(settings: VrSettings, id: VrMenuItemId): String = when (id) {
        VrMenuItemId.SCREEN_SCALE -> String.format(Locale.US, "%.1f x", settings.screenScale)
        VrMenuItemId.SCREEN_DISTANCE -> String.format(Locale.US, "%.1f m", settings.screenDistanceMeters)
        VrMenuItemId.BRIGHTNESS -> String.format(Locale.US, "%.2f", settings.brightness)
        VrMenuItemId.CURVATURE -> String.format(Locale.US, "%.1f", settings.curvature)
        VrMenuItemId.VERTICAL_OFFSET -> String.format(Locale.US, "%+.1f m", settings.screenVerticalOffset)
        VrMenuItemId.IPD_SCALE -> String.format(Locale.US, "%.2f x", settings.ipdScale)
        VrMenuItemId.SCREEN_FILL_MODE -> if (settings.screenFillMode == VrScreenFillMode.FILL) {
            "上下填满·方形"
        } else {
            "原版（可回退）"
        }
        VrMenuItemId.VIEWER_MODE -> if (settings.viewerMode == VrViewerMode.CARDBOARD) "纸盒立体" else "裸眼单屏"
        VrMenuItemId.HEAD_TRACKING -> if (settings.headTrackingEnabled) "开" else "关"
        VrMenuItemId.HEAD_SENSITIVITY -> String.format(Locale.US, "%.1f", settings.headTrackingSensitivity)
        VrMenuItemId.GAMEPAD_DEAD_ZONE -> String.format(Locale.US, "%.2f", settings.gamepadDeadZone)
        VrMenuItemId.MENU_REPEAT -> "${settings.menuRepeatMs} ms"
        VrMenuItemId.SHOW_STATUS -> if (settings.showStatus) "开" else "关"
        VrMenuItemId.HORIZONTAL_MIRROR -> if (settings.horizontalMirror) "开" else "关"
        VrMenuItemId.VERTICAL_FLIP -> if (settings.verticalFlip) "开" else "关"
        VrMenuItemId.CENTER_ALIGN_LINE -> if (settings.showCenterAlignmentLine) "开" else "关"
        VrMenuItemId.BACKGROUND -> if (settings.backgroundEnabled) "开" else "关"
        VrMenuItemId.BACKGROUND_BRIGHTNESS -> String.format(Locale.US, "%.2f", settings.backgroundBrightness)
        VrMenuItemId.RECENTER -> "按 A 执行"
        VrMenuItemId.SCAN_QR -> "按 A 执行"
        VrMenuItemId.RESET_DEFAULTS -> "按 A 执行"
        VrMenuItemId.EXIT_VR -> "按 A 执行"
    }

    /**
     * Applies a relative change. `steps` is usually +/-1; +/-5 is used for the stick's vertical
     * axis. Values are always clamped by VrSettings.sanitized().
     */
    fun adjust(settings: VrSettings, id: VrMenuItemId, steps: Int): VrSettings {
        if (steps == 0) return settings
        val item = items.first { it.id == id }
        return when (item.kind) {
            VrMenuItemKind.RANGE -> {
                val next = rawValue(settings, id) + steps * item.step
                withValue(settings, id, next)
            }
            VrMenuItemKind.TOGGLE, VrMenuItemKind.ENUM -> toggle(settings, id)
            VrMenuItemKind.ACTION -> settings
        }.sanitized()
    }

    /**
     * The user's rule: numeric values may ONLY be changed after A has armed editing, and only by
     * the left stick. This pure function is the single gate, so JVM tests can lock the contract.
     */
    fun adjustIfEditing(settings: VrSettings, id: VrMenuItemId, steps: Int, editing: Boolean): VrSettings {
        if (!editing) return settings
        val item = items.first { it.id == id }
        if (item.kind != VrMenuItemKind.RANGE) return settings
        return adjust(settings, id, steps)
    }

    /**
     * The user's rule for A: on a RANGE row it arms/disarms value editing (no value change), on a
     * TOGGLE/ENUM row it flips immediately, on an ACTION row it executes. Pure and testable.
     */
    fun confirm(settings: VrSettings, id: VrMenuItemId, editing: Boolean): VrConfirmResult {
        val item = items.first { it.id == id }
        return when (item.kind) {
            VrMenuItemKind.RANGE -> VrConfirmResult(settings, !editing)
            VrMenuItemKind.TOGGLE, VrMenuItemKind.ENUM, VrMenuItemKind.ACTION ->
                VrConfirmResult(activate(settings, id), false)
        }
    }

    /** ACTION rows are executed here, never from gaze, timers or head pose. */
    fun activate(settings: VrSettings, id: VrMenuItemId): VrSettings = when (id) {
        VrMenuItemId.RESET_DEFAULTS -> VrSettings.DEFAULT
        VrMenuItemId.RECENTER -> { VrSession.recenter(); settings }
        VrMenuItemId.SCREEN_FILL_MODE, VrMenuItemId.VIEWER_MODE -> toggle(settings, id)
        VrMenuItemId.HEAD_TRACKING, VrMenuItemId.SHOW_STATUS,
        VrMenuItemId.HORIZONTAL_MIRROR, VrMenuItemId.VERTICAL_FLIP,
        VrMenuItemId.CENTER_ALIGN_LINE, VrMenuItemId.BACKGROUND -> toggle(settings, id)
        else -> settings
    }.sanitized()

    private fun toggle(settings: VrSettings, id: VrMenuItemId): VrSettings = when (id) {
        VrMenuItemId.VIEWER_MODE -> settings.copy(
            viewerMode = if (settings.viewerMode == VrViewerMode.CARDBOARD) {
                VrViewerMode.SIMULATION
            } else {
                VrViewerMode.CARDBOARD
            },
        )
        VrMenuItemId.SCREEN_FILL_MODE -> settings.copy(
            screenFillMode = if (settings.screenFillMode == VrScreenFillMode.FILL) {
                VrScreenFillMode.DISTORTED
            } else {
                VrScreenFillMode.FILL
            },
        )
        VrMenuItemId.HEAD_TRACKING -> settings.copy(headTrackingEnabled = !settings.headTrackingEnabled)
        VrMenuItemId.SHOW_STATUS -> settings.copy(showStatus = !settings.showStatus)
        VrMenuItemId.HORIZONTAL_MIRROR -> settings.copy(horizontalMirror = !settings.horizontalMirror)
        VrMenuItemId.VERTICAL_FLIP -> settings.copy(verticalFlip = !settings.verticalFlip)
        VrMenuItemId.CENTER_ALIGN_LINE -> settings.copy(showCenterAlignmentLine = !settings.showCenterAlignmentLine)
        VrMenuItemId.BACKGROUND -> settings.copy(backgroundEnabled = !settings.backgroundEnabled)
        else -> settings
    }

    private fun withValue(settings: VrSettings, id: VrMenuItemId, value: Float): VrSettings = when (id) {
        VrMenuItemId.SCREEN_SCALE -> settings.copy(screenScale = value)
        VrMenuItemId.SCREEN_DISTANCE -> settings.copy(screenDistanceMeters = value)
        VrMenuItemId.BRIGHTNESS -> settings.copy(brightness = value)
        VrMenuItemId.CURVATURE -> settings.copy(curvature = value)
        VrMenuItemId.VERTICAL_OFFSET -> settings.copy(screenVerticalOffset = value)
        VrMenuItemId.IPD_SCALE -> settings.copy(ipdScale = value)
        VrMenuItemId.HEAD_SENSITIVITY -> settings.copy(headTrackingSensitivity = value)
        VrMenuItemId.GAMEPAD_DEAD_ZONE -> settings.copy(gamepadDeadZone = value)
        VrMenuItemId.MENU_REPEAT -> settings.copy(menuRepeatMs = value.toLong())
        VrMenuItemId.BACKGROUND_BRIGHTNESS -> settings.copy(backgroundBrightness = value)
        else -> settings
    }
}
