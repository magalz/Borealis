package io.github.magalz.borealis.library.hosts

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.domain.WakeResult
import io.github.magalz.borealis.library.testing.FakeHostRepository
import io.github.magalz.borealis.library.testing.MainDispatcherRule
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.subscribe
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class HostsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeHostRepository()
    // Lazy: a ViewModel must be created after the rule has set the main dispatcher.
    private val viewModel by lazy { HostsViewModel(repository) }

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
                HostItemUiState("a", "Alpha", Reachability.ONLINE, true, "Steam", HostAction.OPEN),
                HostItemUiState("b", "beta", Reachability.OFFLINE, false, null, HostAction.NONE),
                HostItemUiState("c", "zeta", Reachability.ONLINE, true, null, HostAction.OPEN),
            )
            .inOrder()
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

        repository.emit(aHost(reachability = Reachability.OFFLINE, canWake = true))
        runCurrent()

        assertThat(viewModel.uiState.value.items.single().action).isEqualTo(HostAction.WAKE)
    }

    @Test
    fun `an offline host without a MAC address and an unknown host offer nothing`() = runTest {
        subscribe(viewModel.uiState)

        repository.emit(
            aHost(id = "a", name = "A", reachability = Reachability.OFFLINE, canWake = false),
            aHost(id = "b", name = "B", reachability = Reachability.UNKNOWN, canWake = true),
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
        repository.emit(aHost(id = "h", reachability = Reachability.OFFLINE, canWake = true))
        runCurrent()

        viewModel.onHostSelected("h")
        runCurrent()

        assertThat(repository.wokenHostIds).containsExactly("h")
    }

    @Test
    fun `selecting a host with no action says it is unavailable`() = runTest {
        subscribe(viewModel.uiState)
        repository.emit(aHost(id = "h", reachability = Reachability.UNKNOWN))
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
                    HostsEffect.StartStream(StreamRequest("h", 9, "Steam", hdrSupported = false))
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
