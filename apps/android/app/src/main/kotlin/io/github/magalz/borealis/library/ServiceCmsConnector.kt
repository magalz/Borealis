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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Binds the real `ComputerManagerService` once and keeps the binding for the life of the process:
 * the service then keeps its database and its known hosts, and [ComputerManagerConnection] decides
 * when it polls.
 */
@Singleton
internal class ServiceCmsConnector
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : CmsConnector {
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
        val binder = bound.await()
        withContext(Dispatchers.IO) {
            binder.waitForReady()
            // Generates the keypair now, so that the first host request does not wait for it.
            AndroidCryptoProvider(context).clientCertificate
        }
        return ServiceCmsBinder(binder)
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

    override fun startStream(activity: Activity, app: NvApp, computer: ComputerDetails) =
        ServerHelper.doStart(activity, app, computer, binder)
}
