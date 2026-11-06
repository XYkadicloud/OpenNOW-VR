package com.opencloudgaming.opennow

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Pins a single game so it can be started with one click from outside the app.
 *
 * Two mechanisms, because they cover different hardware:
 *
 * 1. [ShortcutManagerCompat.requestPinShortcut] — the proper "add to home screen" flow. Works on
 *    phones and on launchers that implement `CONFIRM_PIN_SHORTCUT`.
 * 2. A pre-declared `activity-alias` slot — for TV launchers (AQUOS, and most vendor launchers)
 *    that never implement pinning at all. Enabling an alias makes it appear in the launcher's app
 *    list as its own icon; the alias name is mapped back to a game id when it is launched.
 *
 * Both routes end in the same `opennow://launch/<id>` deep link, so [MainActivity] and
 * [OpenNowViewModel.handleExternalLaunchIntent] stay the single owner of "start this game".
 */
internal object GameShortcuts {

    const val SLOT_COUNT = 6

    data class PinResult(val kind: Kind, val slot: Int = 0) {
        enum class Kind { Pinned, ChannelAdded, SlotAdded, AlreadyAdded, NoSlot, Failed }
    }

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            runCatching { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }.getOrDefault(false)

    suspend fun pin(context: Context, game: GameInfo): PinResult {
        existingSlot(context, game.id)?.let { return PinResult(PinResult.Kind.AlreadyAdded, it) }
        // On a TV the pinned-shortcut API is a dead end: the framework may claim support while the
        // vendor launcher accepts the request and draws nothing. Publish to the TV provider instead,
        // which is the mechanism Android TV actually renders.
        if (TvChannels.isAndroidTv(context)) {
            if (TvChannels.publish(context, game)) return PinResult(PinResult.Kind.ChannelAdded)
            return pinAliasSlot(context, game)
        }
        if (isSupported(context)) {
            if (pinLauncherShortcut(context, game)) return PinResult(PinResult.Kind.Pinned)
        }
        return pinAliasSlot(context, game)
    }

    /** Maps a launch component back to the game it was pinned for, if it is one of our aliases. */
    fun gameIdForLaunchComponent(context: Context, component: ComponentName?): String? {
        val className = component?.className ?: return null
        val slot = slotFromAlias(className) ?: return null
        return prefs(context).getString(slotKey(slot), null)?.takeIf { it.isNotBlank() }
    }

    private fun pinLauncherShortcut(context: Context, game: GameInfo): Boolean {
        val shortcut = runCatching {
            ShortcutInfoCompat.Builder(context, "game_" + game.id.replace(Regex("[^A-Za-z0-9_.-]"), "_"))
                .setShortLabel(game.title.take(40))
                .setLongLabel(game.title)
                .setIcon(IconCompat.createWithResource(context, R.drawable.opennow_icon))
                .setIntent(launchIntent(context, game))
                .setRank(0)
                .build()
        }.getOrNull() ?: return false
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, shortcut, null) }
            .getOrDefault(false)
    }

    private fun pinAliasSlot(context: Context, game: GameInfo): PinResult {
        val prefs = prefs(context)
        val slot = (1..SLOT_COUNT).firstOrNull { prefs.getString(slotKey(it), null).isNullOrBlank() }
            ?: return PinResult(PinResult.Kind.NoSlot)
        val enabled = runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, aliasClassName(slot)),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            true
        }.getOrDefault(false)
        if (!enabled) return PinResult(PinResult.Kind.Failed)
        prefs.edit().putString(slotKey(slot), game.id).apply()
        return PinResult(PinResult.Kind.SlotAdded, slot)
    }

    private fun existingSlot(context: Context, gameId: String): Int? {
        val prefs = prefs(context)
        return (1..SLOT_COUNT).firstOrNull { prefs.getString(slotKey(it), null) == gameId }
    }

    private fun slotFromAlias(className: String): Int? {
        val simple = className.substringAfterLast('.')
        if (!simple.startsWith(ALIAS_PREFIX)) return null
        return simple.removePrefix(ALIAS_PREFIX).toIntOrNull()?.takeIf { it in 1..SLOT_COUNT }
    }

    private fun aliasClassName(slot: Int): String =
        "${MainActivity::class.java.packageName}.$ALIAS_PREFIX$slot"

    private fun slotKey(slot: Int) = "shortcut_slot_$slot"

    private fun prefs(context: Context) =
        context.getSharedPreferences("opennow_ui", Context.MODE_PRIVATE)

    private fun launchIntent(context: Context, game: GameInfo): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("opennow://launch/${Uri.encode(game.id)}")
            // The id extra is what [OpenNowViewModel.extractExternalLaunchId] reads first, so a game
            // whose id contains characters that do not survive a URI round trip still resolves.
            putExtra("id", game.id)
            putExtra("title", game.title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    private const val ALIAS_PREFIX = "GameShortcut"
}
