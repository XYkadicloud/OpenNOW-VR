package com.opencloudgaming.opennow.vr

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
 * NO permanent floating settings UI: all VR controls live in the app's own settings screen
 * (`VrSettingsSection` inside `OpenNowSettingsScreens.kt`). While VR is on, nothing is drawn on
 * top of the picture at all — the user exits with the system BACK key or the gamepad shortcut,
 * which restores the flat 2D UI (and the soft keyboard) without interrupting the stream.
 *
 * The only overlay is a small "进入 VR" affordance shown in 2D mode, so a user without a gamepad
 * can still start VR. It disappears completely once VR is active.
 */
@Composable
fun VrModeHost() {
    val enabled by VrSession.enabled.collectAsStateWithLifecycle()
    val settings by VrSession.settings.collectAsStateWithLifecycle()
    var viewRef by remember { mutableStateOf<VrTheaterView?>(null) }

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
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .alpha(0.55f)
                    .background(Color(0x99000000), RoundedCornerShape(6.dp))
                    .clickable { VrSession.setEnabled(true) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(text = "进入 VR", color = Color.White, fontSize = 12.sp)
            }
        }
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
