package io.github.magalz.borealis.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.magalz.borealis.host.caps.BorealisHostInfo
import io.github.magalz.borealis.library.domain.AddHostResult
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.HostAddress
import io.github.magalz.borealis.library.domain.WakeResult
import io.github.magalz.borealis.nativecore.HostCaps
import io.github.magalz.borealis.nativecore.HostFamily
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ComputerHostRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val binder = FakeCmsBinder()
    private val connector = FakeCmsConnector(binder)
    private val clients = FakeHostClients(binder)
    private val woken = mutableListOf<ComputerDetails>()
    private var wakeFailure: IOException? = null
    private val deletedBoxArt = mutableListOf<String>()
    private val forgottenShortcuts = mutableListOf<String>()
    private var shortcutFailure: RuntimeException? = null
    private var localNetworks = listOf<LocalIpv4>()
    private val hiddenApps = HiddenApps(context)

    private fun TestScope.repository(
        connector: CmsConnector = this@ComputerHostRepositoryTest.connector
    ): ComputerHostRepository {
        val io = UnconfinedTestDispatcher(testScheduler)
        return ComputerHostRepository(
            context = context,
            connection = ComputerManagerConnection(connector, io),
            clients = clients,
            waker = {
                wakeFailure?.let { failure -> throw failure }
                woken += it
            },
            subnet = SubnetCheck { localNetworks },
            hiddenApps = hiddenApps,
            boxArt = { deletedBoxArt += it },
            shortcuts = {
                shortcutFailure?.let { failure -> throw failure }
                forgottenShortcuts += it.uuid!!
            },
            io = io,
        )
    }

    private fun TestScope.collectHosts(repository: ComputerHostRepository): List<List<Host>> {
        val seen = mutableListOf<List<Host>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.hosts.collect { seen += it }
        }
        return seen
    }

    @Test
    fun `collecting the hosts lists them by name as the service reports them`() = runTest {
        val repository = repository()

        val seen = collectHosts(repository)
        binder.emit(details(uuid = "b", name = "zed"))
        binder.emit(
            details(uuid = "a", name = "Alpha").reported(state = ComputerDetails.State.OFFLINE)
        )

        assertThat(seen.first()).isEmpty()
        assertThat(seen.last().map { it.name }).containsExactly("Alpha", "zed").inOrder()
        assertThat(seen.last().first().id).isEqualTo("a")
        assertThat(binder.events).containsExactly("startPolling")
    }

    @Test
    fun `nothing is emitted before the service is connected`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository =
            repository(
                CmsConnector {
                    gate.await()
                    binder
                }
            )

        val seen = collectHosts(repository)

        assertThat(seen).isEmpty()
        assertThat(binder.events).isEmpty()

        gate.complete(Unit)

        assertThat(seen).hasSize(1)
    }

    @Test
    fun `the first emission after connecting lists the hosts the service already knew`() = runTest {
        binder.computers["a"] = details(uuid = "a", name = "Alpha")
        binder.computers["b"] =
            details(uuid = "b", name = "Beta").reported(pairState = PairState.NOT_PAIRED)

        val seen = collectHosts(repository())

        assertThat(seen.first().map { it.name }).containsExactly("Alpha", "Beta").inOrder()
    }

    @Test
    fun `an update that changes nothing the screen shows is not emitted again`() = runTest {
        val repository = repository()
        val seen = collectHosts(repository)

        binder.emit(details(uuid = "a"))
        val count = seen.size
        binder.emit(details(uuid = "a"))

        assertThat(seen).hasSize(count)
    }

    @Test
    fun `the last collector leaving stops polling`() = runTest {
        val repository = repository()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.hosts.collect {}
            }

        job.cancelAndJoin()

        assertThat(binder.events).containsExactly("startPolling", "stopPolling").inOrder()
    }

    @Test
    fun `a host is added with the default port when the user gave none`() = runTest {
        val repository = repository()
        var manual: AddressTuple? = null
        binder.onAdd = {
            manual = it.manualAddress
            it.uuid = "new-id"
            it.name = "New PC"
            true
        }

        repository.addHost(HostAddress("pc.local"))

        assertThat(manual).isEqualTo(AddressTuple("pc.local", 47989))
    }

    @Test
    fun `a host is added with the port the user gave`() = runTest {
        val repository = repository()
        var manual: AddressTuple? = null
        binder.onAdd = {
            manual = it.manualAddress
            it.uuid = "new-id"
            it.name = "New PC"
            true
        }

        repository.addHost(HostAddress("192.168.1.9", 50000))

        assertThat(manual).isEqualTo(AddressTuple("192.168.1.9", 50000))
    }

    @Test
    fun `a host that answers is added with its id and the name it reported`() = runTest {
        val repository = repository()
        binder.onAdd = {
            it.uuid = "new-id"
            it.name = "Living Room"
            true
        }

        val result = repository.addHost(HostAddress("192.168.1.9"))

        assertThat(result).isEqualTo(AddHostResult.Added("new-id", "Living Room"))
    }

    @Test
    fun `a host that does not answer is unreachable`() = runTest {
        val repository = repository()
        localNetworks = listOf(LocalIpv4(InetAddress.getByName("192.168.1.20") as Inet4Address, 24))
        binder.onAdd = { false }

        assertThat(repository.addHost(HostAddress("192.168.1.9")))
            .isEqualTo(AddHostResult.Unreachable)
    }

    @Test
    fun `a private address on another subnet that does not answer is the wrong subnet`() = runTest {
        val repository = repository()
        localNetworks = listOf(LocalIpv4(InetAddress.getByName("192.168.1.20") as Inet4Address, 24))
        binder.onAdd = { false }

        assertThat(repository.addHost(HostAddress("10.0.0.7"))).isEqualTo(AddHostResult.WrongSubnet)
    }

    @Test
    fun `a service that cannot be bound makes the host unreachable instead of crashing`() =
        runTest {
            val repository = repository()
            connector.failure = IOException("The computer manager service could not be bound")

            assertThat(repository.addHost(HostAddress("192.168.1.9")))
                .isEqualTo(AddHostResult.Unreachable)
        }

    @Test
    fun `an address that cannot be a host is invalid, and the service is not asked`() = runTest {
        val repository = repository()
        var asked = false
        binder.onAdd = {
            asked = true
            true
        }

        assertThat(repository.addHost(HostAddress("  "))).isEqualTo(AddHostResult.InvalidAddress)
        assertThat(repository.addHost(HostAddress("pc.local", 0)))
            .isEqualTo(AddHostResult.InvalidAddress)
        assertThat(asked).isFalse()
    }

    @Test
    fun `a host name that OkHttp rejects is an invalid address`() = runTest {
        val repository = repository()
        binder.onAdd = { throw IllegalArgumentException("Invalid URL host") }

        assertThat(repository.addHost(HostAddress("bad_host")))
            .isEqualTo(AddHostResult.InvalidAddress)
    }

    @Test
    fun `an online host is already awake`() = runTest {
        val repository = repository()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repository.hosts.collect {} }
        binder.emit(details(uuid = "a").reported(mac = "AA:BB:CC:DD:EE:FF"))

        assertThat(repository.wake("a")).isEqualTo(WakeResult.ALREADY_ONLINE)
        assertThat(woken).isEmpty()
        job.cancelAndJoin()
    }

    @Test
    fun `a host without a MAC address cannot be woken`() = runTest {
        val repository = repository()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repository.hosts.collect {} }
        binder.emit(details(uuid = "a").reported(state = ComputerDetails.State.OFFLINE, mac = null))

        assertThat(repository.wake("a")).isEqualTo(WakeResult.NO_MAC_ADDRESS)
        assertThat(woken).isEmpty()
        job.cancelAndJoin()
    }

    @Test
    fun `an offline host with a MAC address is sent the wake packets`() = runTest {
        val repository = repository()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repository.hosts.collect {} }
        binder.emit(
            details(uuid = "a")
                .reported(state = ComputerDetails.State.OFFLINE, mac = "AA:BB:CC:DD:EE:FF")
        )

        assertThat(repository.wake("a")).isEqualTo(WakeResult.SENT)
        assertThat(woken.single().macAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
        job.cancelAndJoin()
    }

    @Test
    fun `a wake that sent no packet fails`() = runTest {
        val repository = repository()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repository.hosts.collect {} }
        binder.emit(
            details(uuid = "a")
                .reported(state = ComputerDetails.State.OFFLINE, mac = "AA:BB:CC:DD:EE:FF")
        )
        wakeFailure = IOException("network unreachable")

        assertThat(repository.wake("a")).isEqualTo(WakeResult.FAILED)
        job.cancelAndJoin()
    }

    @Test
    fun `waking an unknown host fails`() = runTest {
        assertThat(repository().wake("nobody")).isEqualTo(WakeResult.FAILED)
    }

    @Test
    fun `forgetting a paired online host unpairs it, removes it and clears what was kept for it`() =
        runTest {
            val repository = repository()
            val live = details(uuid = "a").reported(pairState = PairState.PAIRED)
            binder.computers["a"] = live
            hiddenApps.set("a", 7, hidden = true)

            repository.forget("a")

            assertThat(clients.client.unpairs).isEqualTo(1)
            assertThat(binder.removed.single().uuid).isEqualTo("a")
            assertThat(hiddenApps.read("a")).isEmpty()
            assertThat(deletedBoxArt).containsExactly("a")
            assertThat(forgottenShortcuts).containsExactly("a")
        }

    @Test
    fun `a host that does not confirm the unpair is removed all the same`() = runTest {
        val repository = repository()
        binder.computers["a"] = details(uuid = "a")
        clients.client.unpairResult = false

        repository.forget("a")

        assertThat(binder.removed.map { it.uuid }).containsExactly("a")
    }

    @Test
    fun `a host that cannot be reached for the unpair is removed all the same`() = runTest {
        val repository = repository()
        binder.computers["a"] = details(uuid = "a")
        clients.failure = IOException("unreachable")

        repository.forget("a")

        assertThat(binder.removed.map { it.uuid }).containsExactly("a")
    }

    @Test
    fun `an offline or unpaired host is removed without asking it to unpair`() = runTest {
        val repository = repository()
        binder.computers["off"] =
            details(uuid = "off").reported(state = ComputerDetails.State.OFFLINE)
        binder.computers["new"] = details(uuid = "new").reported(pairState = PairState.NOT_PAIRED)

        repository.forget("off")
        repository.forget("new")

        assertThat(clients.client.unpairs).isEqualTo(0)
        assertThat(binder.removed.map { it.uuid }).containsExactly("off", "new")
        assertThat(forgottenShortcuts).containsExactly("off", "new")
    }

    @Test
    fun `the box art of a forgotten host is deleted even when its shortcuts cannot be disabled`() =
        runTest {
            val repository = repository()
            binder.computers["a"] = details(uuid = "a")
            shortcutFailure = IllegalStateException("launcher gone")

            runCatching { repository.forget("a") }

            assertThat(deletedBoxArt).containsExactly("a")
        }

    @Test
    fun `forgetting an unknown host does nothing`() = runTest {
        repository().forget("nobody")

        assertThat(binder.removed).isEmpty()
        assertThat(forgottenShortcuts).isEmpty()
    }

    @Test
    fun `the details say what a user needs in plain words`() = runTest {
        val repository = repository()
        binder.computers["a"] =
            details(uuid = "3f2a-uuid", name = "Den PC").reported(mac = "AA:BB:CC:DD:EE:FF")

        val text = repository.details("a")

        assertThat(text)
            .isEqualTo(
                "Name: Den PC\n" +
                    "Status: Online\n" +
                    "Address: 192.168.1.5\n" +
                    "Paired: Yes\n" +
                    "MAC address (for wake): AA:BB:CC:DD:EE:FF"
            )
        assertThat(repository.details("nobody")).isNull()
    }

    @Test
    fun `the details show neither null, the UUID, the HTTPS port nor a raw enum name`() = runTest {
        val repository = repository()
        binder.computers["a"] =
            details(uuid = "3f2a-uuid", address = null)
                .reported(state = ComputerDetails.State.OFFLINE)
                .also { it.pairState = null }

        val text = repository.details("a")!!

        assertThat(text).isEqualTo("Name: Den PC\nStatus: Offline")
        assertThat(text).doesNotContain("null")
        assertThat(text).doesNotContain("3f2a-uuid")
        assertThat(text).doesNotContain("47984")
        assertThat(text).doesNotContain("OFFLINE")
        assertThat(text).doesNotContain("PAIRED")
    }

    @Test
    fun `the status of a host that was not checked yet is checking`() = runTest {
        val repository = repository()
        binder.computers["a"] = details().reported(state = ComputerDetails.State.UNKNOWN)

        assertThat(repository.details("a")).contains("Status: Checking…\n")
    }

    @Test
    fun `the details list the other addresses once, without the one in use`() = runTest {
        val repository = repository()
        binder.computers["a"] =
            details().also {
                it.localAddress = AddressTuple("192.168.1.5", 47989)
                it.remoteAddress = AddressTuple("203.0.113.7", 47989)
                it.manualAddress = AddressTuple("den.example.net", 47989)
                it.ipv6Address = AddressTuple("2001:db8::1", 47989)
            }

        assertThat(repository.details("a"))
            .contains(
                "Address: 192.168.1.5\n" +
                    "Other addresses: 203.0.113.7, den.example.net, 2001:db8::1\n"
            )
    }

    @Test
    fun `an address shows its port only when it is not the default HTTP port`() = runTest {
        val repository = repository()
        binder.computers["a"] =
            details(address = null).also {
                it.activeAddress = AddressTuple("192.168.1.5", 50000)
                it.localAddress = AddressTuple("fe80::1", 50002)
                it.remoteAddress = AddressTuple("203.0.113.7", NvHTTP.DEFAULT_HTTP_PORT)
                it.manualAddress = AddressTuple("den.example.net", 50001)
                it.ipv6Address = AddressTuple("2001:db8::1", NvHTTP.DEFAULT_HTTP_PORT)
            }

        assertThat(repository.details("a"))
            .contains(
                "Address: 192.168.1.5:50000\n" +
                    "Other addresses: [fe80::1]:50002, 203.0.113.7, den.example.net:50001, " +
                    "2001:db8::1\n"
            )
    }

    @Test
    fun `the same host on another port is not the address in use`() = runTest {
        val repository = repository()
        binder.computers["a"] =
            details().also { it.remoteAddress = AddressTuple("192.168.1.5", 50000) }

        assertThat(repository.details("a"))
            .contains("Address: 192.168.1.5\nOther addresses: 192.168.1.5:50000\n")
    }

    @Test
    fun `a host that is not paired says so`() = runTest {
        val repository = repository()
        binder.computers["a"] = details().reported(pairState = PairState.NOT_PAIRED)

        assertThat(repository.details("a")).contains("Paired: No")
    }

    @Test
    fun `the zero MAC address placeholder is not shown`() = runTest {
        val repository = repository()
        binder.computers["a"] = details().reported(mac = "00:00:00:00:00:00")

        assertThat(repository.details("a")).doesNotContain("MAC")
    }

    @Test
    fun `the running app is shown by the name the app list gives it`() = runTest {
        val repository = repository()
        binder.computers["a"] = details(runningGameId = 8, rawAppList = APP_LIST)

        assertThat(repository.details("a")).contains("Running: Desktop")
    }

    @Test
    fun `a running app the app list does not know leaves the running line out`() = runTest {
        val repository = repository()
        binder.computers["a"] = details(runningGameId = 99, rawAppList = APP_LIST)

        assertThat(repository.details("a")).doesNotContain("Running")
    }

    @Test
    fun `the capabilities block follows the facts after a blank line`() = runTest {
        val repository = repository()
        binder.computers["a"] =
            details().also {
                it.borealis =
                    BorealisHostInfo(
                        HostCaps(
                            HostFamily.SUNSHINE,
                            listOf(7, 1, 431, -1),
                            hevc = true,
                            hevcMain10 = false,
                            av1 = false,
                            av1Main10 = false,
                            yuv444 = false,
                            pyroWaveProfiles = emptySet(),
                        ),
                        httpsPort = 47984,
                        httpPort = 47989,
                    )
            }

        assertThat(repository.details("a"))
            .isEqualTo(
                "Name: Den PC\n" +
                    "Status: Online\n" +
                    "Address: 192.168.1.5\n" +
                    "Paired: Yes\n" +
                    "\n" +
                    "Host capabilities\n" +
                    "Host software: Sunshine-compatible, version 7.1.431.-1\n" +
                    "Video: HEVC\n" +
                    "PyroWave: off\n" +
                    "Ports: HTTP 47989, HTTPS 47984"
            )
    }
}
