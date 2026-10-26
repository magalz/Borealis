package io.github.magalz.borealis.settings.privacy

import android.text.format.Formatter
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.testing.assertDialogWidth
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The rows and dialogs of the Privacy and diagnostics pane (UC-006, UC-007). */
@RunWith(AndroidJUnit4::class)
class PrivacyContentTest {
    @get:Rule val composeRule = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    private val calls = mutableListOf<String>()
    private val sent = mutableListOf<Pair<File, String>>()

    private val actions =
        PrivacyActions(
            onVerboseLoggingChange = { calls += "verbose=$it" },
            onMaskAddressesChange = { calls += "mask=$it" },
            onExport = { calls += "export" },
            onOpenAudit = { calls += "audit" },
            onEraseRequested = { calls += "erase?" },
            dialogs =
                PrivacyDialogActions(
                    onEraseConfirmed = { calls += "erase" },
                    onEraseCancelled = { calls += "cancel" },
                    onDialogDismissed = { calls += "dismiss" },
                    onSendToPhone = { bundle, command -> sent += bundle to command },
                ),
        )

    private val bundle by lazy { bundleFile(folder.root) }

    private fun saved() =
        PrivacyDialog.Saved(
            location = "/sdcard/Download/${bundle.name}",
            adbPullCommand = "adb pull /sdcard/Download/${bundle.name}",
            bundle = bundle,
            fileName = bundle.name,
            sizeBytes = bundle.length(),
        )

    private fun show(state: PrivacyUiState, formFactor: FormFactor = FormFactor.Phone) {
        composeRule.setContentInKeyboardMode {
            BorealisTheme(formFactor) { PrivacyContent(state, SnackbarHostState(), actions) }
        }
    }

