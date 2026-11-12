package io.github.magalz.borealis.library.apps

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.anApp
import io.github.magalz.borealis.library.testing.snapshot
import io.github.magalz.borealis.library.testing.subscribe
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A shortcut tapped while Borealis was closed asks for its launch from the app list that the host
 * had cached, while the host is not polled yet and has no address: the stream would find it
 * offline. The launch waits for the first poll to answer. The poller reports an offline host after
 * the first polls that nobody answered, so that answer comes and the wait has an end.
 */
class AppsViewModelFirstPollTest : AppsViewModelTestBase() {
    private val desktop = anApp(id = 1, name = "Desktop")
    private val steam = anApp(id = 2, name = "steam", hdrSupported = true)
    private val zelda = anApp(id = 3, name = "Zelda")

    private val steamRequest = StreamRequest("host-1", 2, "steam", hdrSupported = true)

    @Test
    fun `a launch request waits for the first poll, then starts the app`() = runTest {
        repository.emit(snapshot(desktop, steam, reachability = Reachability.UNKNOWN))
        subscribe(viewModel.uiState)

        viewModel.onLaunchRequested(2)
        runCurrent()

        viewModel.effects.test {
            expectNoEvents()

            repository.emit(snapshot(desktop, steam))
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AppsEffect.StartStream(steamRequest))
        }
    }

    @Test
    fun `a launch request keeps waiting while the host is still not polled`() = runTest {
        repository.emit(snapshot(desktop, steam, reachability = Reachability.UNKNOWN))
        subscribe(viewModel.uiState)
        viewModel.onLaunchRequested(2)

        repository.emit(snapshot(desktop, steam, zelda, reachability = Reachability.UNKNOWN))
        runCurrent()

        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `a launch request that waited starts the app once`() = runTest {
        repository.emit(snapshot(desktop, steam, reachability = Reachability.UNKNOWN))
        subscribe(viewModel.uiState)
        viewModel.onLaunchRequested(2)

        repository.emit(snapshot(desktop, steam))
        runCurrent()
        repository.emit(snapshot(desktop, steam, zelda))
        runCurrent()

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.StartStream(steamRequest))
            expectNoEvents()
        }
    }

    @Test
    fun `a launch request that waited meets the state that the first poll found`() = runTest {
        repository.emit(snapshot(desktop, steam, reachability = Reachability.UNKNOWN))
        subscribe(viewModel.uiState)
        viewModel.onLaunchRequested(2)

        repository.emit(snapshot(desktop, steam, running = desktop))
        runCurrent()

        assertThat(viewModel.uiState.value.actions?.app?.id).isEqualTo(2)
        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `a newer launch request replaces the one that waits`() = runTest {
        repository.emit(snapshot(desktop, steam, reachability = Reachability.UNKNOWN))
        subscribe(viewModel.uiState)
        viewModel.onLaunchRequested(1)
        viewModel.onLaunchRequested(2)

        repository.emit(snapshot(desktop, steam))
        runCurrent()

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.StartStream(steamRequest))
            expectNoEvents()
        }
    }

    @Test
    fun `a host whose first poll ends offline closes the screen as offline and starts nothing`() =
        runTest {
            repository.emit(snapshot(desktop, steam, reachability = Reachability.UNKNOWN))
            subscribe(viewModel.uiState)
            viewModel.onLaunchRequested(2)

            repository.emit(snapshot(desktop, steam, reachability = Reachability.OFFLINE))
            runCurrent()

            viewModel.effects.test {
                assertThat(awaitItem()).isEqualTo(AppsEffect.Close(AppsMessage.HostOffline))
                expectNoEvents()
            }
        }

    @Test
    fun `a launch request that waited does not start after the host answered offline`() = runTest {
        repository.emit(snapshot(desktop, steam, reachability = Reachability.UNKNOWN))
        subscribe(viewModel.uiState)
        viewModel.onLaunchRequested(2)
        repository.emit(snapshot(desktop, steam, reachability = Reachability.OFFLINE))
        runCurrent()

        repository.emit(snapshot(desktop, steam))
        runCurrent()

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.Close(AppsMessage.HostOffline))
            expectNoEvents()
        }
    }

    @Test
    fun `a host that answers but is not paired closes the screen and starts nothing`() = runTest {
        repository.emit(snapshot(desktop, steam, reachability = Reachability.UNKNOWN))
        subscribe(viewModel.uiState)
        viewModel.onLaunchRequested(2)

        repository.emit(snapshot(desktop, steam, host = aHost(paired = false)))
        runCurrent()

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.Close(AppsMessage.HostNotPaired))
            expectNoEvents()
        }
    }
}
