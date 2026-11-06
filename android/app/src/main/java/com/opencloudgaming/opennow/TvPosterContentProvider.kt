package com.opencloudgaming.opennow

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the box art that [TvChannels] hands to the system TV provider.
 *
 * `TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI` has to be something the launcher can open
 * itself, so the artwork is cached on disk first and exposed here as `content://`. Read-only by
 * design: it only ever answers with files this app already wrote.
 */
class TvPosterContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("This provider is read-only")
        val context = context ?: throw FileNotFoundException()
        val gameId = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: throw FileNotFoundException()
        val file = posterFile(context, gameId)
        if (!file.isFile) throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("This provider is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("This provider is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("This provider is read-only")

    companion object {
        /** Must stay in sync with the authority declared in AndroidManifest.xml. */
        fun authority(context: Context): String = "${context.packageName}.tvposter"

        fun posterUri(context: Context, gameId: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(authority(context))
            .appendPath(safeName(gameId))
            .build()

        fun posterFile(context: Context, gameId: String): File =
            File(File(context.cacheDir, "tvposter"), safeName(gameId)).apply {
                parentFile?.mkdirs()
            }

        private fun safeName(gameId: String): String =
            gameId.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { "unknown" }
    }
}
