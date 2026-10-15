package io.github.magalz.borealis.library.testing

import io.github.magalz.borealis.library.domain.App
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp

fun aHost(
    id: String = "host-1",
    name: String = "Desk PC",
    reachability: Reachability = Reachability.ONLINE,
    paired: Boolean = true,
    runningApp: RunningApp? = null,
    canWake: Boolean = false,
) = Host(id, name, reachability, paired, runningApp, canWake)

fun anApp(
    id: Int = 1,
    name: String = "Desktop",
    hdrSupported: Boolean = false,
    hidden: Boolean = false,
) = App(id, name, hdrSupported, hidden)
