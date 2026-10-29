package io.github.magalz.borealis.library.apps

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.App
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.shell.ResumeChoice
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
 * Only the user's own tap on the running app, the Resume of its actions, or Try again on the stream
 * error screen is the choice to resume it: the stream then joins it without asking. A launch
 * request from a shortcut or a TV program is not.
 */
class AppsViewModelResumeTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeAppRepository()
    private val shortcuts = FakeHostShortcuts(canPinApps = false)
    // Lazy: a ViewModel must be created after the rule has set the main dispatcher.
    private val resumeChoice = ResumeChoice()
    private val viewModel by lazy { AppsViewModel("host-1", repository, shortcuts, resumeChoice) }

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
    fun `a launch request after Try again resumes the app that runs, and says the user chose to`() =
        runTest {
            repository.emit(snapshot(desktop, steam, running = desktop))
            subscribe(viewModel.uiState)
            resumeChoice.remember("host-1", 1)

            viewModel.onLaunchRequested(1)

            viewModel.effects.test {
                assertThat(awaitItem())
                    .isEqualTo(AppsEffect.StartStream(desktopRequest.copy(resumeChosen = true)))
            }
        }

    @Test
    fun `Try again says so also when the Library does not show the app running yet`() = runTest {
        repository.emit(snapshot(desktop, steam))
        subscribe(viewModel.uiState)
        resumeChoice.remember("host-1", 1)

        viewModel.onLaunchRequested(1)

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(AppsEffect.StartStream(desktopRequest.copy(resumeChosen = true)))
        }
    }

    @Test
    fun `the choice of Try again is taken once`() = runTest {
        repository.emit(snapshot(desktop, steam, running = desktop))
        subscribe(viewModel.uiState)
        resumeChoice.remember("host-1", 1)

        viewModel.onLaunchRequested(1)
        viewModel.onLaunchRequested(1)

        viewModel.effects.test {
            assertThat(awaitItem())
                .isEqualTo(AppsEffect.StartStream(desktopRequest.copy(resumeChosen = true)))
            assertThat(awaitItem()).isEqualTo(AppsEffect.StartStream(desktopRequest))
        }
    }

    @Test
    fun `a launch request for another app, or for another host, is not the choice of Try again`() =
        runTest {
            repository.emit(snapshot(desktop, steam))
            subscribe(viewModel.uiState)
            resumeChoice.remember("host-1", 1)
            viewModel.onLaunchRequested(2)
            resumeChoice.remember("other", 1)
            viewModel.onLaunchRequested(1)
            resumeChoice.remember("host-1", 1)
            viewModel.onLaunchRequested(2)
            viewModel.onLaunchRequested(1)

            viewModel.effects.test {
                assertThat(awaitItem())
                    .isEqualTo(
                        AppsEffect.StartStream(
                            StreamRequest("host-1", 2, "steam", hdrSupported = true)
                        )
                    )
                assertThat(awaitItem()).isEqualTo(AppsEffect.StartStream(desktopRequest))
                assertThat(awaitItem())
                    .isEqualTo(
                        AppsEffect.StartStream(
                            StreamRequest("host-1", 2, "steam", hdrSupported = true)
                        )
                    )
                assertThat(awaitItem()).isEqualTo(AppsEffect.StartStream(desktopRequest))
            }
        }

    @Test
    fun `Try again while another app runs opens the actions and its choice is used up`() = runTest {
        repository.emit(snapshot(desktop, steam, running = steam))
        subscribe(viewModel.uiState)
        resumeChoice.remember("host-1", 1)

        viewModel.onLaunchRequested(1)
        runCurrent()

        assertThat(viewModel.uiState.value.actions?.app?.id).isEqualTo(1)
        assertThat(resumeChoice.take("host-1", 1)).isFalse()
        viewModel.effects.test { expectNoEvents() }
    }

    @Test
    fun `a launch request that the screen drops still uses up the choice of Try again`() = runTest {
        repository.emit(snapshot(desktop, reachability = Reachability.OFFLINE))
        subscribe(viewModel.uiState)
        resumeChoice.remember("host-1", 1)

        viewModel.onLaunchRequested(1)

        assertThat(resumeChoice.take("host-1", 1)).isFalse()
    }
}
