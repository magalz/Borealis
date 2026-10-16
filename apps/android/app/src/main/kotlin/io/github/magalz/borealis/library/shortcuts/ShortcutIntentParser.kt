package io.github.magalz.borealis.library.shortcuts

import android.os.Bundle
import io.github.magalz.borealis.library.LibraryLaunchRequest

/** The extras of the launcher shortcuts that 12.2 pinned, and that the Library still creates. */
internal object ShortcutExtras {
    const val UUID = "UUID"
    const val NAME = "Name"
    const val APP_ID = "AppId"
    const val APP_NAME = "AppName"
    const val APP_HDR = "HDR"
}

/**
 * Maps the extras of a shortcut to what the Library should show: the apps of the PC, a game
 * launch, or the host list when the shortcut names no PC (the name-only shortcuts of old releases).
 * The app id is a string in the extras; one that is not a number leaves the user on the apps of the
 * PC.
 */
internal object ShortcutIntentParser {
    fun parse(extras: Bundle?): LibraryLaunchRequest {
        val hostId =
            extras?.getString(ShortcutExtras.UUID)?.takeIf { it.isNotBlank() }
                ?: return LibraryLaunchRequest.Hosts
        val appId = extras.getString(ShortcutExtras.APP_ID)?.trim()?.toIntOrNull()
        return if (appId == null) {
            LibraryLaunchRequest.Apps(hostId)
        } else {
            LibraryLaunchRequest.Launch(hostId, appId)
        }
    }
}
