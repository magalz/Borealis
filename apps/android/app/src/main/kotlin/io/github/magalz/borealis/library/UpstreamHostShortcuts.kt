package io.github.magalz.borealis.library

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import com.limelight.R
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.utils.ShortcutHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.library.domain.HostShortcuts
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Disables the launcher shortcuts and TV channel of a host that was removed from this device. */
internal fun interface ShortcutCleanup {
    fun hostForgotten(details: ComputerDetails)
}

/**
 * [HostShortcuts] and [ShortcutCleanup] over `ShortcutHelper`, which the 12.2 host and app screens
 * used. The shortcuts keep the ids and extras of 12.2, so the ones it pinned keep working and are
 * updated, not duplicated.
 */
@Singleton
internal class UpstreamHostShortcuts
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val boxArt: BoxArt,
) : HostShortcuts, ShortcutCleanup, HiddenAppShortcuts {
    private val helper
        get() = ShortcutHelper(context)

    private val scope = CoroutineScope(SupervisorJob() + io)
    private val publishing = Any()

    override val canPinApps: Boolean
        get() =
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
                context.getSystemService(ShortcutManager::class.java).isRequestPinShortcutSupported

    @Suppress("TooGenericExceptionCaught") // A launcher that fails must not take the app down.
    override fun reportHostOpened(hostId: String, hostName: String) {
        // ShortcutManager calls are binder calls: not on the main thread the ViewModel runs on.
        scope.launch {
            try {
                synchronized(publishing) {
                    // The user opened the host: this makes room for it among the dynamic shortcuts.
                    helper.createHostShortcut(computer(hostId, hostName), true, false)
                }
            } catch (e: RuntimeException) {
                BorealisLog.w(
                    LogTag.HOST,
                    "host shortcut not published",
                    "error" to e.javaClass.simpleName,
                )
            }
        }
    }

    override suspend fun pinApp(
        hostId: String,
        hostName: String,
        appId: Int,
        appName: String,
        hdrSupported: Boolean,
    ): Boolean =
        canPinApps &&
            helper.createPinnedGameShortcut(
                computer(hostId, hostName),
                NvApp(appName, appId, hdrSupported),
                null,
            )

    override fun appHidden(hostId: String, appId: Int) = Unit

    override fun appShown(hostId: String, appId: Int) = Unit

    override fun hostForgotten(details: ComputerDetails) =
        helper.disableComputerShortcut(details, context.getString(R.string.scut_deleted_pc))

    private fun computer(hostId: String, hostName: String) =
        ComputerDetails().also {
            it.uuid = hostId
            it.name = hostName
        }
}
