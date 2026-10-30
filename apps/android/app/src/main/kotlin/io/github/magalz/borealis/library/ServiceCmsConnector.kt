package io.github.magalz.borealis.library

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.limelight.binding.crypto.AndroidCryptoProvider
import com.limelight.computers.ComputerManagerListener
import com.limelight.computers.ComputerManagerService
import com.limelight.computers.ComputerManagerService.ComputerManagerBinder
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.utils.ServerHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Binds the real `ComputerManagerService` once and keeps the binding for the life of the process:
 * the service then keeps its database and its known hosts, and [ComputerManagerConnection] decides
 * when it polls.
 *
 * Connecting does not wait for the client keypair. The host polls still wait for it on the lock of
 * `AndroidCryptoProvider` until [CryptoWarmUp] ends, so the known hosts can be listed before their
 * online or offline state arrives.
 */
@Singleton
internal class ServiceCmsConnector
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val warmUp: CryptoWarmUp,
) : CmsConnector {
    /** Outlives a call of [connect]: the warm-up goes on when the caller is cancelled. */
    private val background =
        CoroutineScope(
            SupervisorJob() +
                io +
                CoroutineExceptionHandler { _, error ->
                    BorealisLog.w(
                        LogTag.HOST,
                        "warm-up failed",
                        "error" to error.javaClass.simpleName,
                    )
                }
        )

    override suspend fun connect(): CmsBinder {
        val bound = CompletableDeferred<ComputerManagerBinder>()
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    bound.complete(service as ComputerManagerBinder)
                }

                override fun onServiceDisconnected(name: ComponentName) = Unit
            }
        val intent = Intent(context, ComputerManagerService::class.java)
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            throw IOException("The computer manager service could not be bound")
        }
        var connected = false
        try {
            val binder = bound.await()
            withContext(io) { binder.waitForReady() }
            connected = true
            // Loading the keypair takes seconds on a TV and the known hosts do not need it. Pairing
            // and streaming get the key from the provider, whose lock makes them wait for it.
            background.launch { warmUp.run() }
            return ServiceCmsBinder(binder)
        } finally {
            // A cancelled or failed connect must not leave a binding that nobody will use.
            if (!connected) context.unbindService(connection)
        }
    }
}

/** Work that makes the first host request faster and that nothing depends on. */
internal fun interface CryptoWarmUp {
    fun run()
}

/** Loads the client keypair, generating it on first use, as the first host request would. */
@Singleton
internal class AndroidCryptoWarmUp
@Inject
constructor(@ApplicationContext private val context: Context) : CryptoWarmUp {
    override fun run() {
        AndroidCryptoProvider(context).clientCertificate
    }
}

/** [CmsBinder] over the upstream binder. */
internal class ServiceCmsBinder(private val binder: ComputerManagerBinder) : CmsBinder {
    override val uniqueId: String
        get() = binder.uniqueId

    override fun startPolling(listener: ComputerManagerListener) = binder.startPolling(listener)

    override fun stopPolling() = binder.stopPolling()

    override fun waitForPollingStopped() = binder.waitForPollingStopped()

    override fun addComputerBlocking(details: ComputerDetails): Boolean =
        binder.addComputerBlocking(details)

    override fun removeComputer(details: ComputerDetails) = binder.removeComputer(details)

    override fun getComputer(uuid: String): ComputerDetails? = binder.getComputer(uuid)

    override fun invalidateStateForComputer(uuid: String) = binder.invalidateStateForComputer(uuid)

    override fun createAppListPoller(computer: ComputerDetails): AppListPoller {
        val poller = binder.createAppListPoller(computer)
        return object : AppListPoller {
            override fun start() = poller.start()

            override fun stop() = poller.stop()
        }
    }

    override fun startStream(
        activity: Activity,
        app: NvApp,
        computer: ComputerDetails,
        resumeChosen: Boolean,
    ) = ServerHelper.doStart(activity, app, computer, binder, resumeChosen)
}
