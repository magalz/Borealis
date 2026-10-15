package io.github.magalz.borealis.library.pairing

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.PairingInstructions
import io.github.magalz.borealis.library.domain.PairingOutcome
import io.github.magalz.borealis.library.testing.FakePairingService
import io.github.magalz.borealis.library.testing.MainDispatcherRule
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PairingViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val service = FakePairingService()

    private fun instructions(pin: String = "1234") =
        PairingInstructions(
            pin = pin,
            webUiUrl = "https://192.168.1.20:47990",
            deviceName = "Pixel",
        )

    private fun waiting(pin: String = "1234", restarted: Boolean = false) =
        PairingUiState.Waiting(pin, "https://192.168.1.20:47990", "Pixel", restarted)

    private fun viewModel(): PairingViewModel = PairingViewModel("host-1", service)

    @Test
    fun `an unknown host shows the host unknown state and pairs nothing`() = runTest {
        service.prepared += null
        val viewModel = viewModel()
        runCurrent()

        assertThat(viewModel.uiState.value).isEqualTo(PairingUiState.HostUnknown)
        assertThat(service.pairCalls).isEmpty()
    }

    @Test
    fun `the PIN is shown while pairing waits for the host`() = runTest {
        service.prepared += instructions("4321")
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value).isEqualTo(PairingUiState.Preparing)
        runCurrent()

        assertThat(viewModel.uiState.value).isEqualTo(waiting("4321"))
        assertThat(service.pairCalls.single().pin).isEqualTo("4321")
        assertThat(service.pairCalls.single().hostId).isEqualTo("host-1")
        assertThat(service.pairCalls.single().restarted).isFalse()
    }

    @Test
    fun `the string form of the waiting state hides the PIN`() {
        val text = waiting("4821").toString()

        assertThat(text).doesNotContain("4821")
        assertThat(text).contains("pin=****")
        assertThat(text).contains("deviceName=Pixel")
    }

    @Test
    fun `pairing succeeds and opens the apps`() = runTest {
        service.prepared += instructions()
        val viewModel = viewModel()
        runCurrent()

        service.pairCalls.single().outcome.complete(PairingOutcome.Paired)
        runCurrent()

        assertThat(viewModel.uiState.value).isEqualTo(PairingUiState.Paired)
        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(PairingEffect.OpenApps("host-1"))
        }
    }

    @Test
    fun `a dropped host session pairs again once with a new PIN`() = runTest {
        service.prepared += instructions("1111")
        service.prepared += instructions("2222")
        val viewModel = viewModel()
        runCurrent()

        service.pairCalls[0].outcome.complete(PairingOutcome.RestartNeeded)
        runCurrent()

        assertThat(viewModel.uiState.value).isEqualTo(waiting("2222", restarted = true))
        assertThat(service.pairCalls[1].pin).isEqualTo("2222")
        assertThat(service.pairCalls[1].restarted).isTrue()

        service.pairCalls[1].outcome.complete(PairingOutcome.Paired)
        runCurrent()
        assertThat(viewModel.uiState.value).isEqualTo(PairingUiState.Paired)
    }

    @Test
    fun `a second dropped session fails without a message`() = runTest {
        service.prepared += instructions("1111")
        service.prepared += instructions("2222")
        val viewModel = viewModel()
        runCurrent()
        service.pairCalls[0].outcome.complete(PairingOutcome.RestartNeeded)
        runCurrent()

        service.pairCalls[1].outcome.complete(PairingOutcome.RestartNeeded)
        runCurrent()

        assertThat(viewModel.uiState.value)
            .isEqualTo(PairingUiState.Failed(PairingFailure.OTHER, null))
        assertThat(service.pairCalls).hasSize(2)
    }

    @Test
    fun `each failed outcome becomes a typed failure`() = runTest {
        val expected =
            mapOf(
                PairingOutcome.PinWrong to PairingUiState.Failed(PairingFailure.PIN_WRONG, null),
                PairingOutcome.AlreadyInProgress to
                    PairingUiState.Failed(PairingFailure.ALREADY_IN_PROGRESS, null),
                PairingOutcome.HostOffline to
                    PairingUiState.Failed(PairingFailure.HOST_OFFLINE, null),
                PairingOutcome.Failed("Host said no") to
                    PairingUiState.Failed(PairingFailure.OTHER, "Host said no"),
                PairingOutcome.Failed(null) to PairingUiState.Failed(PairingFailure.OTHER, null),
            )
        for ((outcome, state) in expected) {
            val scripted = FakePairingService().apply { prepared += instructions() }
            val viewModel = PairingViewModel("host-1", scripted)
            runCurrent()

            scripted.pairCalls.single().outcome.complete(outcome)
            runCurrent()

            assertThat(viewModel.uiState.value).isEqualTo(state)
        }
    }

    @Test
    fun `a PIN that is not accepted in five minutes expires`() = runTest {
        service.prepared += instructions()
        val viewModel = viewModel()
        runCurrent()

        advanceTimeBy(5.minutes - 1.milliseconds)
        runCurrent()
        assertThat(viewModel.uiState.value).isEqualTo(waiting())
        assertThat(service.expiredHostIds).isEmpty()

        advanceTimeBy(2.milliseconds)
        runCurrent()

        assertThat(viewModel.uiState.value).isEqualTo(PairingUiState.Expired)
        assertThat(service.expiredHostIds).containsExactly("host-1")
        assertThat(service.pairCalls.single().cancelled).isTrue()
    }

    @Test
    fun `an accepted PIN does not expire later`() = runTest {
        service.prepared += instructions()
        val viewModel = viewModel()
        runCurrent()
        service.pairCalls.single().outcome.complete(PairingOutcome.Paired)
        runCurrent()

        advanceTimeBy(10.minutes)
        runCurrent()

        assertThat(viewModel.uiState.value).isEqualTo(PairingUiState.Paired)
        assertThat(service.expiredHostIds).isEmpty()
    }

    @Test
    fun `a new PIN after expiry starts again from prepare`() = runTest {
        service.prepared += instructions("1111")
        service.prepared += instructions("2222")
        val viewModel = viewModel()
        runCurrent()
        advanceTimeBy(5.minutes + 1.milliseconds)
        runCurrent()

        viewModel.onGetNewPin()
        runCurrent()

        assertThat(viewModel.uiState.value).isEqualTo(waiting("2222"))
        assertThat(service.pairCalls[1].restarted).isFalse()
    }

    @Test
    fun `a new PIN after a failure starts again from prepare`() = runTest {
        service.prepared += instructions("1111")
        service.prepared += instructions("2222")
        val viewModel = viewModel()
        runCurrent()
        service.pairCalls[0].outcome.complete(PairingOutcome.PinWrong)
        runCurrent()

        viewModel.onGetNewPin()
        runCurrent()

        assertThat(viewModel.uiState.value).isEqualTo(waiting("2222"))
    }

    @Test
    fun `asking for a new PIN while waiting changes nothing`() = runTest {
        service.prepared += instructions("1111")
        val viewModel = viewModel()
        runCurrent()

        viewModel.onGetNewPin()
        runCurrent()

        assertThat(viewModel.uiState.value).isEqualTo(waiting("1111"))
        assertThat(service.prepareCalls).hasSize(1)
    }

    @Test
    fun `cancel aborts the host request and closes without anything else`() = runTest {
        service.prepared += instructions()
        val viewModel = viewModel()
        runCurrent()

        viewModel.onCancel()
        runCurrent()

        assertThat(service.pairCalls.single().cancelled).isTrue()
        assertThat(service.expiredHostIds).isEmpty()
        assertThat(service.prepareCalls).hasSize(1)
        viewModel.effects.test { assertThat(awaitItem()).isEqualTo(PairingEffect.Close) }
    }

    @Test
    fun `a second cancel closes only once`() = runTest {
        service.prepared += instructions()
        val viewModel = viewModel()
        runCurrent()

        viewModel.onCancel()
        viewModel.onCancel()
        runCurrent()

        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(PairingEffect.Close)
            expectNoEvents()
        }
    }

    @Test
    fun `cancel after pairing succeeded does not close`() = runTest {
        service.prepared += instructions()
        val viewModel = viewModel()
        runCurrent()
        service.pairCalls.single().outcome.complete(PairingOutcome.Paired)
        runCurrent()

        viewModel.onCancel()
        runCurrent()

        assertThat(viewModel.uiState.value).isEqualTo(PairingUiState.Paired)
        viewModel.effects.test {
            assertThat(awaitItem()).isEqualTo(PairingEffect.OpenApps("host-1"))
            expectNoEvents()
        }
    }

    @Test
    fun `clearing the view model aborts the host request`() = runTest {
        service.prepared += instructions()
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider.create(store, viewModelFactory { initializer { viewModel() } })[
                    PairingViewModel::class]
        runCurrent()

        store.clear()
        runCurrent()

        assertThat(service.pairCalls.single().cancelled).isTrue()
        assertThat(viewModel.uiState.value).isEqualTo(waiting())
    }
}
