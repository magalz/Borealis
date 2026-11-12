package io.github.magalz.borealis.settings.privacy

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.PHONE
import io.github.magalz.borealis.designsystem.testing.TV
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.diagnostics.bundle.ExportResult
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The pane and the phone screen with a real ViewModel over fakes (UC-006, UC-007). */
@RunWith(AndroidJUnit4::class)
class PrivacyPaneTest {
    @get:Rule val composeRule = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private val settings = FakeSettingsRepository()
    private val audit = FakeAuditTrail()
    private val shared = mutableListOf<Intent>()
    private val sent = mutableListOf<Pair<File, String>>()
    private var audits = 0
    private var backs = 0

    private fun viewModel(result: ExportResult = ExportResult.Failed) =
        PrivacyViewModel(settings, FakeDiagnosticsExport(result), audit)

    private fun bundle() = bundleFile(folder.root)

    /**
     * The pane while a bundle is built, under [reduceMotion], with the clock stopped so that a test
     * says how long the bar runs. Nothing is pressed: the press of a row moves on its own.
     */
    private fun showExporting(reduceMotion: ReduceMotion) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(reduceMotion = reduceMotion)) {
                PrivacyContent(
                    PrivacyUiState(exporting = true),
                    SnackbarHostState(),
                    noopPrivacyActions(),
                    Modifier.width(PANE_WIDTH.dp),
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    /**
     * What the pane draws now. The bar has no semantics to find it by: a screen reader skips it.
     */
    private fun pane(): PixelMap = composeRule.onRoot().captureToImage().toPixelMap()

    private fun pixelsThatMoved(before: PixelMap, after: PixelMap): Int {
        assertThat(after.width to after.height).isEqualTo(before.width to before.height)
        return before.buffer.indices.count { before.buffer[it] != after.buffer[it] }
    }

    private fun showPane(
        viewModel: PrivacyViewModel,
        formFactor: FormFactor = FormFactor.Tv,
        startShare: (Intent) -> Unit = { shared += it },
    ) {
        composeRule.setContentInKeyboardMode {
            BorealisTheme(formFactor) {
                PrivacyPane(
                    onSendToPhone = { bundle, command -> sent += bundle to command },
                    onOpenAudit = { audits++ },
                    viewModel = viewModel,
                    startShare = startShare,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = TV)
    fun `a switch writes the setting and shows it`() {
        showPane(viewModel())

        composeRule.onNodeWithText("Verbose logging").performClick()
        composeRule.waitForIdle()

        assertThat(settings.writes)
            .containsExactly(FakeSettingsRepository.Write(Settings.VERBOSE_LOGGING, true))
        composeRule.onNodeWithText("Verbose logging").assertIsOn()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the Audit trail row opens the audit trail`() {
        showPane(viewModel())

        composeRule.onNodeWithText("Audit trail").performClick()

        assertThat(audits).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a share result starts the chooser`() {
        val share = sampleShare(bundle())
        showPane(viewModel(share), FormFactor.Phone)

        composeRule.onNodeWithText("Export diagnostic bundle").performClick()
        composeRule.waitForIdle()

        assertThat(shared).containsExactly(share.chooser)
        composeRule.onNodeWithText("Diagnostics saved").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a share that no activity handles says the export failed`() {
        showPane(viewModel(sampleShare(bundle())), FormFactor.Phone) {
            throw ActivityNotFoundException("no activity")
        }

        composeRule.onNodeWithText("Export diagnostic bundle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Could not export diagnostics").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a share the system refuses to start says the export failed`() {
        showPane(viewModel(sampleShare(bundle())), FormFactor.Phone) {
            throw SecurityException("not allowed")
        }

        composeRule.onNodeWithText("Export diagnostic bundle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Could not export diagnostics").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a saved export opens the dialog and Send to phone hands over the bundle and its adb command`() {
        val bundle = bundle()
        showPane(viewModel(sampleSaved(bundle)))

        composeRule.onNodeWithText("Export diagnostic bundle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Diagnostics saved").assertIsDisplayed()
        composeRule.onNodeWithText("Send to phone").performClick()

        assertThat(sent).containsExactly(bundle to "adb pull /sdcard/Download/${bundle.name}")
        assertThat(shared).isEmpty()
        composeRule.onNodeWithText("Diagnostics saved").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Export keeps the focus while the bundle is built`() {
        val gate = CompletableDeferred<Unit>()
        showPane(
            PrivacyViewModel(settings, FakeDiagnosticsExport(sampleSaved(bundle()), gate), audit)
        )
        composeRule.onNodeWithText("Export diagnostic bundle").requestFocus()

        composeRule.onNodeWithText("Export diagnostic bundle").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Creating the diagnostic bundle…").assertExists()
        composeRule.onNodeWithText("Export diagnostic bundle").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `with Reduce motion On the export bar stands still`() {
        showExporting(ReduceMotion.On)
        val before = pane()

        composeRule.mainClock.advanceTimeBy(RUN_MILLIS)

        val moved = pixelsThatMoved(before, pane())
        assertWithMessage("pixels of the pane that moved in $RUN_MILLIS ms")
            .that(moved)
            .isEqualTo(0)
    }

    @Test
    @Config(qualifiers = TV)
    fun `with Reduce motion Off the export bar runs`() {
        showExporting(ReduceMotion.Off)
        val before = pane()

        composeRule.mainClock.advanceTimeBy(RUN_MILLIS)

        val moved = pixelsThatMoved(before, pane())
        assertWithMessage("pixels of the pane that moved in $RUN_MILLIS ms")
            .that(moved)
            .isGreaterThan(0)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Close closes the Saved dialog`() {
        showPane(viewModel(sampleSaved(bundle())))
        composeRule.onNodeWithText("Export diagnostic bundle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Diagnostics saved").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a failed export says so`() {
        showPane(viewModel(ExportResult.Failed))

        composeRule.onNodeWithText("Export diagnostic bundle").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Could not export diagnostics").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Erase asks first, then erases the trail and says it is done`() {
        showPane(viewModel())

        composeRule.onNodeWithText("Erase audit trail").performClick()
        composeRule.waitForIdle()
        assertThat(audit.eraseCalls).isEqualTo(0)
        composeRule.onNodeWithText("Erase the audit trail?").assertIsDisplayed()
        composeRule.onNodeWithText("Erase").performClick()
        composeRule.waitForIdle()

        assertThat(audit.eraseCalls).isEqualTo(1)
        composeRule.onNodeWithText("Erase the audit trail?").assertDoesNotExist()
        composeRule.onNodeWithText("Audit trail erased").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a failed erase says so`() {
        audit.eraseResult = false
        showPane(viewModel())

        composeRule.onNodeWithText("Erase audit trail").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Erase").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Could not erase the audit trail").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Cancel and Back leave the trail alone`() {
        showPane(viewModel())

        composeRule.onNodeWithText("Erase audit trail").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Erase audit trail").performClick()
        composeRule.waitForIdle()
        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(audit.eraseCalls).isEqualTo(0)
        composeRule.onNodeWithText("Erase the audit trail?").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the phone screen has a title and Back`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                PrivacyScreen(
                    onBack = { backs++ },
                    onSendToPhone = { _, _ -> },
                    onOpenAudit = {},
                    formFactor = FormFactor.Phone,
                    viewModel = viewModel(),
                )
            }
        }

        composeRule.onNodeWithText("Privacy and diagnostics").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Back closes a dialog and stays on the phone screen`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone) {
                PrivacyScreen(
                    onBack = { backs++ },
                    onSendToPhone = { _, _ -> },
                    onOpenAudit = {},
                    formFactor = FormFactor.Phone,
                    viewModel = viewModel(),
                )
            }
        }
        composeRule.onNodeWithText("Erase audit trail").performClick()
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Erase the audit trail?").assertDoesNotExist()
        assertThat(backs).isEqualTo(0)
    }

    private companion object {
        const val PANE_WIDTH = 640
        const val SETTLE_MILLIS = 100L

        // A run of Material's indeterminate bar takes 1750 ms; this is nearly half of one.
        const val RUN_MILLIS = 800L
    }
}
