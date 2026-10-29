package io.github.magalz.borealis.library.hosts

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.Host
import io.github.magalz.borealis.library.domain.HostCapability
import io.github.magalz.borealis.library.domain.HostRepository
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.domain.WakeResult
import io.github.magalz.borealis.library.testing.FakeHostRepository
import io.github.magalz.borealis.library.testing.MainDispatcherRule
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.subscribe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HostsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeHostRepository()
    // Lazy: a ViewModel must be created after the rule has set the main dispatcher.
    private val viewModel by lazy { HostsViewModel(repository) }

    /** A repository whose hosts are reported only when the test emits them. */
    private fun repositoryEmitting(reports: Flow<List<Host>>): HostRepository =
        object : HostRepository by repository {
            override val hosts = reports
        }

    @Test
    fun `the state is loading and not searching before the repository reported the hosts`() =
        runTest {
            val pending = MutableSharedFlow<List<Host>>()
            val viewModel = HostsViewModel(repositoryEmitting(pending))
            subscribe(viewModel.uiState)
            runCurrent()

            assertThat(viewModel.uiState.value.loading).isTrue()
            assertThat(viewModel.uiState.value.searching).isFalse()
            assertThat(viewModel.uiState.value.items).isEmpty()
        }

    @Test
    fun `a first report without hosts ends the loading and starts the search`() = runTest {
        val pending = MutableSharedFlow<List<Host>>()
        val viewModel = HostsViewModel(repositoryEmitting(pending))
        subscribe(viewModel.uiState)
        runCurrent()

        pending.emit(emptyList())
        runCurrent()

        assertThat(viewModel.uiState.value.loading).isFalse()
        assertThat(viewModel.uiState.value.searching).isTrue()
    }

    @Test
    fun `a first report with hosts shows them without ever searching`() = runTest {
        val pending = MutableSharedFlow<List<Host>>()
        val viewModel = HostsViewModel(repositoryEmitting(pending))
        val states = mutableListOf<HostsUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }
        runCurrent()

        pending.emit(listOf(aHost(id = "a", name = "Alpha")))
        runCurrent()

        assertThat(states.any { it.searching }).isFalse()
        assertThat(states.last().loading).isFalse()
        assertThat(states.last().items.map { it.id }).containsExactly("a")
    }

    @Test
    fun `an empty list means the screen is searching`() = runTest {
        subscribe(viewModel.uiState)

        assertThat(viewModel.uiState.value.searching).isTrue()
        assertThat(viewModel.uiState.value.items).isEmpty()
    }

    @Test
    fun `a known host ends the search`() = runTest {
        subscribe(viewModel.uiState)

        repository.emit(aHost())
        runCurrent()

        assertThat(viewModel.uiState.value.searching).isFalse()
    }

    @Test
    fun `the items carry the host fields and are sorted by name ignoring case`() = runTest {
        subscribe(viewModel.uiState)

        repository.emit(
            aHost(id = "c", name = "zeta"),
            aHost(id = "a", name = "Alpha", runningApp = RunningApp(7, "Steam")),
            aHost(id = "b", name = "beta", reachability = Reachability.OFFLINE, paired = false),
        )
        runCurrent()

        assertThat(viewModel.uiState.value.items)
            .containsExactly(
                HostItemUiState(
                    "a",
                    "Alpha",
                    Reachability.ONLINE,
                    true,
                    RunningApp(7, "Steam"),
                    HostAction.OPEN,
                ),
                HostItemUiState("b", "beta", Reachability.OFFLINE, false, null, HostAction.NONE),
                HostItemUiState("c", "zeta", Reachability.ONLINE, true, null, HostAction.OPEN),
            )
            .inOrder()
    }

    @Test
    fun `the items carry the address`() = runTest {
        subscribe(viewModel.uiState)

        repository.emit(aHost().copy(address = "192.168.1.20"))
        runCurrent()

        assertThat(viewModel.uiState.value.items.single().address).isEqualTo("192.168.1.20")
    }

    @Test
    fun `only an online host that is not paired was found on the network`() = runTest {
        subscribe(viewModel.uiState)

        repository.emit(
            aHost(id = "a", name = "A", reachability = Reachability.ONLINE, paired = false),
            aHost(id = "b", name = "B", reachability = Reachability.ONLINE, paired = true),
            aHost(id = "c", name = "C", reachability = Reachability.OFFLINE, paired = false),
            aHost(id = "d", name = "D", reachability = Reachability.UNKNOWN, paired = false),
        )
        runCurrent()

        assertThat(viewModel.uiState.value.items.map { it.foundOnNetwork })
            .containsExactly(true, false, false, false)
            .inOrder()
    }

    @Test
    fun `the tags are the best codec, HDR and 4-4-4 in that order`() = runTest {
        subscribe(viewModel.uiState)

        repository.emit(
            aHost()
                .copy(
                    capabilities =
                        setOf(
                            HostCapability.YUV_444,
                            HostCapability.HDR,
                            HostCapability.HEVC,
                            HostCapability.HEVC_10_BIT,
                        )
                )
        )
        runCurrent()

        assertThat(viewModel.uiState.value.items.single().tags)
            .containsExactly(
                HostCapability.HEVC_10_BIT,
                HostCapability.HDR,
                HostCapability.YUV_444,
            )
            .inOrder()
    }

    @Test
    fun `a 10-bit codec beats an 8-bit one and AV1 shows only without HEVC 10-bit`() = runTest {
        subscribe(viewModel.uiState)
        val hevc = setOf(HostCapability.HEVC)
        val av1 = setOf(HostCapability.AV1)

        repository.emit(
            aHost(id = "a", name = "A").copy(capabilities = hevc + av1),
            aHost(id = "b", name = "B").copy(capabilities = hevc + HostCapability.AV1_10_BIT),
            aHost(id = "c", name = "C")
                .copy(capabilities = setOf(HostCapability.HEVC_10_BIT) + av1),
            aHost(id = "d", name = "D").copy(capabilities = setOf(HostCapability.AV1_10_BIT)),
            aHost(id = "e", name = "E").copy(capabilities = av1),
        )
        runCurrent()

        assertThat(viewModel.uiState.value.items.map { it.tags })
            .containsExactly(
                listOf(HostCapability.HEVC),
                listOf(HostCapability.AV1_10_BIT),
                listOf(HostCapability.HEVC_10_BIT),
                listOf(HostCapability.AV1_10_BIT),
                listOf(HostCapability.AV1),
            )
            .inOrder()
    }

    @Test
    fun `a host without capabilities has no tags and an offline host shows none`() = runTest {
        subscribe(viewModel.uiState)
        val everything = HostCapability.entries.toSet()

        repository.emit(
            aHost(id = "a", name = "A").copy(capabilities = emptySet()),
            aHost(id = "b", name = "B", reachability = Reachability.OFFLINE)
                .copy(capabilities = everything),
            aHost(id = "c", name = "C").copy(capabilities = everything),
        )
        runCurrent()

        val tags = viewModel.uiState.value.items.map { it.tags }
        assertThat(tags[0]).isEmpty()
        assertThat(tags[1]).isEmpty()
        assertThat(tags[2]).hasSize(3)
    }

    @Test
    fun `an online paired host opens its apps`() = runTest {
        subscribe(viewModel.uiState)

        repository.emit(aHost(reachability = Reachability.ONLINE, paired = true))
        runCurrent()

        assertThat(viewModel.uiState.value.items.single().action).isEqualTo(HostAction.OPEN)
    }

    @Test
    fun `an online host that is not paired offers to pair`() = runTest {
        subscribe(viewModel.uiState)

        repository.emit(aHost(reachability = Reachability.ONLINE, paired = false))
        runCurrent()

        assertThat(viewModel.uiState.value.items.single().action).isEqualTo(HostAction.PAIR)
    }

    @Test
    fun `an offline host with a MAC address offers to wake`() = runTest {
        subscribe(viewModel.uiState)

        repository.emit(aHost(reachability = Reachability.OFFLINE).copy(canWake = true))
        runCurrent()

        assertThat(viewModel.uiState.value.items.single().action).isEqualTo(HostAction.WAKE)
    }

    @Test
    fun `an offline host without a MAC address and an unknown host offer nothing`() = runTest {
        subscribe(viewModel.uiState)

        repository.emit(
            aHost(id = "a", name = "A", reachability = Reachability.OFFLINE).copy(canWake = false),
            aHost(id = "b", name = "B", reachability = Reachability.UNKNOWN).copy(canWake = true),
        )
        runCurrent()

        assertThat(viewModel.uiState.value.items.map { it.action })
            .containsExactly(HostAction.NONE, HostAction.NONE)
    }

    @Test
    fun `continue lists only online paired hosts with a running app in host name order`() =
        runTest {
            subscribe(viewModel.uiState)

            repository.emit(
                aHost(id = "z", name = "Zed", runningApp = RunningApp(2, "Game")),
                aHost(id = "idle", name = "Idle"),
                aHost(
                    id = "off",
                    name = "Off",
                    reachability = Reachability.OFFLINE,
                    runningApp = RunningApp(3, "X"),
                ),
                aHost(
                    id = "unpaired",
                    name = "Unpaired",
                    paired = false,
                    runningApp = RunningApp(4, "Y"),
                ),
                aHost(id = "a", name = "alpha", runningApp = RunningApp(1, "Desktop")),
            )
            runCurrent()

            assertThat(viewModel.uiState.value.continueItems)
                .containsExactly(
                    ContinueItemUiState("a", "alpha", 1, "Desktop"),
                    ContinueItemUiState("z", "Zed", 2, "Game"),
                )
                .inOrder()
        }

    @Test
    fun `an app without a known name is still running and still continues`() = runTest {
        subscribe(viewModel.uiState)
        repository.emit(aHost(id = "h", name = "Den", runningApp = RunningApp(7, null)))
        runCurrent()

        assertThat(viewModel.uiState.value.items.single().runningApp).isEqualTo(RunningApp(7, null))
        assertThat(viewModel.uiState.value.continueItems)
            .containsExactly(ContinueItemUiState("h", "Den", 7, null))

        viewModel.onContinue("h")

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(
                    HostsEffect.StartStream(
                        StreamRequest("h", 7, null, hdrSupported = false, resumeChosen = true)
                    )
                )
        }
    }

    @Test
    fun `selecting an online paired host opens its apps`() = runTest {
        subscribe(viewModel.uiState)
        repository.emit(aHost(id = "h"))
        runCurrent()

        viewModel.onHostSelected("h")

        viewModel.effects.test { assertThat(awaitItem()).isEqualTo(HostsEffect.OpenApps("h")) }
    }

    @Test
    fun `selecting an online host that is not paired opens pairing`() = runTest {
        subscribe(viewModel.uiState)
        repository.emit(aHost(id = "h", paired = false))
        runCurrent()

        viewModel.onHostSelected("h")

        viewModel.effects.test { assertThat(awaitItem()).isEqualTo(HostsEffect.OpenPairing("h")) }
    }

    @Test
    fun `selecting an offline host wakes it`() = runTest {
        subscribe(viewModel.uiState)
        repository.emit(aHost(id = "h", reachability = Reachability.OFFLINE).copy(canWake = true))
        runCurrent()

        viewModel.onHostSelected("h")
        runCurrent()

        assertThat(repository.wokenHostIds).containsExactly("h")
    }

    @Test
    fun `selecting a host that was not polled yet says it is still being checked`() = runTest {
        subscribe(viewModel.uiState)
        repository.emit(aHost(id = "h", reachability = Reachability.UNKNOWN))
        runCurrent()

        viewModel.onHostSelected("h")

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(HostsEffect.ShowMessage(HostsMessage.HOST_CHECKING))
        }
    }

    @Test
    fun `selecting an offline host that cannot wake says it is unavailable`() = runTest {
        subscribe(viewModel.uiState)
        repository.emit(aHost(id = "h", reachability = Reachability.OFFLINE).copy(canWake = false))
        runCurrent()

        viewModel.onHostSelected("h")

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(HostsEffect.ShowMessage(HostsMessage.HOST_UNAVAILABLE))
        }
    }

    @Test
    fun `selecting an unknown host does nothing`() = runTest {
        subscribe(viewModel.uiState)

        viewModel.onHostSelected("gone")
        runCurrent()

        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `continue starts the running app without HDR`() = runTest {
        subscribe(viewModel.uiState)
        repository.emit(aHost(id = "h", runningApp = RunningApp(9, "Steam")))
        runCurrent()

        viewModel.onContinue("h")

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(
                    HostsEffect.StartStream(
                        StreamRequest("h", 9, "Steam", hdrSupported = false, resumeChosen = true)
                    )
                )
        }
    }

    @Test
    fun `continue does nothing for a host that has no running app`() = runTest {
        subscribe(viewModel.uiState)
        repository.emit(aHost(id = "h"))
        runCurrent()

        viewModel.onContinue("h")
        runCurrent()

        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `every wake result becomes its message`() = runTest {
        val expected =
            mapOf(
                WakeResult.SENT to HostsMessage.WAKE_SENT,
                WakeResult.ALREADY_ONLINE to HostsMessage.ALREADY_ONLINE,
                WakeResult.NO_MAC_ADDRESS to HostsMessage.NO_MAC_ADDRESS,
                WakeResult.FAILED to HostsMessage.WAKE_FAILED,
            )
        viewModel.effects.test {
            for ((result, message) in expected) {
                repository.wakeResult = result
                viewModel.onWake("h")
                assertThat(awaitItem()).isEqualTo(HostsEffect.ShowMessage(message))
            }
        }
        assertThat(repository.wokenHostIds).containsExactly("h", "h", "h", "h")
    }

    @Test
    fun `forget removes the host`() = runTest {
        viewModel.onForget("h")
        runCurrent()

        assertThat(repository.forgottenHostIds).containsExactly("h")
    }

    @Test
    fun `details opens a dialog with the host text until it is dismissed`() = runTest {
        subscribe(viewModel.uiState)
        repository.emit(aHost(id = "h"))
        repository.detailsResult = "Desk PC\n192.168.1.20"
        runCurrent()

        viewModel.onDetails("h")
        runCurrent()
        assertThat(viewModel.uiState.value.details).isEqualTo("Desk PC\n192.168.1.20")

        viewModel.onDetailsDismissed()
        runCurrent()
        assertThat(viewModel.uiState.value.details).isNull()
    }

    @Test
    fun `forgetting a host closes its open details dialog but not another host's`() = runTest {
        subscribe(viewModel.uiState)
        repository.emit(aHost(id = "h"), aHost(id = "other", name = "Other"))
        repository.detailsResult = "Desk PC"
        runCurrent()
        viewModel.onDetails("h")
        runCurrent()

        viewModel.onForget("other")
        runCurrent()
        assertThat(viewModel.uiState.value.details).isEqualTo("Desk PC")

        viewModel.onForget("h")
        runCurrent()
        assertThat(viewModel.uiState.value.details).isNull()
    }

    @Test
    fun `add host and settings are navigation effects`() = runTest {
        viewModel.onAddHost()
        viewModel.onSettings()

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(HostsEffect.OpenAddHost)
            assertThat(awaitItem()).isEqualTo(HostsEffect.OpenSettings)
        }
    }

    @Test
    fun `polling stops five seconds after the last collector leaves`() = runTest {
        val screen = subscribe(viewModel.uiState)
        assertThat(repository.activeCollectors).isEqualTo(1)

        screen.cancel()
        advanceTimeBy(4_999)
        runCurrent()
        assertThat(repository.activeCollectors).isEqualTo(1)

        advanceTimeBy(2)
        runCurrent()
        assertThat(repository.activeCollectors).isEqualTo(0)
    }
}
