package io.github.magalz.borealis.library.apps

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.RunningApp
import io.github.magalz.borealis.library.shell.ResumeChoice
import io.github.magalz.borealis.library.testing.FakeAppRepository
import io.github.magalz.borealis.library.testing.FakeHostShortcuts
import io.github.magalz.borealis.library.testing.MainDispatcherRule
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.anApp
import io.github.magalz.borealis.library.testing.subscribe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The actions say whether the running app is Desktop, so Quit can tell what it costs. */
class AppsViewModelDesktopTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeAppRepository()
    private val shortcuts = FakeHostShortcuts(canPinApps = false)
    // Lazy: a ViewModel must be created after the rule has set the main dispatcher.
    private val viewModel by lazy { AppsViewModel("host-1", repository, shortcuts, ResumeChoice()) }

    private val desktop = anApp(id = 1, name = "Desktop")
    private val steam = anApp(id = 2, name = "steam")

    private fun emitRunning(running: RunningApp?) {
        repository.emit(
            AppsSnapshot(
                host = aHost(id = "host-1", runningApp = running),
                apps = listOf(desktop, steam),
                loading = false,
            )
        )
    }

    private fun TestScope.actionsOf(appId: Int): AppActionsUiState? {
        viewModel.onActions(appId)
        runCurrent()
        return viewModel.uiState.value.actions
    }

    @Test
    fun `the actions of the running app say when it is Desktop`() = runTest {
        emitRunning(RunningApp(1, "Desktop", isDesktop = true))
        subscribe(viewModel.uiState)
        runCurrent()

        assertThat(actionsOf(1)?.runningAppIsDesktop).isTrue()
    }

    @Test
    fun `the actions of another app say that the running app is Desktop`() = runTest {
        emitRunning(RunningApp(1, "Desktop", isDesktop = true))
        subscribe(viewModel.uiState)
        runCurrent()

        assertThat(actionsOf(2)?.runningAppIsDesktop).isTrue()
    }

    @Test
    fun `the actions say the running app is not Desktop when it is a game`() = runTest {
        emitRunning(RunningApp(2, "steam"))
        subscribe(viewModel.uiState)
        runCurrent()

        assertThat(actionsOf(2)?.runningAppIsDesktop).isFalse()
        viewModel.onActionsDismissed()
        assertThat(actionsOf(1)?.runningAppIsDesktop).isFalse()
    }

    @Test
    fun `the actions say the running app is not Desktop when nothing runs`() = runTest {
        emitRunning(null)
        subscribe(viewModel.uiState)
        runCurrent()

        assertThat(actionsOf(1)?.runningAppIsDesktop).isFalse()
    }
}
