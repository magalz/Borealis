package io.github.magalz.borealis.library

/**
 * One app list poller per host, shared by everyone who collects that host's apps. Not thread safe;
 * [ComputerManagerConnection] calls it with its lock held.
 */
internal class SharedAppListPollers {
    private class Shared(val poller: AppListPoller, var users: Int)

    private val pollers = HashMap<String, Shared>()

    /** True when the caller now shares the poller of [uuid]; false when the host is unknown. */
    fun start(binder: CmsBinder, uuid: String): Boolean {
        val shared = pollers[uuid]
        if (shared != null) {
            shared.users++
            return true
        }
        val live = binder.getComputer(uuid) ?: return false
        val poller = binder.createAppListPoller(live)
        poller.start()
        pollers[uuid] = Shared(poller, 1)
        return true
    }

    /** Ends one caller's share; the last one stops the poller. */
    fun stop(uuid: String) {
        val shared = pollers[uuid] ?: return
        if (--shared.users == 0) {
            shared.poller.stop()
            pollers.remove(uuid)
        }
    }
}
