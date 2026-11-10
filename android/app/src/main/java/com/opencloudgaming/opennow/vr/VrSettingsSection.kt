package com.opencloudgaming.opennow.vr

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencloudgaming.opennow.NumberSlider
import com.opencloudgaming.opennow.SettingSwitch

/**
 * NATIVE settings-page version of the phone-side VR controls.
 *
 * The user explicitly asked to move VR settings INTO the original OpenNOW settings screen rather
 * than showing a permanent floating "VR 设置" button over the picture. This composable uses the
 * very same upstream controls (`SettingSwitch` / `NumberSlider`) as every other settings section,
 * so it inherits the app's own styling, search and controller navigation.
 *
 * Every change goes through [VrSession.updateSettings] (sanitise + SharedPreferences + gamepad
 * router sync). The VR renderer observes the same StateFlow, so values apply immediately even
 * while VR is rendering.
 */
@Composable
fun VrSettingsSection() {
    val settings by VrSession.settings.collectAsStateWithLifecycle()
    val enabled by VrSession.enabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ACTION_OPEN_DOCUMENT + takePersistableUriPermission keeps the imported panorama readable
    // across app restarts (the URI is persisted in SharedPreferences via VrSession.updateSettings).
    val pickPanorama = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            VrSession.updateSettings(
                VrSession.settings.value.copy(backgroundImageUri = uri.toString()),
            )
        }
    }

    // CAMERA permission is requested centrally: VrSession.updateSettings() fires the OFF -> ON
    // edge and calls VrSession.requestCameraPermission (registered by MainActivity). This keeps
    // ONE dialog for all three entry points (this 2D page, the in-VR 环境 menu, and the L2+R2
    // shortcut). A denial restores the switch to off and the renderer falls back to black —
    // no retry loop, no forced permission.

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingSwitch(
            label = "VR 模式（半 VR 影院）",
            checked = enabled,
            description = "把平面 2D 串流画面放进纯黑 VR 空间。退出方式只有两种：手机系统返回键，" +
                "或 VR 设置菜单「快捷 → 退出 VR」按 A；L1+R1 只负责进入，不会误把你踢出。" +
                "串流不会中断，退出后可直接使用手机软键盘。VR 内操作只用物理手柄，没有眼睛/凝视操控。",
        ) { VrSession.setEnabled(it) }

        SettingSwitch(
            label = "画面上下填满（单纯放大）",
            checked = settings.screenFillMode == VrScreenFillMode.FILL,
            description = "开启：完全保留原来的双目立体 + 纸盒镜头畸变，只把最终画面整体等比放大，" +
                "把上下大黑边顶出屏幕；双眼仍然重叠，画面不会变成方形、也不会去掉桶形校正。" +
                "放大量由下面的「整体放大」滑块控制，1.00x 等于完全不放大。" +
                "关闭：一键回到 Cardboard 原版（四周有黑边，适配纸盒镜片）。" +
                "只保证上下填满，左右允许留一点黑边；放大过大时边缘会被裁掉，随时可调回。",
        ) { checked ->
            VrSession.updateSettings(
                settings.copy(
                    screenFillMode = if (checked) VrScreenFillMode.FILL else VrScreenFillMode.DISTORTED,
                ),
            )
        }

        VrSettingSpecs.ALL.forEach { spec ->
            NumberSlider(
                label = spec.label,
                value = spec.read(settings),
                min = spec.min,
                max = spec.max,
                step = spec.step,
                unit = spec.unit.takeIf { it.isNotEmpty() },
            ) { value ->
                VrSession.updateSettings(spec.write(settings, spec.quantize(value)))
            }
        }

        SettingSwitch(
            label = "单目省电模式（低端机推荐）",
            checked = settings.viewerMode == VrViewerMode.SIMULATION,
            description = "只画一只眼并跳过镜头畸变合成，是低端机最有效的一档。纸盒 VR 请保持关闭（Cardboard 双目）。",
        ) { checked ->
            VrSession.updateSettings(
                settings.copy(
                    viewerMode = if (checked) VrViewerMode.SIMULATION else VrViewerMode.CARDBOARD,
                ),
            )
        }
        SettingSwitch(
            label = "头部追踪（仅转动视角）",
            checked = settings.headTrackingEnabled,
        ) { checked ->
            VrSession.updateSettings(settings.copy(headTrackingEnabled = checked))
        }
        SettingSwitch(
            label = "显示状态信息",
            checked = settings.showStatus,
        ) { checked ->
            VrSession.updateSettings(settings.copy(showStatus = checked))
        }

        SettingSwitch(
            label = "串流画面左右镜像",
            checked = settings.horizontalMirror,
            description = "只翻转串流画面（左右），不影响状态面板、菜单和 360° 背景。随时可关回；不改 shader。",
        ) { checked ->
            VrSession.updateSettings(settings.copy(horizontalMirror = checked))
        }
        SettingSwitch(
            label = "串流画面上下翻转",
            checked = settings.verticalFlip,
            description = "只翻转串流画面（上下），不影响状态面板、菜单和 360° 背景。串流纹理天然上下倒置，所以默认开 = 正立画面；关掉可看原始采样。与左右镜像可叠加（等于旋转 180°）。",
        ) { checked ->
            VrSession.updateSettings(settings.copy(verticalFlip = checked))
        }
        SettingSwitch(
            label = "中心对齐辅助线",
            checked = settings.showCenterAlignmentLine,
            description = "屏幕正中央一条从底部到中心的竖线，固定在 2D 屏幕层，不跟随头转动、不贴在 VR 幕布上。用来确认左右眼是否正常立体显示。",
        ) { checked ->
            VrSession.updateSettings(settings.copy(showCenterAlignmentLine = checked))
        }

        SettingSwitch(
            label = "摄像头透视（Passthrough）",
            checked = settings.passthroughEnabled,
            description = "把手机摄像头的实时画面放进 VR，不摘纸盒也能看见周围。手机不是 360° 摄像头，" +
                "所以画面是一个锁定正前方的居中窗口，窗口外仍是纯黑；串流幕布始终盖在窗口之上，不会被挡住。" +
                "窗口固定为横向 4:3 / 16:9（由下面的比例项决定），摄像头真实预览比例只决定居中裁切多少，" +
                "任何档位都不会把画面拉伸或变成竖屏；方向也可手动拨正。需要相机权限；无摄像头、权限被拒或摄像头被占用" +
                "（例如串流正在录制摄像头）时自动降级回原来的黑底，VR 不会黑屏或崩溃。" +
                "开启时优先于 360° 背景图，两者不会叠加。",
        ) { checked ->
            // VrSession.updateSettings() detects the OFF -> ON edge and asks MainActivity for the
            // CAMERA permission, so no local launcher is needed here.
            VrSession.updateSettings(settings.copy(passthroughEnabled = checked))
        }
        SettingSwitch(
            label = "透视使用前置摄像头",
            checked = settings.passthroughCameraFacing == VrPassthroughFacing.FRONT,
            description = "默认后置（朝纸盒外侧，看环境）。若你的纸盒没有后置开孔导致画面全黑，改开此项用前置镜头；" +
                "切换会重新打开摄像头，随时可切回。",
        ) { checked ->
            VrSession.updateSettings(
                settings.copy(
                    passthroughCameraFacing = if (checked) {
                        VrPassthroughFacing.FRONT
                    } else {
                        VrPassthroughFacing.BACK
                    },
                ),
            )
        }
        SettingSwitch(
            label = "透视窗口使用 16:9 比例",
            checked = settings.passthroughAspect == VrPassthroughAspect.SIXTEEN_NINE,
            description = "默认关闭 = 4:3，这是多数手机摄像头的传感器原生比例，裁切最少、垂直视野更大，推荐保持。" +
                "若你的设备只提供 16:9 预览尺寸、或希望窗口更扁，可打开此项；窗口始终是横向的，" +
                "预览比例不同时只做居中裁切，不会拉伸画面，也绝不会变成竖屏 9:16。",
        ) { checked ->
            VrSession.updateSettings(
                settings.copy(
                    passthroughAspect = if (checked) {
                        VrPassthroughAspect.SIXTEEN_NINE
                    } else {
                        VrPassthroughAspect.FOUR_THREE
                    },
                ),
            )
        }

        // The three passthrough LAYOUTS are settings, not shortcut steps. L2+R2 only flips the
        // ON/OFF switch, so the user picks the look here (or in the in-VR 环境 page) and it stays.
        val modeLabel = when (settings.passthroughMode) {
            VrPassthroughMode.OVERLAY -> "叠加（摄像头在后、游戏居中覆盖）"
            VrPassthroughMode.CAMERA_MAIN -> "摄像头为主（游戏缩到右下角小窗）"
            VrPassthroughMode.SIDE_BY_SIDE -> "双窗口并排（左摄像头 / 右游戏）"
        }
        TextButton(onClick = {
            val next = when (settings.passthroughMode) {
                VrPassthroughMode.OVERLAY -> VrPassthroughMode.CAMERA_MAIN
                VrPassthroughMode.CAMERA_MAIN -> VrPassthroughMode.SIDE_BY_SIDE
                VrPassthroughMode.SIDE_BY_SIDE -> VrPassthroughMode.OVERLAY
            }
            VrSession.updateSettings(settings.copy(passthroughMode = next))
        }) {
            Text("透视布局：$modeLabel（点击切换）")
        }
        Text(
            "三种样式都保持各自比例、不拉伸：叠加 = 4:3 摄像头窗口在正前方、16:9 游戏幕布居中盖在上面；" +
                "摄像头为主 = 摄像头放大为主画面、游戏缩成右下角小窗；双窗口并排 = 左摄像头、右游戏互不重叠。" +
                "长按手柄 L2+R2（LT/RT）只负责开启 / 关闭透视，不会改动这里选好的样式。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // --- 真机反馈修复：方向错 / 直接拉伸 / 缺分辨率与帧率 ---------------------------------
        val resolutionLabel = when (settings.passthroughResolution) {
            VrPassthroughResolution.P360 -> "360P"
            VrPassthroughResolution.P480 -> "480P（默认）"
            VrPassthroughResolution.P720 -> "720P"
            VrPassthroughResolution.P1080 -> "1080P"
        }
        TextButton(onClick = {
            val all = VrPassthroughResolution.values()
            val next = all[(settings.passthroughResolution.ordinal + 1) % all.size]
            VrSession.updateSettings(settings.copy(passthroughResolution = next))
        }) {
            Text("透视分辨率：$resolutionLabel（点击切换）")
        }
        Text(
            "按短边像素分档，默认 480P。设备不一定支持精确尺寸，App 会从 Camera2 实际支持的横向预览尺寸里" +
                "挑最接近、比例一致的一档；真实宽高只用来决定居中裁切多少，窗口比例永远由你选的 4:3 / 16:9 决定，" +
                "所以任何档位都不会拉伸、也不会变成竖屏。360P 最省电，720P / 1080P 更清晰但更吃性能，低端机建议留在 480P。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        val fpsLabel = when (settings.passthroughFrameRate) {
            VrPassthroughFrameRate.FPS_15 -> "15 帧（省电）"
            VrPassthroughFrameRate.FPS_30 -> "30 帧（默认）"
            VrPassthroughFrameRate.FPS_60 -> "60 帧"
        }
        TextButton(onClick = {
            val all = VrPassthroughFrameRate.values()
            val next = all[(settings.passthroughFrameRate.ordinal + 1) % all.size]
            VrSession.updateSettings(settings.copy(passthroughFrameRate = next))
        }) {
            Text("透视帧率：$fpsLabel（点击切换）")
        }
        Text(
            "写入 Camera2 的自动曝光 FPS 区间，三档循环：15 帧省电（透视只作粗略环境参考时最省）、" +
                "30 帧默认、60 帧给支持的机型。设备达不到所选档位时自动回落到最接近的稳定区间，" +
                "不会黑屏；同类上限相同时优先固定区间（如 [30,30] 而非 [15,30]），避免低光掉帧。" +
                "状态面板显示的是实际生效帧率，不是你选的档位。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        val rotationLabel = when (settings.passthroughRotation) {
            VrPassthroughRotation.AUTO -> "自动（0°·横屏已正立）"
            VrPassthroughRotation.R0 -> "0°"
            VrPassthroughRotation.R90 -> "90°"
            VrPassthroughRotation.R180 -> "180°"
            VrPassthroughRotation.R270 -> "270°"
        }
        TextButton(onClick = {
            val all = VrPassthroughRotation.values()
            val next = all[(settings.passthroughRotation.ordinal + 1) % all.size]
            VrSession.updateSettings(settings.copy(passthroughRotation = next))
        }) {
            Text("透视画面旋转：$rotationLabel（点击切换）")
        }
        Text(
            "摄像头方向不对时在这里拨：AUTO = 0°，因为 VR 强制横屏时 Camera2 预览缓冲本身已经正立，" +
                "SENSOR_ORIENTATION 只是传感器相对竖屏自然方向的安装角，不该再额外补偿（之前两个版本都多转了 90°，" +
                "才出现方向错和竖屏 9:16 拉伸）。仍不对就按顺序试 0° / 90° / 180° / 270°，对照现实场景选正立的那一档。" +
                "旋转只改变采样哪部分画面，窗口永远横向 4:3 / 16:9，多余部分居中裁掉，绝不拉伸。" +
                "VR 状态面板会显示当前生效角度、实际预览尺寸与窗口比例。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        SettingSwitch(
            label = "透视画面额外左右镜像",
            checked = settings.passthroughMirror,
            description = "在前置摄像头自动镜像之外再做一次水平翻转。若画面左右反了（文字是反的），打开它；" +
                "前后置镜头都适用，随时可关回。",
        ) { checked ->
            VrSession.updateSettings(settings.copy(passthroughMirror = checked))
        }
        SettingSwitch(
            label = "透视窗口钉在眼前",
            checked = settings.passthroughHeadLocked,
            description = "默认关闭：摄像头窗口与游戏幕布共用同一个空间变换，转头时两层一起移动，不会互相" +
                "滑动，这是真机反馈「很大的不协调感」的修复。打开则恢复旧行为——窗口始终钉在双眼正前方，" +
                "游戏幕布随头移动，两层会相对滑动。两种都保留，按自己不晕的那一档选。",
        ) { checked ->
            VrSession.updateSettings(settings.copy(passthroughHeadLocked = checked))
        }

        SettingSwitch(
            label = "360° 背景图（VR 环境）",
            checked = settings.backgroundEnabled,
            description = "在纯黑背景之外叠加一张全景图作为 VR 环境。默认关闭；关闭时完全不占用额外显存与绘制。" +
                "摄像头透视开启且有画面时，此项会被自动让位（两者都是背景层，不叠加）。",
        ) { checked ->
            VrSession.updateSettings(settings.copy(backgroundEnabled = checked))
        }
        NumberSlider(
            label = "背景亮度",
            value = settings.backgroundBrightness,
            min = VrRanges.BACKGROUND_BRIGHTNESS_MIN,
            max = VrRanges.BACKGROUND_BRIGHTNESS_MAX,
            step = VrRanges.BACKGROUND_BRIGHTNESS_STEP,
            unit = null,
        ) { value ->
            VrSession.updateSettings(settings.copy(backgroundBrightness = value))
        }
        TextButton(onClick = { pickPanorama.launch(arrayOf("image/*")) }) {
            Text("导入 360° 全景图（推荐 2:1 等距圆柱图片）")
        }
        TextButton(onClick = { VrSession.updateSettings(settings.copy(backgroundImageUri = "")) }) {
            Text("清除已导入图片，改用内置星空")
        }
        TextButton(onClick = { VrSession.recenter() }) {
            Text("重置中心点（头部追踪校准）")
        }

        TextButton(onClick = { VrSession.updateSettings(VrSettings.DEFAULT) }) {
            Text("恢复默认")
        }
        Text(
            text = "串流画面始终是平面 2D（不做左右眼不同内容 / SBS）。「上下翻转」默认开：串流纹理天然上下倒置，这样新装、恢复默认都是正立画面；若仍朝向不对，再试「左右镜像」，两项可叠加、随时可关回。" +
                "「瞳距 / IPD 缩放」1.00x = 完全不改动（SDK 原版画面）；调小左右眼画面靠拢更容易重合、调大分离立体感更强，随时可恢复。该项作用于左右眼的最终画面位移，不改动 SDK 的瞳距/畸变参数。" +
                "「整体放大」1.00x = 原版输出，默认 1.35x，可一直调到 2.50x；往上调逐步把上下黑边顶满，若还看到大黑边就继续加大。" +
                "这是纯最终画面后处理，不改 FOV / 投影 / 几何 / shader，双眼始终保持原来的重叠关系；打开 VR 设置菜单时会自动暂停放大，菜单不会被裁。" +
                "360° 背景默认关闭，开启后为可选的静态全景环境，不影响串流解码通路。" +
                "「摄像头透视」是单目实时画面，不是 360° 环境：它以固定窗口显示在正前方（窗口大小、可见度、4:3 / 16:9 比例、" +
                "360P / 480P / 720P / 1080P 分辨率、15 / 30 / 60 帧、0° / 90° / 180° / 270° 旋转、额外镜像都可调），" +
                "默认 4:3 + 480P + 30 帧。窗口固定横向；真实预览宽高原样上报（竖屏 buffer 也如实处理），" +
                "0° / 90° / 180° / 270° 任一旋转下都按窗口比例居中裁切，绝不拉伸、不会变成竖屏。" +
                "方向不对用「透视画面旋转」拨正；状态面板第二行显示「采样比例（应 x.xx）」，两个数一致就说明没拉伸。" +
                "窗口外保持纯黑，串流画面始终在最上层。" +
                "默认「跟随幕布」：摄像头窗口与幕布共用同一空间变换，转头不相对滑动（真机反馈的不协调感修复）；" +
                "想要旧行为可打开「透视窗口钉在眼前」。左右眼仍有正确视差，看起来像正前方开了一扇窗。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
