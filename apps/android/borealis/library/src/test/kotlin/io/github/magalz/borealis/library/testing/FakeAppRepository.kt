package io.github.magalz.borealis.library.testing

import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.QuitResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

class FakeAppRepository(
    initial: AppsSnapshot = AppsSnapshot(host = null, apps = emptyList(), loading = true)
) : AppRepository {
    private val state = MutableStateFlow(initial)

    /** How many collectors of [apps] are active: the real repository polls while it is above 0. */
    var activeCollectors = 0
        private set

    val requestedHostIds = mutableListOf<String>()
    val hiddenCalls = mutableListOf<Triple<String, Int, Boolean>>()
    var quitCalls = 0
        private set

    var quitResult: QuitResult = QuitResult.Quit

    fun emit(snapshot: AppsSnapshot) {
        state.value = snapshot
    }

    override fun apps(hostId: String): Flow<AppsSnapshot> {
        requestedHostIds += hostId
        return state.onStart { activeCollectors++ }.onCompletion { activeCollectors-- }
    }

    override suspend fun setHidden(hostId: String, appId: Int, hidden: Boolean) {
        hiddenCalls += Triple(hostId, appId, hidden)
    }

    override suspend fun quit(hostId: String): QuitResult {
        quitCalls++
        return quitResult
    }
}
