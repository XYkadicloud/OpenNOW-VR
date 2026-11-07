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

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingSwitch(
            label = "VR 模式（半 VR 影院）",
            checked = enabled,
            description = "把平面 2D 串流画面放进纯黑 VR 空间。开启后按系统返回键或手柄快捷键即可退出，串流不会中断，" +
                "退出后可直接使用手机软键盘。VR 内操作只用物理手柄，没有眼睛/凝视操控。",
        ) { VrSession.setEnabled(it) }

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
            description = "只翻转串流画面（上下），不影响状态面板、菜单和 360° 背景。与左右镜像可叠加（等于旋转 180°）。",
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
            label = "360° 背景图（VR 环境）",
            checked = settings.backgroundEnabled,
            description = "在纯黑背景之外叠加一张全景图作为 VR 环境。默认关闭；关闭时完全不占用额外显存与绘制。",
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

        TextButton(onClick = { VrSession.updateSettings(VrSettings.DEFAULT) }) {
            Text("恢复默认")
        }
        Text(
            text = "串流画面始终是平面 2D（不做左右眼不同内容 / SBS）。若朝向不对，分别试开「左右镜像」和「上下翻转」，两项可叠加，默认都关、随时可恢复。" +
                "「瞳距 / IPD 缩放」1.00x = 纸盒设备默认值；调小左右眼更容易重合、调大立体感更强，随时可恢复。" +
                "360° 背景默认关闭，开启后为可选的静态全景环境，不影响串流解码通路。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
