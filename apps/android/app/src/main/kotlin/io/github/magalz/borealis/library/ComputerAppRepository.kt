package io.github.magalz.borealis.library

import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvApp
import io.github.magalz.borealis.library.domain.App
import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.QuitResult
import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

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
    override fun apps(hostId: String): Flow<AppsSnapshot> = flow {
        // The raw list changes every 30 seconds at most: parse it once per change.
        var parsedFrom: String? = null
        var parsed = emptyList<NvApp>()
        emitAll(
            combine(connection.appListUpdates(hostId), hiddenApps.revision) { details, _ ->
                    details
                }
                .map { details ->
                    if (details == null) {
                        AppsSnapshot(host = null, apps = emptyList(), loading = false)
                    } else {
                        val raw = details.rawAppList
                        if (raw != parsedFrom) {
                            parsed = parseApps(raw)
                            parsedFrom = raw
                        }
                        val hidden = hiddenApps.read(hostId)
                        AppsSnapshot(
                            host = details.toHost(parsed),
                            apps =
                                parsed.map {
                                    App(it.appId, it.appName, it.isHdrSupported, it.appId in hidden)
                                },
                            loading = raw == null,
                        )
                    }
                }
        )
    }
        .distinctUntilChanged()

    override suspend fun setHidden(hostId: String, appId: Int, hidden: Boolean) {
        withContext(io) { hiddenApps.set(hostId, appId, hidden) }
    }

    /** As `ServerHelper.doQuit`, without its dialogs. */
    override suspend fun quit(hostId: String): QuitResult {
        val details = connection.lookup(hostId) ?: return QuitResult.Failed(null)
        val uniqueId = connection.binder().uniqueId
        return withContext(io) {
            try {
                if (clients.create(details, uniqueId).quitApp()) {
                    QuitResult.Quit
                } else {
                    QuitResult.Failed(null)
                }
            } catch (e: IOException) {
                failure(e)
            } catch (e: XmlPullParserException) {
                failure(e)
            }
        }
    }

    private fun failure(error: Exception): QuitResult.Failed =
        QuitResult.Failed(
            when {
                error is HostHttpResponseException && error.errorCode == NOT_THE_STARTER ->
                    NOT_THE_STARTER_TEXT
                error is UnknownHostException -> texts.unknownHost()
                error is FileNotFoundException -> texts.notFound()
                else -> error.message
            }
        )

    private companion object {
        /** The synthetic status that `NvHTTP.quitApp` reports for a session of another client. */
        const val NOT_THE_STARTER = 599
        const val NOT_THE_STARTER_TEXT =
            "This session wasn't started by this device, so it cannot be quit. End streaming " +
                "on the original device or the PC itself. (Error code: 599)"
    }
}
