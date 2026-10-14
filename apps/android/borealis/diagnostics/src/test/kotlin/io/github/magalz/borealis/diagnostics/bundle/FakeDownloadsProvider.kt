package io.github.magalz.borealis.diagnostics.bundle

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import org.robolectric.Robolectric

/**
 * A stand-in for the `media` provider behind `MediaStore.Downloads`: it keeps rows and their file
 * content in memory, and answers the display-name prefix query the store uses to find its own
 * earlier bundles.
 */
class FakeDownloadsProvider : ContentProvider() {
    class Row(val id: Long, val inserted: ContentValues, val current: ContentValues, val file: File)

    val rows = mutableListOf<Row>()
    private var nextId = 1L

    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val id = nextId++
        val file = File.createTempFile("fake-download", ".bin")
        rows += Row(id, ContentValues(values), ContentValues(values), file)
        return ContentUris.withAppendedId(uri, id)
    }

    private fun rowOf(uri: Uri) = rows.first { it.id == ContentUris.parseId(uri) }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(rowOf(uri).file, ParcelFileDescriptor.parseMode(mode))

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        rowOf(uri).current.putAll(values)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        if (rows.removeAll { it.id == ContentUris.parseId(uri) }) 1 else 0

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val prefix = selectionArgs?.firstOrNull()?.removeSuffix("%").orEmpty()
        val cursor =
            MatrixCursor(arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME))
        for (row in rows) {
            val name = row.current.getAsString(MediaStore.MediaColumns.DISPLAY_NAME)
            if (name.startsWith(prefix)) cursor.addRow(arrayOf(row.id, name))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    companion object {
        /** Registers a fake `media` provider for the current Robolectric test. */
        fun install(): FakeDownloadsProvider =
            Robolectric.buildContentProvider(FakeDownloadsProvider::class.java)
                .create("media")
                .get()
    }
}
