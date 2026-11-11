package io.github.magalz.borealis.library.apps

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.testing.anApp
import io.github.magalz.borealis.library.testing.snapshot
import io.github.magalz.borealis.library.testing.subscribe
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The header of the app page after a Disconnect. Polling stops during the stream, so the host that
 * said nothing runs before it turns UNKNOWN when the app page returns, while the app is still
 * running on the host: the header then says it is checking, never that nothing runs (device round
 * of Sprint 17a, finding 6).
 */
class AppsViewModelDisconnectTest : AppsViewModelTestBase() {
    private val state
        get() = viewModel.uiState.value

    private val desktop = anApp(id = 1, name = "Desktop")

    @Test
    fun `the header does not say nothing runs while the host is checked after a Disconnect`() =
        runTest {
            repository.emit(snapshot(desktop, reachability = Reachability.ONLINE))
            val screen = subscribe(viewModel.uiState)
            assertThat(state.runningAppId).isNull()
            assertThat(state.hostChecking).isFalse()

            // The stream: the screen leaves and, after the grace period, polling stops.
            screen.cancel()
            advanceTimeBy(5_001)
            runCurrent()
            assertThat(repository.activeCollectors).isEqualTo(0)

            // The Disconnect: the app page returns while the poll of the host timed out.
            repository.emit(snapshot(desktop, reachability = Reachability.UNKNOWN))
            subscribe(viewModel.uiState)
            assertThat(state.runningAppId).isNull()
            assertThat(state.hostChecking).isTrue()

            // The host answers: Desktop is still running on it.
            repository.emit(
                snapshot(desktop, reachability = Reachability.ONLINE, running = desktop)
            )
            runCurrent()
            assertThat(state.runningAppName).isEqualTo("Desktop")
            assertThat(state.hostChecking).isFalse()
        }

    @Test
    fun `a host that is checked keeps the app its snapshot says is running`() = runTest {
        repository.emit(snapshot(desktop, reachability = Reachability.UNKNOWN, running = desktop))
        subscribe(viewModel.uiState)

        assertThat(state.runningAppId).isEqualTo(1)
        assertThat(state.runningAppName).isEqualTo("Desktop")
        assertThat(state.hostChecking).isTrue()
    }
}
