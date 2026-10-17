package io.github.magalz.borealis.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.limelight.grid.assets.DiskAssetLoader
import com.limelight.grid.assets.DiskAssetLoader.LoaderTuple
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager.PairState
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The box art of the apps of a paired host, cached on disk. */
internal interface BoxArt {
    /** The art of [appId] on [hostId], fetched when not cached; null when there is none. */
    suspend fun bitmap(hostId: String, appId: Int): Bitmap?

    /** Starts fetching the art of [appId] without waiting for it; the job ends with the fetch. */
    fun prefetch(hostId: String, appId: Int): Job
}

/**
 * [BoxArt] over the `DiskAssetLoader` cache of 12.2: `cache/boxart/<host>/<app>.png`, which the TV
 * channel's `PosterContentProvider` serves. The art comes from the paired host only, over the HTTPS
 * connection that pins its certificate. Nothing here throws: art that cannot be had is null.
 */
@Singleton
internal class BoxArtLoader
internal constructor(
    private val context: Context,
    private val connection: ComputerManagerConnection,
    private val clients: HostClientFactory,
    private val io: CoroutineDispatcher,
    private val fetchTimeoutMs: Long,
) : BoxArt {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        connection: ComputerManagerConnection,
        clients: HostClientFactory,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(context, connection, clients, io, FETCH_TIMEOUT_MS)

    private val assets by lazy { DiskAssetLoader(context) }
    private val scope = CoroutineScope(SupervisorJob() + io)

    // One fetch at a time: a launch and a pin of the same app must not both ask the host.
    private val fetching = Mutex()

    override suspend fun bitmap(hostId: String, appId: Int): Bitmap? =
        withContext(io) {
            val tuple = tuple(hostId, appId)
            if (cached(tuple)) decode(tuple) else null
        }

    override fun prefetch(hostId: String, appId: Int): Job = scope.launch {
        cached(tuple(hostId, appId))
    }

    private fun tuple(hostId: String, appId: Int) =
        LoaderTuple(ComputerDetails().also { it.uuid = hostId }, NvApp("", appId, false))

    /** True when the art is in the disk cache, fetched from the host if it was not. */
    private suspend fun cached(tuple: LoaderTuple): Boolean {
        if (assets.checkCacheExists(tuple)) return true
        return fetching.withLock { assets.checkCacheExists(tuple) || fetch(tuple) }
    }

    @Suppress("TooGenericExceptionCaught") // Art is a nicety: whatever goes wrong, there is none.
    private suspend fun fetch(tuple: LoaderTuple): Boolean {
        try {
            val details = connection.lookup(tuple.computer.uuid) ?: return false
            // The art is only asked of a paired host, whose certificate NvHTTP then pins.
            if (details.pairState != PairState.PAIRED || details.serverCert == null) return false
            val client = clients.create(details, connection.binder().uniqueId)
            // Stores at most 5 MB and deletes a file that did not arrive whole.
            client.boxArt(tuple.app.appId).use { assets.populateCacheWithStream(tuple, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            unavailable(e)
        } catch (e: RuntimeException) {
            unavailable(e)
        }
        return assets.checkCacheExists(tuple)
    }

    /**
     * The cached file as a software bitmap of about the standard art size. Not
     * `DiskAssetLoader.loadBitmapFromCache`: its `ImageDecoder` squeezes any art to 300x400 and
     * decodes into hardware memory, which the icon cannot draw from.
     */
    private fun decode(tuple: LoaderTuple): Bitmap? {
        val file = assets.getFile(tuple.computer.uuid, tuple.app.appId)
        // The file is at most 5 MB. Decoding from memory leaves no handle on it, so it can be
        // deleted.
        val bytes =
            try {
                file.readBytes()
            } catch (e: IOException) {
                unavailable(e)
                return null
            }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val bitmap =
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize =
                            DiskAssetLoader.calculateInSampleSize(bounds, ART_WIDTH, ART_HEIGHT)
                    }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } else {
                null
            }
        if (bitmap == null) {
            // A file that does not decode would be found again and again: fetch it anew next time.
            unavailable(IOException("undecodable"))
            file.delete()
        }
        return bitmap
    }

    private fun unavailable(error: Exception) =
        BorealisLog.w(LogTag.HOST, "box art unavailable", "error" to error.javaClass.simpleName)

    private companion object {
        /** The size of the art that hosts serve, as in `DiskAssetLoader`. */
        const val ART_WIDTH = 300
        const val ART_HEIGHT = 400

        /** How long the host has to send the art before the fetch gives up. */
        const val FETCH_TIMEOUT_MS = 20_000L
    }
}
