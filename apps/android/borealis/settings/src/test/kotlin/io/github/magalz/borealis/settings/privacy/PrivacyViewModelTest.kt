package io.github.magalz.borealis.settings.privacy

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.diagnostics.bundle.ExportResult
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import io.github.magalz.borealis.settings.testing.MainDispatcherRule
import io.github.magalz.borealis.settings.testing.subscribe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    @get:Rule val folder = TemporaryFolder()

    private val settings = FakeSettingsRepository()
    private val audit = FakeAuditTrail()

    private fun viewModel(export: DiagnosticsExport = FakeDiagnosticsExport(ExportResult.Failed)) =
        PrivacyViewModel(settings, export, audit)

    private fun bundle() = bundleFile(folder.root)

    @Test
    fun `the switches start as the repository has them`() = runTest {
        val repository =
            FakeSettingsRepository(SettingsSnapshot.DEFAULTS.with(Settings.VERBOSE_LOGGING, true))
        val viewModel =
            PrivacyViewModel(repository, FakeDiagnosticsExport(ExportResult.Failed), audit)
        subscribe(viewModel.uiState)

        assertThat(viewModel.uiState.value.verboseLogging).isTrue()
        assertThat(viewModel.uiState.value.maskAddresses).isFalse()
    }

    @Test
    fun `the switches follow the repository`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        settings.setNow(Settings.MASK_ADDRESSES, true)
        runCurrent()

        assertThat(viewModel.uiState.value.maskAddresses).isTrue()
    }

    @Test
    fun `turning a switch on writes the setting`() = runTest {
        val viewModel = viewModel()

        viewModel.onVerboseLoggingChange(true)
        viewModel.onMaskAddressesChange(true)
        runCurrent()

        assertThat(settings.writes)
            .containsExactly(
                FakeSettingsRepository.Write(Settings.VERBOSE_LOGGING, true),
                FakeSettingsRepository.Write(Settings.MASK_ADDRESSES, true),
            )
            .inOrder()
    }

    @Test
    fun `the pane is exporting until the bundle is delivered`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val viewModel = viewModel(FakeDiagnosticsExport(sampleSaved(bundle()), gate))
        subscribe(viewModel.uiState)

        viewModel.onExport()
        runCurrent()
        assertThat(viewModel.uiState.value.exporting).isTrue()

        gate.complete(Unit)
        runCurrent()
        assertThat(viewModel.uiState.value.exporting).isFalse()
    }

    @Test
    fun `a second export while one runs does nothing`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val export = FakeDiagnosticsExport(sampleSaved(bundle()), gate)
        val viewModel = viewModel(export)
        subscribe(viewModel.uiState)

        viewModel.onExport()
        runCurrent()
        viewModel.onExport()
        runCurrent()

        assertThat(export.calls).isEqualTo(1)
    }

    @Test
    fun `a share result starts the chooser and opens no dialog`() = runTest {
        val share = sampleShare(bundle())
        val viewModel = viewModel(FakeDiagnosticsExport(share))
        subscribe(viewModel.uiState)

        viewModel.effects.test {
            viewModel.onExport()
            runCurrent()

            val effect = awaitItem() as PrivacyEffect.StartShare
            assertThat(effect.chooser).isSameInstanceAs(share.chooser)
        }
        assertThat(viewModel.uiState.value.dialog).isNull()
    }

    @Test
    fun `a saved result opens the Saved dialog with where and how to fetch it`() = runTest {
        val bundle = bundle()
        val viewModel = viewModel(FakeDiagnosticsExport(sampleSaved(bundle)))
        subscribe(viewModel.uiState)

        viewModel.onExport()
        runCurrent()

        assertThat(viewModel.uiState.value.dialog)
            .isEqualTo(
                PrivacyDialog.Saved(
                    location = "/sdcard/Download/${bundle.name}",
                    adbPullCommand = "adb pull /sdcard/Download/${bundle.name}",
                    bundle = bundle,
                    fileName = bundle.name,
                    sizeBytes = SAMPLE_SIZE.toLong(),
                )
            )
    }

    @Test
    fun `a failed export says so`() = runTest {
        val viewModel = viewModel(FakeDiagnosticsExport(ExportResult.Failed))
        subscribe(viewModel.uiState)

        viewModel.effects.test {
            viewModel.onExport()
            runCurrent()

            assertThat(awaitItem())
                .isEqualTo(PrivacyEffect.ShowMessage(PrivacyMessage.ExportFailed))
        }
        assertThat(viewModel.uiState.value.dialog).isNull()
        assertThat(viewModel.uiState.value.exporting).isFalse()
    }

    @Test
    fun `dismissing the Saved dialog closes it`() = runTest {
        val viewModel = viewModel(FakeDiagnosticsExport(sampleSaved(bundle())))
        subscribe(viewModel.uiState)
        viewModel.onExport()
        runCurrent()

        viewModel.onDialogDismissed()
        runCurrent()

        assertThat(viewModel.uiState.value.dialog).isNull()
    }

    @Test
    fun `asking to erase opens the confirmation and erases nothing`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        viewModel.onEraseRequested()
        runCurrent()

        assertThat(viewModel.uiState.value.dialog).isEqualTo(PrivacyDialog.EraseConfirmation)
        assertThat(audit.eraseCalls).isEqualTo(0)
    }

    @Test
    fun `cancelling the confirmation closes it and erases nothing`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)
        viewModel.onEraseRequested()
        runCurrent()

        viewModel.onEraseCancelled()
        runCurrent()

        assertThat(viewModel.uiState.value.dialog).isNull()
        assertThat(audit.eraseCalls).isEqualTo(0)
    }

    @Test
    fun `confirming erases the trail, closes the dialog and says it is done`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)
        viewModel.onEraseRequested()
        runCurrent()

        viewModel.effects.test {
            viewModel.onEraseConfirmed()
            runCurrent()

            assertThat(awaitItem()).isEqualTo(PrivacyEffect.ShowMessage(PrivacyMessage.EraseDone))
        }
        assertThat(audit.eraseCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.dialog).isNull()
    }

    @Test
    fun `a failed erase says so`() = runTest {
        audit.eraseResult = false
        val viewModel = viewModel()
        subscribe(viewModel.uiState)
        viewModel.onEraseRequested()
        runCurrent()

        viewModel.effects.test {
            viewModel.onEraseConfirmed()
            runCurrent()

            assertThat(awaitItem()).isEqualTo(PrivacyEffect.ShowMessage(PrivacyMessage.EraseFailed))
        }
    }

    @Test
    fun `an erase that nobody asked for is not run`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel.uiState)

        viewModel.onEraseConfirmed()
        runCurrent()

        assertThat(audit.eraseCalls).isEqualTo(0)
    }
}
