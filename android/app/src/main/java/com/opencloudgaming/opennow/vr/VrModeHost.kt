package com.opencloudgaming.opennow.vr

import android.util.Log
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
import androidx.compose.runtime.remember
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
 *
 * LIFECYCLE (root cause fix, 2026-11-06) — DO NOT reintroduce a shared viewRef:
 *  An earlier version held `var viewRef` at VrModeHost scope and cleaned it up in
 *  `DisposableEffect(enabled).onDispose`. On a false->true transition Compose disposes the OLD
 *  (key=false) effect, but by the time that onDispose runs AndroidView.factory has already stored
 *  the NEW view in the shared viewRef — so the stale onDispose paused+destroyed the freshly
 *  created VrTheaterView (onResume() then onPause()/onDestroy() ~3 ms later). The GL surface never
 *  got a chance to run onSurfaceCreated/onDrawEye, which is why "VR shows nothing".
 *  The fix: [VrTheaterHost] exists ONLY while enabled==true and owns its view in a LOCAL
 *  reference with DisposableEffect(Unit). Leaving the composition (enabled==false) disposes that
 *  one effect and cleans up exactly the view that belongs to it — no cross-transition aliasing.
 */
@Composable
fun VrModeHost() {
    val enabled by VrSession.enabled.collectAsStateWithLifecycle()
    val settings by VrSession.settings.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (enabled) {
            VrTheaterHost(settings)
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
}

/**
 * Exists only while VR is on. Keeps the VrTheaterView reference local so its disposal can never
 * touch a view created by a later composition. `DisposableEffect(Unit)` means the effect is tied
 * to THIS composable's lifetime, not to the `enabled` value shared with a sibling composition.
 */
@Composable
private fun VrTheaterHost(settings: VrSettings) {
    // Local holder: survives recompositions of VrTheaterHost, dies with it.
    val holder = remember { VrTheaterHolder() }

    AndroidView(
        factory = { context ->
            Log.d("VrModeHost", "AndroidView.factory: creating VrTheaterView")
            VrTheaterView(context).also { view ->
                holder.view = view
                Log.d("VrModeHost", "AndroidView.factory: VrTheaterView created; attaching start-on-attach listener")
                VrSession.onScanQr = { view.scanViewerQrCode() }
                // CardboardView.onResume() must run once the GL surface is attached.
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(attached: View) {
                        Log.d("VrModeHost", "onViewAttachedToWindow: calling view.start()")
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

    DisposableEffect(Unit) {
        onDispose {
            Log.d("VrModeHost", "VrTheaterHost.onDispose: stopping and releasing its own view")
            holder.view?.let { view ->
                view.stop()
                view.release()
            }
            holder.view = null
            VrSession.onScanQr = null
            VrSession.setMenuOpen(false)
            VrVideoSurfaceRegistry.clear()
        }
    }
}

/** Mutable holder kept in remember(); lets factory and onDispose share one local reference. */
private class VrTheaterHolder {
    var view: VrTheaterView? = null
}
