package com.opencloudgaming.opennow

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Publishes games as Android TV "channels" and preview programs, the way Moonlight's
 * `TvChannelHelper` does.
 *
 * Pinned shortcuts are a phone concept: `requestPinShortcut` needs a launcher that implements
 * `CONFIRM_PIN_SHORTCUT`, and vendor TV launchers (AQUOS included) either lack it or accept the
 * request and draw nothing. The supported TV route is the system TV provider: one channel owned by
 * this package plus a preview program per game, which the launcher's recommendation row renders
 * with the box art and launches through [TvContract.PreviewPrograms.COLUMN_INTENT_URI].
 */
internal object TvChannels {

    private const val CHANNEL_PROVIDER_ID = "opennow"
    // Literals rather than TvContract constants: TYPE_GAME is not exposed by the public SDK and the
    // aspect-ratio constants were renamed between releases, but the wire values are stable.
    private const val TYPE_GAME = 12
    private const val ASPECT_RATIO_POSTER = 5

    fun isAndroidTv(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /** Returns true when the game now has a browsable preview program on the TV home screen. */
    suspend fun publish(context: Context, game: GameInfo): Boolean = withContext(Dispatchers.IO) {
        if (!isAndroidTv(context)) return@withContext false
        val channelId = ensureChannel(context) ?: return@withContext false
        val posterUri = cachePoster(context, game)
        val intentUri = launchIntent(context, game).toUri(Intent.URI_INTENT_SCHEME)
        val values = ContentValues().apply {
            put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
            put(TvContract.PreviewPrograms.COLUMN_TYPE, TYPE_GAME)
            put(TvContract.PreviewPrograms.COLUMN_TITLE, game.title)
            put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, game.id)
            put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, intentUri)
            put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, ASPECT_RATIO_POSTER)
            put(TvContract.PreviewPrograms.COLUMN_WEIGHT, currentWeight())
            game.publisherName?.takeIf { it.isNotBlank() }?.let {
                put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, it)
            }
            if (posterUri != null) put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, posterUri.toString())
        }
        val existing = findProgramId(context, channelId, game.id)
        val written = runCatching {
            if (existing != null) {
                context.contentResolver.update(previewProgramUri(existing), values, null, null)
            } else {
                context.contentResolver.insert(TvContract.PreviewPrograms.CONTENT_URI, values)
            }
            true
        }.getOrDefault(false)
        if (!written) return@withContext false
        // This is what puts the channel on the TV home screen; it shows a system confirmation.
        runCatching { TvContract.requestChannelBrowsable(context, channelId) }
        true
    }

    fun remove(context: Context, game: GameInfo) {
        if (!isAndroidTv(context)) return
        runCatching {
            val channelId = findChannelId(context) ?: return
            val programId = findProgramId(context, channelId, game.id) ?: return
            context.contentResolver.delete(previewProgramUri(programId), null, null)
        }
    }

    private fun ensureChannel(context: Context): Long? {
        val existing = findChannelId(context)
        val values = ContentValues().apply {
            put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
            put(TvContract.Channels.COLUMN_DISPLAY_NAME, context.getString(R.string.app_name))
            put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, CHANNEL_PROVIDER_ID)
            put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, appLinkIntentUri(context))
        }
        if (existing != null) {
            runCatching { context.contentResolver.update(channelUri(existing), values, null, null) }
            return existing
        }
        return runCatching {
            context.contentResolver.insert(TvContract.Channels.CONTENT_URI, values)
        }.getOrNull()?.let { uri: Uri -> runCatching { ContentUris.parseId(uri) }.getOrNull() }
    }

    private fun findChannelId(context: Context): Long? = runCatching {
        context.contentResolver.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == CHANNEL_PROVIDER_ID) return@use cursor.getLong(0)
            }
            null
        }
    }.getOrNull()

    private fun findProgramId(context: Context, channelId: Long, gameId: String): Long? = runCatching {
        context.contentResolver.query(
            TvContract.PreviewPrograms.CONTENT_URI,
            arrayOf(
                TvContract.PreviewPrograms._ID,
                TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID,
            ),
            "${TvContract.PreviewPrograms.COLUMN_CHANNEL_ID}=?",
            arrayOf(channelId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == gameId) return@use cursor.getLong(0)
            }
            null
        }
    }.getOrNull()

    private fun channelUri(channelId: Long): Uri =
        ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channelId)

    private fun previewProgramUri(programId: Long): Uri =
        ContentUris.withAppendedId(TvContract.PreviewPrograms.CONTENT_URI, programId)

    private fun appLinkIntentUri(context: Context): String =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .toUri(Intent.URI_INTENT_SCHEME)

    private fun launchIntent(context: Context, game: GameInfo): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("opennow://launch/${Uri.encode(game.id)}")
            putExtra("id", game.id)
            putExtra("title", game.title)
        }

    /** Monotonic-ish rank so a freshly added game outranks the ones added before it. */
    private fun currentWeight(): Int =
        ((System.currentTimeMillis() - 1_500_000_000_000L) / 1000L).toInt()

    /**
     * The launcher opens the poster itself, so it has to exist as a local file. Failure is
     * survivable — the program is still published, just without artwork.
     */
    private fun cachePoster(context: Context, game: GameInfo): Uri? {
        val file = TvPosterContentProvider.posterFile(context, game.id)
        if (file.isFile && file.length() > 0) return TvPosterContentProvider.posterUri(context, game.id)
        val url = catalogCardImageUrl(game, tvProfile = false, requestWidth = 512) ?: return null
        val bitmap = downloadBitmap(url) ?: return null
        return runCatching {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            TvPosterContentProvider.posterUri(context, game.id)
        }.getOrNull()
    }

    private fun downloadBitmap(url: String): Bitmap? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        try {
            val bytes = connection.inputStream.use { it.readBytes() }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            runCatching { connection.disconnect() }
        }
    }.getOrNull()
}
