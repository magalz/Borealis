package io.github.magalz.borealis.library.apps

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.App
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.testing.FakeAppRepository
import io.github.magalz.borealis.library.testing.FakeHostShortcuts
import io.github.magalz.borealis.library.testing.MainDispatcherRule
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.anApp
import io.github.magalz.borealis.library.testing.subscribe
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Only the user's own tap on the running app or the Resume of its actions is the choice to resume
 * it: the stream then joins it without asking. A launch request is not, whether it comes from Try
 * again on the stream error screen, a shortcut or a TV program: the host does not tell whose
 * session runs, so the stream asks.
 */
class AppsViewModelResumeTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeAppRepository()
    private val shortcuts = FakeHostShortcuts(canPinApps = false)
    // Lazy: a ViewModel must be created after the rule has set the main dispatcher.
    private val viewModel by lazy { AppsViewModel("host-1", repository, shortcuts) }

    private val desktop = anApp(id = 1, name = "Desktop")
    private val steam = anApp(id = 2, name = "steam", hdrSupported = true)

    private fun snapshot(
        vararg apps: App,
        running: App? = null,
        reachability: Reachability = Reachability.ONLINE,
    ) =
        AppsSnapshot(
            host =
                aHost(
                    id = "host-1",
                    name = "Desk PC",
                    reachability = reachability,
                    runningApp = running?.let { RunningApp(it.id, it.name) },
                ),
            apps = apps.toList(),
            loading = false,
        )

    private val desktopRequest = StreamRequest("host-1", 1, "Desktop", hdrSupported = false)

    @Test
    fun `selecting the running app resumes it, and says the user chose to`() = runTest {
        repository.emit(snapshot(desktop, steam, running = desktop))
        subscribe(viewModel.uiState)

        viewModel.onAppSelected(1)

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(AppsEffect.StartStream(desktopRequest.copy(resumeChosen = true)))
        }
    }

    @Test
    fun `resume starts the running app, and says the user chose to`() = runTest {
        repository.emit(snapshot(desktop, running = desktop))
        subscribe(viewModel.uiState)
        viewModel.onActions(1)
        runCurrent()

        viewModel.onActionChosen(AppAction.RESUME)

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(AppsEffect.StartStream(desktopRequest.copy(resumeChosen = true)))
        }
    }

    @Test
    fun `a launch request for the running app is not the user's choice to resume`() = runTest {
        repository.emit(snapshot(desktop, steam, running = desktop))
        subscribe(viewModel.uiState)

        viewModel.onLaunchRequested(1)

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.StartStream(desktopRequest))
        }
    }

    @Test
    fun `quit and start does not say the user chose to resume`() = runTest {
        repository.emit(snapshot(desktop, steam, running = desktop))
        subscribe(viewModel.uiState)
        viewModel.onActions(2)
        runCurrent()

        viewModel.onActionChosen(AppAction.QUIT_AND_START)

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(
                    AppsEffect.StartStream(StreamRequest("host-1", 2, "steam", hdrSupported = true))
                )
        }
    }

    @Test
    fun `a launch request from Try again for the app that runs is not the user's choice`() =
        runTest {
            repository.emit(snapshot(desktop, steam, running = desktop))
            subscribe(viewModel.uiState)

            viewModel.onLaunchRequested(1)

            viewModel.effects.test {
                assertThat(awaitItem()).isEqualTo(AppsEffect.StartStream(desktopRequest))
            }
        }

    @Test
    fun `Try again starts the app as before when the Library shows nothing running`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)

        viewModel.onLaunchRequested(1)

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(AppsEffect.StartStream(desktopRequest))
        }
    }

    @Test
    fun `a launch request that waits for the first poll is not the user's choice`() = runTest {
        repository.emit(snapshot(desktop, steam, reachability = Reachability.UNKNOWN))
        subscribe(viewModel.uiState)
        viewModel.onLaunchRequested(1)

        viewModel.effects.test {
            expectNoEvents()

            repository.emit(snapshot(desktop, steam, running = desktop))
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AppsEffect.StartStream(desktopRequest))
        }
    }

    @Test
    fun `Try again while another app runs opens the actions`() = runTest {
        repository.emit(snapshot(desktop, steam, running = steam))
        subscribe(viewModel.uiState)

        viewModel.onLaunchRequested(1)
        runCurrent()

        assertThat(viewModel.uiState.value.actions?.app?.id).isEqualTo(1)
        viewModel.effects.test { expectNoEvents() }
    }
}
