package io.github.magalz.borealis.support

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.media.tv.TvContract
import android.net.Uri

/**
 * The TV provider of the system (`android.media.tv`), as far as the TV channel of a PC needs it:
 * channels and preview programs that are looked up by their internal provider id, inserted, updated
 * and deleted. Every change is recorded in order.
 */
class FakeTvProvider : ContentProvider() {
    /**
     * The rows by id; a seed or an insert puts one here, with the columns as the caller gave them.
     */
    val channels = linkedMapOf<Long, ContentValues>()
    val programs = linkedMapOf<Long, ContentValues>()

    val inserts = mutableListOf<Pair<Uri, ContentValues>>()
    val updates = mutableListOf<Pair<Uri, ContentValues>>()
    val deletes = mutableListOf<Uri>()
    val queries = mutableListOf<Uri>()

    /** What an insert throws, as the TV provider of a HarmonyOS device does. */
    var insertFailure: RuntimeException? = null
    var insertReturnsNull = false
    var queryReturnsNull = false

    /** A delete that finds nothing to delete, whatever was stored. */
    var deleteReturnsZero = false

    private var nextId = FIRST_ID

    override fun onCreate() = true

    override fun getType(uri: Uri): String? = null

    private fun table(uri: Uri) =
        if (uri.pathSegments.firstOrNull() == "channel") channels else programs

    private fun idOf(uri: Uri): Long? = uri.pathSegments.getOrNull(1)?.toLongOrNull()

    fun seedChannel(internalProviderId: String?): Long {
        val id = nextId++
        channels[id] =
            ContentValues().also {
                it.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, internalProviderId)
            }
        return id
    }

    fun seedProgram(channelId: Long, internalProviderId: String?, browsable: Int): Long {
        val id = nextId++
        programs[id] =
            ContentValues().also {
                it.put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
                it.put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, internalProviderId)
                it.put(TvContract.PreviewPrograms.COLUMN_BROWSABLE, browsable)
            }
        return id
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        queries += uri
        if (queryReturnsNull) return null
        val columns = checkNotNull(projection).toList()
        val cursor = MatrixCursor(columns.toTypedArray())
        val channel = uri.getQueryParameter("channel")?.toLongOrNull()
        for ((id, values) in table(uri)) {
            if (
                channel != null &&
                    values.getAsLong(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID) != channel
            ) {
                continue
            }
            cursor.addRow(columns.map { if (it == "_id") id else values.get(it) ?: defaultOf(it) })
        }
        return cursor
    }

    // A program that was inserted without the column is browsable, as the provider has it.
    private fun defaultOf(column: String): Any? =
        if (column == TvContract.PreviewPrograms.COLUMN_BROWSABLE) 1 else null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val given = ContentValues(checkNotNull(values))
        inserts += uri to given
        insertFailure?.let { throw it }
        if (insertReturnsNull) return null
        val id = nextId++
        table(uri)[id] = given
        return if (table(uri) === channels) {
            TvContract.buildChannelUri(id)
        } else {
            TvContract.buildPreviewProgramUri(id)
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        val given = ContentValues(checkNotNull(values))
        updates += uri to given
        val row = idOf(uri)?.let { table(uri)[it] } ?: return 0
        row.putAll(given)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        deletes += uri
        if (deleteReturnsZero) return 0
        return if (idOf(uri)?.let { table(uri).remove(it) } != null) 1 else 0
    }

    companion object {
        /** The id of the first row: a channel and a program never share one. */
        const val FIRST_ID = 41L
    }
}
