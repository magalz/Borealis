package io.github.magalz.borealis.library.apps

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.App
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.testing.FakeAppRepository
import io.github.magalz.borealis.library.testing.FakeHostShortcuts
import io.github.magalz.borealis.library.testing.MainDispatcherRule
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.anApp
import io.github.magalz.borealis.library.testing.subscribe
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The header of the app page after a Disconnect. Polling stops during the stream, so the host that
 * said nothing runs before it turns UNKNOWN when the app page returns, while the app is still
 * running on the host: the header then says it is checking, never that nothing runs (device round
 * of Sprint 17a, finding 6).
 */
class AppsViewModelDisconnectTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeAppRepository()
    private val shortcuts = FakeHostShortcuts(canPinApps = false)
    // Lazy: a ViewModel must be created after the rule has set the main dispatcher.
    private val viewModel by lazy { AppsViewModel("host-1", repository, shortcuts) }
    private val state
        get() = viewModel.uiState.value

    private val desktop = anApp(id = 1, name = "Desktop")

    private fun snapshot(reachability: Reachability, running: App? = null) =
        AppsSnapshot(
            host =
                aHost(
                    id = "host-1",
                    name = "Desk PC",
                    reachability = reachability,
                    runningApp = running?.let { RunningApp(it.id, it.name) },
                ),
            apps = listOf(desktop),
            loading = false,
        )

    @Test
    fun `the header does not say nothing runs while the host is checked after a Disconnect`() =
        runTest {
            repository.emit(snapshot(Reachability.ONLINE))
            val screen = subscribe(viewModel.uiState)
            assertThat(state.runningAppId).isNull()
            assertThat(state.hostChecking).isFalse()

            // The stream: the screen leaves and, after the grace period, polling stops.
            screen.cancel()
            advanceTimeBy(5_001)
            runCurrent()
            assertThat(repository.activeCollectors).isEqualTo(0)

            // The Disconnect: the app page returns while the poll of the host timed out.
            repository.emit(snapshot(Reachability.UNKNOWN))
            subscribe(viewModel.uiState)
            assertThat(state.runningAppId).isNull()
            assertThat(state.hostChecking).isTrue()

            // The host answers: Desktop is still running on it.
            repository.emit(snapshot(Reachability.ONLINE, running = desktop))
            runCurrent()
            assertThat(state.runningAppName).isEqualTo("Desktop")
            assertThat(state.hostChecking).isFalse()
        }

    @Test
    fun `a host that is checked keeps the app its snapshot says is running`() = runTest {
        repository.emit(snapshot(Reachability.UNKNOWN, running = desktop))
        subscribe(viewModel.uiState)

        assertThat(state.runningAppId).isEqualTo(1)
        assertThat(state.runningAppName).isEqualTo("Desktop")
        assertThat(state.hostChecking).isTrue()
    }
}
