package io.github.magalz.borealis.library

import android.content.Context
import com.limelight.binding.PlatformBinding
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager.PairState
import com.limelight.utils.ServerHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** [HostClient] over the upstream [NvHTTP], which has no seam of its own. */
internal class NvHostClient(private val http: NvHTTP) : HostClient {
    override fun pairState(): PairState = http.getPairState()

    override fun pair(pin: String): PairResult {
        val manager = http.pairingManager
        val state = manager.pair(http.getServerInfo(true), pin)
        return PairResult(state, manager.pairedCert)
    }

    override fun unpair(): Boolean = http.unpair()

    override fun quitApp(): Boolean = http.quitApp()

    override fun boxArt(appId: Int): InputStream = http.getBoxArt(NvApp("", appId, false))

    override fun cancelPendingRequests() = http.cancelPendingRequests()
}

@Singleton
internal class NvHostClientFactory
@Inject
constructor(@ApplicationContext private val context: Context) : HostClientFactory {
    override fun create(details: ComputerDetails, uniqueId: String): HostClient =
        NvHostClient(
            NvHTTP(
                ServerHelper.getCurrentAddressFromComputer(details),
                details.httpsPort,
                uniqueId,
                details.serverCert,
                PlatformBinding.getCryptoProvider(context),
            )
        )
}
