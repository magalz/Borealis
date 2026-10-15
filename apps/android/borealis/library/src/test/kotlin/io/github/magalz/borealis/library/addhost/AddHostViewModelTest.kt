package io.github.magalz.borealis.library.addhost

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.AddHostResult
import io.github.magalz.borealis.library.domain.HostAddress
import io.github.magalz.borealis.library.testing.FakeHostRepository
import io.github.magalz.borealis.library.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AddHostViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeHostRepository()
    private val viewModel = AddHostViewModel(repository)

    @Test
    fun `typing keeps the address`() = runTest {
        viewModel.onAddressChanged("192.168.1.20")

        assertThat(viewModel.uiState.value.address).isEqualTo("192.168.1.20")
    }

    @Test
    fun `an address without a port is added with the default port`() = runTest {
        viewModel.onAddressChanged("192.168.1.20")
        viewModel.onSubmit()
        runCurrent()

        assertThat(repository.addedAddresses).containsExactly(HostAddress("192.168.1.20", 47989))
    }

    @Test
    fun `an address with a port is added with that port`() = runTest {
        viewModel.onAddressChanged("192.168.1.20:48000")
        viewModel.onSubmit()
        runCurrent()

        assertThat(repository.addedAddresses).containsExactly(HostAddress("192.168.1.20", 48000))
    }

    @Test
    fun `an added host reports its name and is not busy any more`() = runTest {
        repository.addHostResult = AddHostResult.Added("id", "Desk PC")
        viewModel.onAddressChanged("192.168.1.20")

        viewModel.onSubmit()

        viewModel.effects.test { assertThat(awaitItem()).isEqualTo(AddHostEffect.Added("Desk PC")) }
        assertThat(viewModel.uiState.value.busy).isFalse()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `an empty field is an error and calls nothing`() = runTest {
        viewModel.onAddressChanged("  ")
        viewModel.onSubmit()
        runCurrent()

        assertThat(viewModel.uiState.value.error).isEqualTo(AddHostError.EMPTY)
        assertThat(repository.addedAddresses).isEmpty()
    }

    @Test
    fun `a malformed address is an error and calls nothing`() = runTest {
        viewModel.onAddressChanged("pc:99999")
        viewModel.onSubmit()
        runCurrent()

        assertThat(viewModel.uiState.value.error).isEqualTo(AddHostError.INVALID)
        assertThat(repository.addedAddresses).isEmpty()
    }

    @Test
    fun `each failure result becomes its error`() = runTest {
        val expected =
            mapOf(
                AddHostResult.InvalidAddress to AddHostError.INVALID,
                AddHostResult.WrongSubnet to AddHostError.WRONG_SUBNET,
                AddHostResult.Unreachable to AddHostError.UNREACHABLE,
            )
        viewModel.onAddressChanged("192.168.1.20")

        for ((result, error) in expected) {
            repository.addHostResult = result
            viewModel.onSubmit()
            runCurrent()

            assertThat(viewModel.uiState.value.error).isEqualTo(error)
            assertThat(viewModel.uiState.value.busy).isFalse()
        }
    }

    @Test
    fun `editing the address clears the error`() = runTest {
        viewModel.onSubmit()
        assertThat(viewModel.uiState.value.error).isEqualTo(AddHostError.EMPTY)

        viewModel.onAddressChanged("1")

        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `a second submit while busy is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.addHostGate = gate
        viewModel.onAddressChanged("192.168.1.20")

        viewModel.onSubmit()
        runCurrent()
        assertThat(viewModel.uiState.value.busy).isTrue()

        viewModel.onSubmit()
        runCurrent()
        assertThat(repository.addedAddresses).hasSize(1)

        gate.complete(Unit)
        runCurrent()
        assertThat(viewModel.uiState.value.busy).isFalse()
    }

    @Test
    fun `cancel closes without calling the repository`() = runTest {
        viewModel.onAddressChanged("192.168.1.20")

        viewModel.onCancel()

        viewModel.effects.test { assertThat(awaitItem()).isEqualTo(AddHostEffect.Close) }
        assertThat(repository.addedAddresses).isEmpty()
    }
}
