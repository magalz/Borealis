package io.github.magalz.borealis.library

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job

/** The box art of the apps of a paired host, cached on disk. */
internal interface BoxArt {
    /** The art of [appId] on [hostId], fetched when not cached; null when there is none. */
    suspend fun bitmap(hostId: String, appId: Int): Bitmap?

    /** Starts fetching the art of [appId] without waiting for it; the job ends with the fetch. */
    fun prefetch(hostId: String, appId: Int): Job
}

@Singleton
internal class BoxArtLoader
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val connection: ComputerManagerConnection,
    private val clients: HostClientFactory,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BoxArt {
    override suspend fun bitmap(hostId: String, appId: Int): Bitmap? = null

    override fun prefetch(hostId: String, appId: Int): Job = CompletableDeferred(Unit)
}
