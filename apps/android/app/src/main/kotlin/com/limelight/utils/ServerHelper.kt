package com.limelight.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.limelight.Game
import com.limelight.R
import com.limelight.ShortcutTrampoline
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import io.github.magalz.borealis.library.shortcuts.ShortcutExtras
import java.io.IOException
import java.security.cert.CertificateEncodingException

object ServerHelper {
    const val CONNECTION_TEST_SERVER = "android.conntest.moonlight-stream.org"

    @JvmStatic
    @Throws(IOException::class)
    fun getCurrentAddressFromComputer(computer: ComputerDetails): ComputerDetails.AddressTuple =
        computer.activeAddress ?: throw IOException("No active address for ${computer.name}")

    @JvmStatic
    fun createPcShortcutIntent(parent: Context, computer: ComputerDetails): Intent {
        val i = Intent(parent, ShortcutTrampoline::class.java)
        i.putExtra(ShortcutExtras.NAME, computer.name)
        i.putExtra(ShortcutExtras.UUID, computer.uuid)
        i.action = Intent.ACTION_DEFAULT
        return i
    }

    @JvmStatic
    fun createAppShortcutIntent(parent: Context, computer: ComputerDetails, app: NvApp): Intent {
        val i = Intent(parent, ShortcutTrampoline::class.java)
        i.putExtra(ShortcutExtras.NAME, computer.name)
        i.putExtra(ShortcutExtras.UUID, computer.uuid)
        i.putExtra(ShortcutExtras.APP_NAME, app.appName)
        i.putExtra(ShortcutExtras.APP_ID, "" + app.appId)
        i.putExtra(ShortcutExtras.APP_HDR, app.isHdrSupported)
        i.action = Intent.ACTION_DEFAULT
        return i
    }

    // Kept as the Java had it: a certificate that cannot be encoded is printed and left out.
    @Suppress("PrintStackTrace")
    @JvmStatic
    fun createStartIntent(
        parent: Activity,
        app: NvApp,
        computer: ComputerDetails,
        managerBinder: ComputerManagerService.ComputerManagerBinder,
    ): Intent {
        val intent = Intent(parent, Game::class.java)
        val activeAddress = checkNotNull(computer.activeAddress)
        intent.putExtra(Game.EXTRA_HOST, activeAddress.address)
        intent.putExtra(Game.EXTRA_PORT, activeAddress.port)
        intent.putExtra(Game.EXTRA_HTTPS_PORT, computer.httpsPort)
        intent.putExtra(Game.EXTRA_APP_NAME, app.appName)
        intent.putExtra(Game.EXTRA_APP_ID, app.appId)
        intent.putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported)
        intent.putExtra(Game.EXTRA_UNIQUEID, managerBinder.uniqueId)
        intent.putExtra(Game.EXTRA_PC_UUID, computer.uuid)
        intent.putExtra(Game.EXTRA_PC_NAME, computer.name)
        try {
            computer.serverCert?.let { intent.putExtra(Game.EXTRA_SERVER_CERT, it.encoded) }
        } catch (e: CertificateEncodingException) {
            e.printStackTrace()
        }
        return intent
    }

    @JvmStatic
    fun doStart(
        parent: Activity,
        app: NvApp,
        computer: ComputerDetails,
        managerBinder: ComputerManagerService.ComputerManagerBinder,
    ) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(
                    parent,
                    parent.resources.getString(R.string.pair_pc_offline),
                    Toast.LENGTH_SHORT,
                )
                .show()
            return
        }
        parent.startActivity(createStartIntent(parent, app, computer, managerBinder))
    }
}
