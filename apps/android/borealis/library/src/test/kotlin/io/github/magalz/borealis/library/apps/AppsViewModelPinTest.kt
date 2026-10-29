package io.github.magalz.borealis.library.apps

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.shell.ResumeChoice
import io.github.magalz.borealis.library.testing.FakeAppRepository
import io.github.magalz.borealis.library.testing.FakeHostShortcuts
import io.github.magalz.borealis.library.testing.MainDispatcherRule
import io.github.magalz.borealis.library.testing.aHost
import io.github.magalz.borealis.library.testing.anApp
import io.github.magalz.borealis.library.testing.subscribe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Pinning an app waits for its box art; the screen does not. */
class AppsViewModelPinTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeAppRepository()
    private val shortcuts = FakeHostShortcuts()
    // Lazy: a ViewModel must be created after the rule has set the main dispatcher.
    private val viewModel by lazy { AppsViewModel("host-1", repository, shortcuts, ResumeChoice()) }
    private val desktop = anApp(id = 1, name = "Desktop")

    @Test
    fun `a pin that waits for the box art does not hold the screen, and a refusal is reported after it`() =
        runTest {
            shortcuts.pinResult = false
            val gate = CompletableDeferred<Unit>()
            shortcuts.pinGate = gate
            repository.emit(
                AppsSnapshot(
                    host = aHost(id = "host-1", name = "Desk PC"),
                    apps = listOf(desktop),
                    loading = false,
                )
            )
            subscribe(viewModel.uiState)
            viewModel.onActions(1)
            runCurrent()

            viewModel.onActionChosen(AppAction.ADD_TO_HOME_SCREEN)
            runCurrent()

            assertThat(shortcuts.pinned).hasSize(1)
            assertThat(viewModel.uiState.value.actions).isNull()
            viewModel.effects.test {
                expectNoEvents()
                gate.complete(Unit)
                assertThat(awaitItem()).isEqualTo(AppsEffect.ShowMessage(AppsMessage.PinFailed))
            }
        }
}
