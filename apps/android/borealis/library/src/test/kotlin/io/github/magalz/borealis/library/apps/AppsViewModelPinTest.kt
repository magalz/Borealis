package io.github.magalz.borealis.library.apps

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.testing.anApp
import io.github.magalz.borealis.library.testing.snapshot
import io.github.magalz.borealis.library.testing.subscribe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Pinning an app waits for its box art; the screen does not. */
class AppsViewModelPinTest : AppsViewModelTestBase(canPinApps = true) {
    private val desktop = anApp(id = 1, name = "Desktop")

    @Test
    fun `a pin that waits for the box art does not hold the screen, and a refusal is reported after it`() =
        runTest {
            shortcuts.pinResult = false
            val gate = CompletableDeferred<Unit>()
            shortcuts.pinGate = gate
            repository.emit(snapshot(desktop))
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
