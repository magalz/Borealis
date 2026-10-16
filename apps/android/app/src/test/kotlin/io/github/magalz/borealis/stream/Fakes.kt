package io.github.magalz.borealis.stream

import io.github.magalz.borealis.library.domain.AddHostResult
import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.HostAddress
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.domain.WakeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Records what reaches the connection, as the sequence a host would see. */
class RecordingInput : InputSink {
    val events = mutableListOf<String>()

    override fun sendKeyboardInput(keyMap: Short, direction: Byte, modifiers: Byte, flags: Byte) {
        val key = "%04X".format(keyMap.toInt() and 0xFFFF)
        val action = if (direction == DOWN) "down" else "up"
        events += "$action $key mods %02X flags %d".format(modifiers.toInt(), flags.toInt())
    }

    override fun sendUtf8Text(text: String) {
        events += "text $text"
    }

    override fun sendMouseButtonUp(button: Byte) {
        events += "mouse up $button"
    }

    private companion object {
        const val DOWN: Byte = 0x03
    }
}

class FakeApps(var result: QuitResult = QuitResult.Quit) : AppRepository {
    val quits = mutableListOf<String>()

    override fun apps(hostId: String): Flow<AppsSnapshot> = emptyFlow()

    override suspend fun setHidden(hostId: String, appId: Int, hidden: Boolean) = Unit

    override suspend fun quit(hostId: String): QuitResult {
        quits += hostId
        return result
    }
}

class FakeHosts : HostRepository {
    val wakes = mutableListOf<String>()

    override val hosts: Flow<List<Host>> = emptyFlow()

    override suspend fun addHost(address: HostAddress): AddHostResult = AddHostResult.Unreachable

    override suspend fun wake(hostId: String): WakeResult {
        wakes += hostId
        return WakeResult.SENT
    }

    override suspend fun forget(hostId: String) = Unit

    override suspend fun details(hostId: String): String? = null
}

class FakeEntryPoint(val apps: FakeApps = FakeApps(), val hosts: FakeHosts = FakeHosts()) :
    StreamEntryPoint {
    override fun appRepository(): AppRepository = apps

    override fun hostRepository(): HostRepository = hosts
}