    private fun top(text: String) =
        composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    @Config(qualifiers = PHONE)
    fun `the rows come in order`() {
        show(PrivacyUiState())

        val tops =
            listOf(
                    "Verbose logging",
                    "Mask addresses in logs and diagnostics",
                    "Export diagnostic bundle",
                    "Audit trail",
                    "Erase audit trail",
                )
                .map(::top)
        assertThat(tops).isInOrder()
        assertThat(tops.toSet()).hasSize(5)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the switches show the state`() {
        show(PrivacyUiState(verboseLogging = true, maskAddresses = false))

        composeRule.onNodeWithText("Verbose logging").assertIsOn()
        composeRule.onNodeWithText("Mask addresses in logs and diagnostics").assertIsOff()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap on a switch asks for the opposite value`() {
        show(PrivacyUiState(verboseLogging = false, maskAddresses = true))

        composeRule.onNodeWithText("Verbose logging").performClick()
        composeRule.onNodeWithText("Mask addresses in logs and diagnostics").performClick()

        assertThat(calls).containsExactly("verbose=true", "mask=false").inOrder()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the erase row says it cannot be undone`() {
        show(PrivacyUiState())

        composeRule.onNodeWithText("Cannot be undone").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the rows run their actions`() {
        show(PrivacyUiState())

        composeRule.onNodeWithText("Export diagnostic bundle").performClick()
        composeRule.onNodeWithText("Audit trail").performClick()
        composeRule.onNodeWithText("Erase audit trail").performClick()

        assertThat(calls).containsExactly("export", "audit", "erase?").inOrder()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the export row says what it does on a phone`() {
        show(PrivacyUiState())

        composeRule
            .onNodeWithText("Logs, decoders and the audit trail, already redacted")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the export row says what it does on a TV`() {
        show(PrivacyUiState(), FormFactor.Tv)

        composeRule
            .onNodeWithText("Saves it to Downloads and offers Send to phone")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a running export shows progress in the row and keeps it enabled`() {
        show(PrivacyUiState(exporting = true))

        composeRule.onNodeWithText("Creating the diagnostic bundle…").assertExists()
        composeRule.onNodeWithText("Export diagnostic bundle").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a screen reader meets the progress message once`() {
        show(PrivacyUiState(exporting = true))

        composeRule
            .onAllNodes(
                hasText("Creating the diagnostic bundle…") or
                    hasContentDescription("Creating the diagnostic bundle…")
            )
            .assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `no progress shows while no export runs`() {
        show(PrivacyUiState())

        composeRule.onNodeWithText("Creating the diagnostic bundle…").assertDoesNotExist()
        composeRule.onNodeWithText("Export diagnostic bundle").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the D-pad reaches every row`() {
        show(PrivacyUiState(), FormFactor.Tv)

        composeRule.onNodeWithText("Verbose logging").requestFocus()
        var current = "Verbose logging"
        for (next in
            listOf(
                "Mask addresses in logs and diagnostics",
                "Export diagnostic bundle",
                "Audit trail",
                "Erase audit trail",
            )) {
            composeRule.onNodeWithText(current).performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.onNodeWithText(next).assertIsFocused()
            current = next
        }
    }

    @Test
    @Config(qualifiers = TV)
    fun `OK on a focused row runs it`() {
        show(PrivacyUiState(), FormFactor.Tv)

        composeRule.onNodeWithText("Audit trail").requestFocus()
        composeRule.onNodeWithText("Audit trail").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(calls).containsExactly("audit")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the Saved dialog on a TV shows where the bundle is and how to copy it`() {
        show(PrivacyUiState(dialog = saved()), FormFactor.Tv)

        composeRule.onNodeWithText("Diagnostics saved").assertIsDisplayed()
        val size =
            Formatter.formatShortFileSize(
                ApplicationProvider.getApplicationContext(),
                bundle.length(),
            )
        composeRule.onNodeWithText("${bundle.name} ($size)").assertIsDisplayed()
        composeRule.onNodeWithText("/sdcard/Download/${bundle.name}").assertIsDisplayed()
        composeRule.onNodeWithText("adb pull /sdcard/Download/${bundle.name}").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the Saved dialog on a TV starts with focus on Close`() {
        show(PrivacyUiState(dialog = saved()), FormFactor.Tv)

        composeRule.onNodeWithText("Close").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Send to phone hands over the bundle and leaves the dialog open`() {
        show(PrivacyUiState(dialog = saved()), FormFactor.Tv)

        composeRule.onNodeWithText("Send to phone").performClick()

        assertThat(sent).containsExactly(bundle to "adb pull /sdcard/Download/${bundle.name}")
        assertThat(calls).isEmpty()
        composeRule.onNodeWithText("Diagnostics saved").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the D-pad moves between Send to phone and Close`() {
        show(PrivacyUiState(dialog = saved()), FormFactor.Tv)

        composeRule.onNodeWithText("Close").performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithText("Send to phone").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Close and Back dismiss the Saved dialog`() {
        show(PrivacyUiState(dialog = saved()), FormFactor.Tv)

        composeRule.onNodeWithText("Close").performClick()
        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(calls).containsExactly("dismiss", "dismiss")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Saved dialog on a phone does not offer Send to phone`() {
        show(PrivacyUiState(dialog = saved()))

        composeRule.onNodeWithText("Diagnostics saved").assertIsDisplayed()
        composeRule.onAllNodesWithText("Send to phone").assertCountEquals(0)
        composeRule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `the Saved dialog on a handheld does not offer Send to phone`() {
        show(PrivacyUiState(dialog = saved()), FormFactor.Handheld)

        composeRule.onNodeWithText("Diagnostics saved").assertIsDisplayed()
        composeRule.onNodeWithText("adb pull /sdcard/Download/${bundle.name}").assertIsDisplayed()
        composeRule.onAllNodesWithText("Send to phone").assertCountEquals(0)
        composeRule.onNodeWithText("Close").assertIsFocused()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `the export row says what it does on a handheld as on a phone`() {
        show(PrivacyUiState(), FormFactor.Handheld)

        composeRule
            .onNodeWithText("Logs, decoders and the audit trail, already redacted")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the erase confirmation says what it does and starts with focus on Cancel`() {
        show(PrivacyUiState(dialog = PrivacyDialog.EraseConfirmation), FormFactor.Tv)

        composeRule.onNodeWithText("Erase the audit trail?").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "This deletes every audit entry stored on this device. Diagnostic bundles " +
                    "that you exported before are not changed."
            )
            .assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsFocused()
        composeRule.onNodeWithText("Erase").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Erase confirms and Cancel cancels`() {
        show(PrivacyUiState(dialog = PrivacyDialog.EraseConfirmation), FormFactor.Tv)

        composeRule.onNodeWithText("Erase").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(calls).containsExactly("erase", "cancel").inOrder()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back cancels the erase confirmation`() {
        show(PrivacyUiState(dialog = PrivacyDialog.EraseConfirmation), FormFactor.Tv)

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(calls).containsExactly("cancel")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the D-pad reaches Erase from Cancel`() {
        show(PrivacyUiState(dialog = PrivacyDialog.EraseConfirmation), FormFactor.Tv)

        composeRule.onNodeWithText("Cancel").performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithText("Erase").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the erase confirmation is a compact dialog, 420dp wide`() {
        show(PrivacyUiState(dialog = PrivacyDialog.EraseConfirmation), FormFactor.Tv)

        composeRule.assertDialogWidth("Erase the audit trail?", 420f)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the Saved dialog is a standard dialog, 560dp wide`() {
        show(PrivacyUiState(dialog = saved()), FormFactor.Tv)

        composeRule.assertDialogWidth("Diagnostics saved", 560f)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the Saved dialog starts with focus on Close`() {
        show(PrivacyUiState(dialog = saved()), FormFactor.Tv)

        composeRule.onNodeWithText("Close").assertIsFocused()
    }
}
