package io.github.magalz.borealis.diagnostics.bundle

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/** Where a saved bundle can be fetched: [location] for the dialog, and the `adb pull` for it. */
internal class SavedBundle(val location: String, val adbPullCommand: String)

/** Keeps a copy of the finished bundle where a user (or `adb`) can reach it. */
internal fun interface BundleStore {
    /** Copies [zip] into the store and drops the bundles this app saved earlier. */
    fun save(zip: File): SavedBundle
}

/** API 28: the app's own external files directory. */
internal class AppFilesBundleStore(
    private val packageName: String,
    private val externalDir: () -> File?,
) : BundleStore {
    override fun save(zip: File): SavedBundle {
        val dir = checkNotNull(externalDir()) { "No external files directory" }
        check(dir.isDirectory || dir.mkdirs()) {
            "Cannot create the external diagnostics directory"
        }
        BundleFiles.pruneOld(dir)
        val saved = zip.copyTo(File(dir, zip.name), overwrite = true)
        return SavedBundle(
            saved.absolutePath,
            "adb pull /sdcard/Android/data/$packageName/files/" +
                "${ExportFlow.EXTERNAL_DIR_NAME}/${saved.name}",
        )
    }
}

/**
 * API 29 and later: the public Downloads collection, through MediaStore. The app needs no storage
 * permission for its own files, and `adb` and TV file managers can reach `/sdcard/Download`. The
 * entry is pending while it is written. MediaStore only shows an app its own entries, so pruning
 * can only remove bundles this app saved.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class DownloadsBundleStore(private val context: Context) : BundleStore {
    private val resolver
        get() = context.contentResolver

    override fun save(zip: File): SavedBundle {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, zip.name)
                put(MediaStore.MediaColumns.MIME_TYPE, ExportFlow.ZIP_MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val item =
            checkNotNull(resolver.insert(collection, values)) { "MediaStore refused the bundle" }
        var complete = false
        try {
            checkNotNull(resolver.openOutputStream(item)) { "Cannot write the bundle" }
                .use { out ->
                    zip.inputStream().use { it.copyTo(out) }
                }
            resolver.update(
                item,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            complete = true
        } finally {
            if (!complete) resolver.delete(item, null, null)
        }
        pruneEarlier(collection, keep = ContentUris.parseId(item))
        // MediaStore can rename the file when the name is taken.
        val name = displayName(item) ?: zip.name
        return SavedBundle(
            "${Environment.DIRECTORY_DOWNLOADS}/$name",
            "adb pull /sdcard/${Environment.DIRECTORY_DOWNLOADS}/$name",
        )
    }

    private fun displayName(item: Uri): String? =
        resolver.query(item, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun pruneEarlier(collection: Uri, keep: Long) {
        val earlier = mutableListOf<Long>()
        resolver
            .query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("${BundleFiles.PREFIX}%"),
                null,
            )
            ?.use {
                while (it.moveToNext()) {
                    if (it.getLong(0) != keep && it.getString(1).endsWith(".zip")) {
                        earlier += it.getLong(0)
                    }
                }
            }
        earlier.forEach { resolver.delete(ContentUris.withAppendedId(collection, it), null, null) }
    }
}

internal fun defaultBundleStore(context: Context): BundleStore =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        DownloadsBundleStore(context)
    } else {
        AppFilesBundleStore(context.packageName) {
            context.getExternalFilesDir(ExportFlow.EXTERNAL_DIR_NAME)
        }
    }

/** A television: the UI mode of the configuration, or the leanback feature. */
internal fun isTelevision(context: Context): Boolean {
    val uiMode = context.resources.configuration.uiMode
    return (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
