package com.opencloudgaming.opennow.vr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

        TextButton(onClick = { VrSession.updateSettings(VrSettings.DEFAULT) }) {
            Text("恢复默认")
        }
        Text(
            text = "串流画面始终是平面 2D（不做左右眼不同内容 / SBS）；黑色背景不渲染 3D 场景。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
