package com.opencloudgaming.opennow.vr

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Compose host placed AFTER OpenNowApp() in MainActivity's root Box.
 *
 * When VR is off this is a pass-through Box plus two small buttons (VR settings and enter VR),
 * so the original app keeps all of its behaviour and touch handling. When VR is on, a
 * CardboardView covers the screen; the buttons stay above it and the settings dialog is a separate
 * window, so the PHONE-SIDE settings remain reachable even while VR is rendering.
 *
 * The entry/exit button is ONLY an affordance (and a safety exit when no gamepad is connected).
 * All in-VR control is physical gamepad input — no gaze/eye interaction exists.
 */
@Composable
fun VrModeHost() {
    val enabled by VrSession.enabled.collectAsStateWithLifecycle()
    val settings by VrSession.settings.collectAsStateWithLifecycle()
    var viewRef by remember { mutableStateOf<VrTheaterView?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (enabled) {
            AndroidView(
                factory = { context ->
                    VrTheaterView(context).also { view ->
                        viewRef = view
                        VrSession.onScanQr = { view.scanViewerQrCode() }
                        // CardboardView.onResume() must run once the GL surface is attached.
                        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(attached: View) {
                                view.removeOnAttachStateChangeListener(this)
                                view.start()
                            }
                            override fun onViewDetachedFromWindow(detached: View) = Unit
                        })
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view -> view.applySettings(settings) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VrHostButton(text = "VR 设置") { showSettings = true }
            VrHostButton(text = if (enabled) "退出 VR" else "进入 VR") { VrSession.toggle() }
        }
    }

    // A Compose dialog lives in its own window, so the phone-side controls stay usable even when
    // the full-screen CardboardView is rendering.
    if (showSettings) {
        VrPhoneSettingsDialog(onDismiss = { showSettings = false })
    }

    DisposableEffect(enabled) {
        onDispose {
            viewRef?.let { view ->
                view.stop()
                view.release()
            }
            viewRef = null
            VrSession.onScanQr = null
            VrSession.setMenuOpen(false)
            VrVideoSurfaceRegistry.clear()
        }
    }
}

@Composable
private fun VrHostButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .alpha(0.72f)
            .background(Color(0x99000000), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text = text, color = Color.White, fontSize = 12.sp)
    }
}
