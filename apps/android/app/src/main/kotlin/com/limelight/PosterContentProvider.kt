package com.limelight

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.limelight.grid.assets.DiskAssetLoader
import io.github.magalz.borealis.library.PosterRequest
import java.io.FileNotFoundException

class PosterContentProvider : ContentProvider() {
    private var diskAssetLoader: DiskAssetLoader? = null

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val match = uriMatcher.match(uri)
        if (match == BOXART_URI_ID) {
            return openBoxArtFile(uri, mode)
        }
        return openBoxArtFile(uri, mode)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("ThrowsCount")
    @Throws(FileNotFoundException::class)
    fun openBoxArtFile(uri: Uri, mode: String?): ParcelFileDescriptor {
        if (mode != "r") {
            throw UnsupportedOperationException("This provider is only for read mode")
        }

        val segments = uri.pathSegments
        if (segments.size != BOXART_URI_SEGMENTS) {
            throw FileNotFoundException()
        }
        val appId = segments[APP_ID_PATH_INDEX]
        val uuid = segments[COMPUTER_UUID_PATH_INDEX]
        val checkedAppId = PosterRequest.checkedAppId(uuid, appId)
        val file = checkNotNull(diskAssetLoader).getFile(uuid, checkedAppId)
        if (file.exists()) {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        throw FileNotFoundException()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("This provider is only for read mode")

    override fun getType(uri: Uri): String = PNG_MIME_TYPE

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("This provider is only for read mode")

    override fun onCreate(): Boolean {
        diskAssetLoader = DiskAssetLoader(checkNotNull(context))
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = throw UnsupportedOperationException("This provider doesn't support query")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("This provider is support read only")

    companion object {
        const val AUTHORITY = "poster." + BuildConfig.APPLICATION_ID
        const val PNG_MIME_TYPE = "image/png"
        const val APP_ID_PATH_INDEX = 2
        const val COMPUTER_UUID_PATH_INDEX = 1

        private const val BOXART_PATH = "boxart"
        private const val BOXART_URI_ID = 1
        private const val BOXART_URI_SEGMENTS = 3

        private val uriMatcher =
            UriMatcher(UriMatcher.NO_MATCH).also {
                it.addURI(AUTHORITY, BOXART_PATH, BOXART_URI_ID)
            }

        @JvmStatic
        fun createBoxArtUri(uuid: String, appId: String): Uri =
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(BOXART_PATH)
                .appendPath(uuid)
                .appendPath(appId)
                .build()
    }
}
