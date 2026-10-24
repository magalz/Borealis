package com.limelight.utils

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.net.Uri
import androidx.core.graphics.createBitmap
import com.limelight.LimeLog
import com.limelight.PosterContentProvider
import com.limelight.R
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import java.io.IOException

private const val ASPECT_RATIO_MOVIE_POSTER = 5
private const val TYPE_GAME = 12
private const val INTERNAL_PROVIDER_ID_INDEX = 1
private const val PROGRAM_BROWSABLE_INDEX = 2
private const val ID_INDEX = 0
private const val LOGO_QUALITY = 100

// The weight of a program counts the seconds since this instant (2017-07-14).
private const val WEIGHT_EPOCH_MS = 1_500_000_000_000L
private const val MS_PER_SECOND = 1000L

private fun toValueString(value: Any?): String? = value?.toString()

private fun toUriString(intent: Intent?): String? = intent?.toUri(Intent.URI_INTENT_SCHEME)

class TvChannelHelper(private val context: Context) {
    // BOREALIS: the channel request needs an Activity; an application context only deletes
    // channels.
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    internal fun requestChannelOnHomeScreen(computer: ComputerDetails) {
        if (context !is Activity) {
            return
        }
        if (!isAndroidTV()) {
            return
        }

        val channelId = getChannelId(computer.uuid)
        if (channelId == null) {
            return
        }

        val intent = Intent(TvContract.ACTION_REQUEST_CHANNEL_BROWSABLE)
        intent.putExtra(TvContract.EXTRA_CHANNEL_ID, getChannelId(computer.uuid))
        try {
            context.startActivityForResult(intent, 0)
        } catch (ignored: Exception) {
            // ActivityNotFoundException is the only officially documented
            // exception that can result from this call. However some buggy
            // devices throw others.
            // See https://github.com/moonlight-stream/moonlight-android/issues/1302
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    // The Java printed the stack trace of a provider that does not know the channels, as it did
    // for the programs.
    @Suppress("ReturnCount", "PrintStackTrace")
    internal fun createTvChannel(computer: ComputerDetails) {
        if (!isAndroidTV()) {
            return
        }

        val builder =
            ChannelBuilder()
                .setType(TvContract.Channels.TYPE_PREVIEW)
                .setDisplayName(computer.name)
                .setInternalProviderId(computer.uuid)
                .setAppLinkIntent(ServerHelper.createPcShortcutIntent(context, computer))

        val channelId = getChannelId(computer.uuid)
        if (channelId != null) {
            context.contentResolver.update(
                TvContract.buildChannelUri(channelId),
                builder.toContentValues(),
                null,
                null,
            )
            return
        }

        val channelUri: Uri?

        try {
            channelUri =
                context.contentResolver.insert(
                    TvContract.Channels.CONTENT_URI,
                    builder.toContentValues(),
                )
        } catch (e: IllegalArgumentException) {
            // This can happen on HarmonyOS devices which report to
            // support Leanback APIs, yet don't implement this URI
            e.printStackTrace()
            return
        }

        if (channelUri != null) {
            val id = ContentUris.parseId(channelUri)
            updateChannelIcon(id)
        }
    }

    @Suppress("DEPRECATION")
    private fun updateChannelIcon(channelId: Long) {
        val logo = drawableToBitmap(context.resources.getDrawable(R.drawable.ic_channel))
        try {
            val localUri = TvContract.buildChannelLogoUri(channelId)
            try {
                context.contentResolver.openOutputStream(localUri).use { outputStream ->
                    val stream = checkNotNull(outputStream)
                    logo.compress(Bitmap.CompressFormat.PNG, LOGO_QUALITY, stream)
                    stream.flush()
                }
            } catch (e: SQLiteException) {
                logoNotStored(e)
            } catch (e: IOException) {
                logoNotStored(e)
            }
        } finally {
            logo.recycle()
        }
    }

    private fun logoNotStored(e: Exception) {
        LimeLog.warning("Failed to store the logo to the system content provider.")
        e.printStackTrace()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = context.resources.getDimensionPixelSize(R.dimen.tv_channel_logo_width)
        // The height reads the width dimension too, as the Java did (reported in Sprint 12).
        val height = context.resources.getDimensionPixelSize(R.dimen.tv_channel_logo_width)

        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("ReturnCount", "PrintStackTrace")
    internal fun addGameToChannel(computer: ComputerDetails, app: NvApp) {
        if (!isAndroidTV()) {
            return
        }

        val channelId = getChannelId(computer.uuid)
        if (channelId == null) {
            return
        }

        val builder =
            PreviewProgramBuilder()
                .setChannelId(channelId)
                .setType(TYPE_GAME)
                .setTitle(app.appName)
                .setPosterArtAspectRatio(ASPECT_RATIO_MOVIE_POSTER)
                .setPosterArtUri(
                    PosterContentProvider.createBoxArtUri(
                        checkNotNull(computer.uuid),
                        "" + app.appId,
                    )
                )
                .setIntent(ServerHelper.createAppShortcutIntent(context, computer, app))
                .setInternalProviderId("" + app.appId)
                // Weight should increase each time we run the game
                .setWeight(((System.currentTimeMillis() - WEIGHT_EPOCH_MS) / MS_PER_SECOND).toInt())

        val programId = getProgramId(channelId, "" + app.appId)
        if (programId != null) {
            context.contentResolver.update(
                TvContract.buildPreviewProgramUri(programId),
                builder.toContentValues(),
                null,
                null,
            )
            return
        }

        try {
            context.contentResolver.insert(
                TvContract.PreviewPrograms.CONTENT_URI,
                builder.toContentValues(),
            )
        } catch (e: IllegalArgumentException) {
            // This can happen on HarmonyOS devices which report to
            // support Leanback APIs, yet don't implement this URI
            e.printStackTrace()
            return
        }

        TvContract.requestChannelBrowsable(context, channelId)
    }

    internal fun deleteChannel(computer: ComputerDetails) {
        if (!isAndroidTV()) {
            return
        }

        val channelId = getChannelId(computer.uuid)
        if (channelId == null) {
            return
        }

        context.contentResolver.delete(TvContract.buildChannelUri(channelId), null, null)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("ReturnCount")
    internal fun deleteProgram(computer: ComputerDetails, app: NvApp) {
        if (!isAndroidTV()) {
            return
        }

        val channelId = getChannelId(computer.uuid)
        if (channelId == null) {
            return
        }

        val programId = getProgramId(channelId, "" + app.appId)
        if (programId == null) {
            return
        }

        context.contentResolver.delete(TvContract.buildPreviewProgramUri(programId), null, null)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("ReturnCount")
    private fun getChannelId(computerUuid: String?): Long? {
        context.contentResolver
            .query(
                TvContract.Channels.CONTENT_URI,
                arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID),
                null,
                null,
                null,
            )
            .use { cursor ->
                if (cursor == null) {
                    return null
                }
                if (cursor.count == 0) {
                    return null
                }
                while (cursor.moveToNext()) {
                    val internalProviderId = cursor.getString(INTERNAL_PROVIDER_ID_INDEX)
                    if (checkNotNull(computerUuid) == internalProviderId) {
                        return cursor.getLong(ID_INDEX)
                    }
                }

                return null
            }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("ReturnCount", "NestedBlockDepth")
    private fun getProgramId(channelId: Long, appId: String): Long? {
        context.contentResolver
            .query(
                TvContract.buildPreviewProgramsUriForChannel(channelId),
                arrayOf(
                    TvContract.PreviewPrograms._ID,
                    TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                    TvContract.PreviewPrograms.COLUMN_BROWSABLE,
                ),
                null,
                null,
                null,
            )
            .use { cursor ->
                if (cursor == null) {
                    return null
                }
                if (cursor.count == 0) {
                    return null
                }
                while (cursor.moveToNext()) {
                    val internalProviderId = cursor.getString(INTERNAL_PROVIDER_ID_INDEX)
                    if (appId == internalProviderId) {
                        val id = cursor.getLong(ID_INDEX)
                        val browsable = cursor.getInt(PROGRAM_BROWSABLE_INDEX)
                        if (browsable != 0) {
                            return id
                        } else {
                            val countDeleted =
                                context.contentResolver.delete(
                                    TvContract.buildPreviewProgramUri(id),
                                    null,
                                    null,
                                )
                            if (countDeleted > 0) {
                                LimeLog.info("Preview program has been deleted")
                            } else {
                                LimeLog.warning("Preview program has not been deleted")
                            }
                        }
                    }
                }

                return null
            }
    }

    private fun isAndroidTV(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private class PreviewProgramBuilder {
        private val values = ContentValues()

        fun setChannelId(channelId: Long): PreviewProgramBuilder {
            values.put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
            return this
        }

        fun setType(type: Int): PreviewProgramBuilder {
            values.put(TvContract.PreviewPrograms.COLUMN_TYPE, type)
            return this
        }

        fun setTitle(title: String?): PreviewProgramBuilder {
            values.put(TvContract.PreviewPrograms.COLUMN_TITLE, title)
            return this
        }

        fun setPosterArtAspectRatio(aspectRatio: Int): PreviewProgramBuilder {
            values.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, aspectRatio)
            return this
        }

        fun setIntent(intent: Intent?): PreviewProgramBuilder {
            values.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, toUriString(intent))
            return this
        }

        fun setIntentUri(uri: Uri?): PreviewProgramBuilder {
            values.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, toValueString(uri))
            return this
        }

        fun setInternalProviderId(id: String?): PreviewProgramBuilder {
            values.put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, id)
            return this
        }

        fun setPosterArtUri(uri: Uri?): PreviewProgramBuilder {
            values.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, toValueString(uri))
            return this
        }

        fun setWeight(weight: Int): PreviewProgramBuilder {
            values.put(TvContract.PreviewPrograms.COLUMN_WEIGHT, weight)
            return this
        }

        fun toContentValues(): ContentValues = ContentValues(values)
    }

    private class ChannelBuilder {
        private val values = ContentValues()

        fun setType(type: String?): ChannelBuilder {
            values.put(TvContract.Channels.COLUMN_TYPE, type)
            return this
        }

        fun setDisplayName(displayName: String?): ChannelBuilder {
            values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, displayName)
            return this
        }

        fun setInternalProviderId(internalProviderId: String?): ChannelBuilder {
            values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, internalProviderId)
            return this
        }

        fun setAppLinkIntent(intent: Intent?): ChannelBuilder {
            values.put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, toUriString(intent))
            return this
        }

        fun toContentValues(): ContentValues = ContentValues(values)
    }
}
