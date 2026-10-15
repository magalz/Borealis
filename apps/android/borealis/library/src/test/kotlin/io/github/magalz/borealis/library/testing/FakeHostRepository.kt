package io.github.magalz.borealis.library.testing

import io.github.magalz.borealis.library.domain.AddHostResult
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.HostAddress
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.WakeResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

class FakeHostRepository : HostRepository {
    private val state = MutableStateFlow<List<Host>>(emptyList())

    /** How many collectors of [hosts] are active: the real repository polls while it is above 0. */
    var activeCollectors = 0
        private set

    override val hosts: Flow<List<Host>> =
        state.onStart { activeCollectors++ }.onCompletion { activeCollectors-- }

    val addedAddresses = mutableListOf<HostAddress>()
    val wokenHostIds = mutableListOf<String>()
    val forgottenHostIds = mutableListOf<String>()

    var addHostResult: AddHostResult = AddHostResult.Added(hostId = "new-host", name = "New PC")
    var wakeResult: WakeResult = WakeResult.SENT
    var detailsResult: String? = null

    /** When set, [addHost] waits for it before it answers. */
    var addHostGate: CompletableDeferred<Unit>? = null

    fun emit(vararg hosts: Host) {
        state.value = hosts.toList()
    }

    override suspend fun addHost(address: HostAddress): AddHostResult {
        addedAddresses += address
        addHostGate?.await()
        return addHostResult
    }

    override suspend fun wake(hostId: String): WakeResult {
        wokenHostIds += hostId
        return wakeResult
    }

    override suspend fun forget(hostId: String) {
        forgottenHostIds += hostId
    }

    override suspend fun details(hostId: String): String? = detailsResult
}
