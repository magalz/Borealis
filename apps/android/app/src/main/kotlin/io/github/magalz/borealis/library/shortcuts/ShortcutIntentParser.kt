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

/** Maps the extras of a shortcut to what the Library should show. */
internal object ShortcutIntentParser {
    fun parse(extras: Bundle?): LibraryLaunchRequest = LibraryLaunchRequest.Hosts
}
