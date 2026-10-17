package io.github.magalz.borealis.library

import android.content.Context
import com.limelight.R
import com.limelight.grid.assets.DiskAssetLoader
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.wol.WakeOnLanSender
import com.limelight.utils.CacheHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.host.launch.HostError
import io.github.magalz.borealis.host.pairing.PairingErrors
import io.github.magalz.borealis.host.pairing.PairingFailure
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/** The upstream texts the adapters hand to the screens. */
internal interface HostTexts {
    fun unknownHost(): String

    fun notFound(): String

    fun pairFailed(): String

    fun pairInGame(): String
}

@Singleton
internal class ResourceHostTexts
@Inject
constructor(@ApplicationContext private val context: Context) : HostTexts {
    override fun unknownHost(): String = context.getString(R.string.error_unknown_host)

    override fun notFound(): String = context.getString(R.string.error_404)

    override fun pairFailed(): String = context.getString(R.string.pair_fail)

    override fun pairInGame(): String = context.getString(R.string.pair_pc_ingame)
}

/** `PairingErrors.describe`, which needs the Android resources and the native host rules. */
internal fun interface PairingErrorMapper {
    fun describe(
        error: HostError?,
        fallback: String?,
        restarted: Boolean,
        host: String?,
    ): PairingFailure
}

@Singleton
internal class UpstreamPairingErrorMapper
@Inject
constructor(@ApplicationContext private val context: Context) : PairingErrorMapper {
    override fun describe(
        error: HostError?,
        fallback: String?,
        restarted: Boolean,
        host: String?,
    ): PairingFailure = PairingErrors.describe(context, error, fallback, restarted, host)
}

/** Sends the Wake-on-LAN packets of `WakeOnLanSender`; throws `IOException` when none went out. */
internal fun interface WakeSender {
    fun send(details: ComputerDetails)
}

@Singleton
internal class UpstreamWakeSender @Inject constructor() : WakeSender {
    override fun send(details: ComputerDetails) = WakeOnLanSender.sendWolPacket(details)
}

/**
 * Switches the launcher shortcut and the TV program of an app off when the user hides it, and the
 * shortcut on again when the user shows it.
 */
internal interface HiddenAppShortcuts {
    fun appHidden(hostId: String, appId: Int)

    fun appShown(hostId: String, appId: Int)
}

/** Deletes the cached box art of a removed host. */
internal fun interface BoxArtCache {
    fun delete(hostId: String)
}

@Singleton
internal class DiskBoxArtCache
@Inject
constructor(@ApplicationContext private val context: Context) : BoxArtCache {
    override fun delete(hostId: String) = DiskAssetLoader(context).deleteAssetsForComputer(hostId)
}

/** A site-local IPv4 address of this device and the length of its subnet prefix. */
internal class LocalIpv4(val address: Inet4Address, val prefixLength: Int)

/** The network interfaces of this device. */
internal fun interface LocalNetworks {
    /** The site-local IPv4 addresses; throws when the platform cannot list its interfaces. */
    fun siteLocalIpv4(): List<LocalIpv4>
}

@Singleton
internal class DeviceLocalNetworks @Inject constructor() : LocalNetworks {
    override fun siteLocalIpv4(): List<LocalIpv4> =
        NetworkInterface.getNetworkInterfaces().toList().flatMap { network ->
            network.interfaceAddresses.mapNotNull { entry ->
                val address = entry.address
                if (address is Inet4Address && address.isSiteLocalAddress) {
                    LocalIpv4(address, entry.networkPrefixLength.toInt())
                } else {
                    null
                }
            }
        }
}

/** The app list that the computer manager service cached on disk for a host, as raw XML. */
internal fun interface AppListCache {
    /** Reads from disk; null when there is no usable cache. Never call it on the main thread. */
    fun read(hostId: String): String?
}

/**
 * [AppListCache] over the file that `ComputerManagerService` writes: `applist/<uuid>` in the cache.
 */
@Singleton
internal class DiskAppListCache
@Inject
constructor(@ApplicationContext private val context: Context) : AppListCache {
    override fun read(hostId: String): String? {
        if (!isFileName(hostId)) return null
        val file = CacheHelper.openPath(false, context.cacheDir, "applist", hostId)
        if (!file.isFile) return null
        return try {
            file.readText(Charsets.UTF_8).ifEmpty { null }
        } catch (e: IOException) {
            BorealisLog.w(
                LogTag.HOST,
                "cached app list unreadable",
                "error" to e.javaClass.simpleName,
            )
            null
        }
    }

    // The id comes from the host: it must not leave the cache directory.
    private fun isFileName(id: String): Boolean =
        id.isNotEmpty() && id != ".." && id.none { it == '/' || it == '\\' }
}
