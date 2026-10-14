package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
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
    override fun save(zip: File): SavedBundle = TODO()
}

/** API 29 and later: the public Downloads collection, through MediaStore. */
internal class DownloadsBundleStore(private val context: Context) : BundleStore {
    override fun save(zip: File): SavedBundle = TODO()
}

internal fun defaultBundleStore(context: Context): BundleStore = TODO()

/** A television: the UI mode of the configuration, or the leanback feature. */
internal fun isTelevision(context: Context): Boolean = TODO()
