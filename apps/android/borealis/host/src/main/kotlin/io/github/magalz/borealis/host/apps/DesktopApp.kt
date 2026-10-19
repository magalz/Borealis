package io.github.magalz.borealis.host.apps

private const val DESKTOP_TITLE = "Desktop"

/**
 * Whether the app is Desktop, the one app whose Quit closes no program: it ends the stream and
 * gives the display back to the PC. The host's `/applist` gives only `ID`, `AppTitle` and
 * `IsHdrSupported` (host contract, section 3), so the title is all there is to tell on: Desktop is
 * the app titled "Desktop", ignoring case and the spaces around it. A title that only contains the
 * word (Remote Desktop, Desktop 2) is another app, and so is an unknown or blank title. When the
 * host renames Desktop, the app is treated as any other app, which is the cautious reading.
 */
fun isDesktop(title: String?): Boolean = title?.trim().equals(DESKTOP_TITLE, ignoreCase = true)
