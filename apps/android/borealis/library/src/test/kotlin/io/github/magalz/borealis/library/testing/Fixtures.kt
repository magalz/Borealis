package io.github.magalz.borealis.library.testing

import io.github.magalz.borealis.library.domain.App
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp

/** An online, paired host. A test that needs a wake address or capabilities uses `copy`. */
fun aHost(
    id: String = "host-1",
    name: String = "Desk PC",
    reachability: Reachability = Reachability.ONLINE,
    paired: Boolean = true,
    runningApp: RunningApp? = null,
) =
    Host(
        id = id,
        name = name,
        reachability = reachability,
        paired = paired,
        runningApp = runningApp,
        canWake = false,
        address = null,
        capabilities = emptySet(),
    )

fun anApp(
    id: Int = 1,
    name: String = "Desktop",
    hdrSupported: Boolean = false,
    hidden: Boolean = false,
) = App(id, name, hdrSupported, hidden)

/**
 * What the repository emits: [apps] of the host `host-1`, which is paired and [reachability], with
 * [running] running. A test that needs another host, or none, gives [host].
 */
fun snapshot(
    vararg apps: App,
    running: App? = null,
    reachability: Reachability = Reachability.ONLINE,
    loading: Boolean = false,
    host: Host? =
        aHost(
            id = "host-1",
            name = "Desk PC",
            reachability = reachability,
            runningApp = running?.let { RunningApp(it.id, it.name) },
        ),
) = AppsSnapshot(host = host, apps = apps.toList(), loading = loading)
