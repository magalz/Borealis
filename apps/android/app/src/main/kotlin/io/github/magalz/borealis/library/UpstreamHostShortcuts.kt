package io.github.magalz.borealis.library

import android.content.Context
import com.limelight.nvstream.http.ComputerDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.library.domain.HostShortcuts
import javax.inject.Inject
import javax.inject.Singleton

/** Disables the launcher shortcuts and TV channel of a host that was removed from this device. */
internal fun interface ShortcutCleanup {
    fun hostForgotten(details: ComputerDetails)
}

/** [HostShortcuts] and [ShortcutCleanup] over `ShortcutHelper`, which `PcView` and `AppView` used. */
@Singleton
internal class UpstreamHostShortcuts
@Inject
constructor(@ApplicationContext private val context: Context) : HostShortcuts, ShortcutCleanup {
    override val canPinApps: Boolean = false

    override fun reportHostOpened(hostId: String, hostName: String) = Unit

    override fun pinApp(
        hostId: String,
        hostName: String,
        appId: Int,
        appName: String,
        hdrSupported: Boolean,
    ): Boolean = false

    override fun hostForgotten(details: ComputerDetails) = Unit
}
