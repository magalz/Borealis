package io.github.magalz.borealis.library

import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.QuitResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/** [AppRepository] over the app list poller of [ComputerManagerConnection] and `NvHTTP`. */
@Singleton
internal class ComputerAppRepository
@Inject
constructor(
    private val connection: ComputerManagerConnection,
    private val clients: HostClientFactory,
    private val hiddenApps: HiddenApps,
    private val texts: HostTexts,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AppRepository {
    override fun apps(hostId: String): Flow<AppsSnapshot> = TODO()

    override suspend fun setHidden(hostId: String, appId: Int, hidden: Boolean): Unit = TODO()

    override suspend fun quit(hostId: String): QuitResult = TODO()
}
