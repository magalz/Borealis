package io.github.magalz.borealis.host.apps

/**
 * Whether the app is Desktop, the one app whose Quit closes no program: it ends the stream and
 * gives the display back to the PC. The host's `/applist` gives only `ID`, `AppTitle` and
 * `IsHdrSupported` (host contract, section 3), so the title is all there is to tell on.
 */
fun isDesktop(title: String?): Boolean = false
