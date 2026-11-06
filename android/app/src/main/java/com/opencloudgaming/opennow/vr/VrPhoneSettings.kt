package com.opencloudgaming.opennow.vr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * PHONE-SIDE (flat 2D) VR settings panel.
 *
 * The user explicitly asked to control VR settings directly from the phone, without entering VR:
 * 「设置平面手机这边直接就能控制 VR 这边的具体设置」. This dialog is reachable from VrModeHost at
 * all times (VR on or off), renders every numeric control from [VrSettingSpecs], and writes every
 * change straight through [VrSession.updateSettings] — which sanitises, persists to
 * SharedPreferences and re-syncs the gamepad router. The VR renderer observes the same StateFlow,
 * so a change made here takes effect immediately in VR.
 *
 * No gaze/eye interaction exists: this is an ordinary touch UI on the flat phone screen, and the
 * in-VR control path remains purely physical gamepad input.
 */
@Composable
fun VrPhoneSettingsDialog(onDismiss: () -> Unit) {
    val settings by VrSession.settings.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("VR 设置（手机端）") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "不进入 VR 也能调整；改动立即保存，进入 VR 时直接生效。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))

                VrSettingSpecs.ALL.forEach { spec ->
                    SliderRow(
                        spec = spec,
                        value = spec.read(settings),
                        onChange = { raw ->
                            VrSession.updateSettings(spec.write(settings, spec.quantize(raw)))
                        },
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "模式与开关",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                SwitchRow(
                    label = "单目省电模式（低端机推荐）",
                    checked = settings.viewerMode == VrViewerMode.SIMULATION,
                    onChange = { checked ->
                        VrSession.updateSettings(
                            settings.copy(
                                viewerMode = if (checked) VrViewerMode.SIMULATION else VrViewerMode.CARDBOARD,
                            ),
                        )
                    },
                )
                SwitchRow(
                    label = "头部追踪（仅转动视角）",
                    checked = settings.headTrackingEnabled,
                    onChange = { checked ->
                        VrSession.updateSettings(settings.copy(headTrackingEnabled = checked))
                    },
                )
                SwitchRow(
                    label = "显示状态信息",
                    checked = settings.showStatus,
                    onChange = { checked ->
                        VrSession.updateSettings(settings.copy(showStatus = checked))
                    },
                )
                Text(
                    text = "单目模式只画一只眼并跳过镜头畸变合成，是低端机最有效的一档；" +
                        "纸盒 VR 请保持关闭（Cardboard 双目）。头部姿态只用于视角，不用于任何确认操作。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = { VrSession.updateSettings(VrSettings.DEFAULT) }) {
                Text("恢复默认")
            }
        },
    )
}

@Composable
private fun SliderRow(
    spec: VrSettingSpec,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = spec.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = spec.format(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value.coerceIn(spec.min, spec.max),
            onValueChange = onChange,
            valueRange = spec.min..spec.max,
            steps = spec.sliderSteps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
