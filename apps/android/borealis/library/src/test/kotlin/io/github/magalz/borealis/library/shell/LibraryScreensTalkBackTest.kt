package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.headings
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.addhost.AddHostError
import io.github.magalz.borealis.library.addhost.AddHostScreen
import io.github.magalz.borealis.library.addhost.AddHostUiState
import io.github.magalz.borealis.library.apps.AppItemUiState
import io.github.magalz.borealis.library.apps.AppsScreen
import io.github.magalz.borealis.library.apps.AppsUiState
import io.github.magalz.borealis.library.pairing.PairingFailure
import io.github.magalz.borealis.library.pairing.PairingScreen
import io.github.magalz.borealis.library.pairing.PairingUiState
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader is told on Apps, Add host and Pairing (WCAG 1.3.1, 2.4.6, 4.1.2 and 4.1.3):
 * the title is a heading, a step is one thing to read and not a number and a sentence, and what
 * went wrong is announced when it appears.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScreensTalkBackTest {
    @get:Rule val composeRule = createComposeRule()

    private val waiting =
        PairingUiState.Waiting("4817", "https://192.168.1.40:47990", "Galaxy S22", false)

    @Test
    @Config(qualifiers = PHONE)
    fun `the host name over the apps is a heading`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Apps(it) }

        assertThat(composeRule.headings()).containsExactly("Desk PC")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Add host has its title as a heading on a phone`() {
        show(FormFactor.Phone, RailSelection.AddHost) { AddHost(it) }

        assertThat(composeRule.headings()).containsExactly("Add host")
    }

    @Test
    @Config(qualifiers = TV)
    fun `Add host has its title as a heading on a TV`() {
        show(FormFactor.Tv, RailSelection.AddHost) { AddHost(it) }

        assertThat(composeRule.headings()).containsExactly("Add host")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the error under the address is announced when it appears`() {
        show(FormFactor.Phone, RailSelection.AddHost) {
            AddHost(it, AddHostUiState("abc", error = AddHostError.INVALID))
        }

        assertThat(liveRegionOf(composeRule.onNodeWithText(INVALID, substring = true)))
            .isEqualTo(LiveRegionMode.Polite)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the address says its error before it is edited`() {
        show(FormFactor.Tv, RailSelection.AddHost) {
            AddHost(it, AddHostUiState("abc", error = AddHostError.INVALID))
        }

        composeRule.onNodeWithContentDescription("Address, abc", substring = true).assertExists()
        composeRule.onNodeWithContentDescription(INVALID, substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Pairing has its title as a heading`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Pairing(it, waiting) }

        assertThat(composeRule.headings()).containsExactly("Pair with Studio")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a step of the pairing is read with its number`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Pairing(it, waiting) }

        assertThat(textsOf("Go to the Clients page."))
            .containsExactly("2", "Go to the Clients page.")
            .inOrder()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the step with an address is read with the address`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Pairing(it, waiting) }

        assertThat(textsOf("Open the LuminalShine Web UI at this address."))
            .containsExactly(
                "1",
                "Open the LuminalShine Web UI at this address.",
                "https://192.168.1.40:47990",
            )
            .inOrder()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a PIN that was wrong is announced when the pairing fails`() {
        show(FormFactor.Phone, RailSelection.Hosts) {
            Pairing(it, PairingUiState.Failed(PairingFailure.PIN_WRONG, null))
        }

        assertThat(liveRegionOf(composeRule.onNodeWithText("The PIN was wrong.")))
            .isEqualTo(LiveRegionMode.Polite)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a PIN that expired is announced`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Pairing(it, PairingUiState.Expired) }

        assertThat(liveRegionOf(composeRule.onNodeWithText("The PIN expired.")))
            .isEqualTo(LiveRegionMode.Polite)
    }

    private fun liveRegionOf(node: SemanticsNodeInteraction): LiveRegionMode? =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion)

    /** The texts that the node holding [text] reads out, which are more when it is merged. */
    private fun textsOf(text: String): List<String> =
        composeRule.onNodeWithText(text).fetchSemanticsNode().config[SemanticsProperties.Text].map {
            it.text
        }

    private fun show(
        formFactor: FormFactor,
        selected: RailSelection,
        screen: @Composable (FormFactor) -> Unit,
    ) {
        val content: @Composable () -> Unit = {
            LibraryScaffold(
                formFactor,
                selected,
                RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
            ) {
                screen(formFactor)
            }
        }
        if (formFactor == FormFactor.Phone) composeRule.setContent(content)
        else composeRule.setContentInKeyboardMode(content)
        composeRule.waitForIdle()
    }

    @Composable
    private fun Apps(formFactor: FormFactor) {
        AppsScreen(
            state =
                AppsUiState(
                    loading = false,
                    hostName = "Desk PC",
                    runningAppId = 1,
                    runningAppName = "Desktop",
                    items = listOf(AppItemUiState(1, "Desktop", true, false, false)),
                ),
            formFactor = formFactor,
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onAppSelected = {},
            onActions = {},
            onActionChosen = {},
            onActionsDismissed = {},
            onDetailsDismissed = {},
            onToggleShowHidden = {},
        )
    }

    @Composable
    private fun AddHost(formFactor: FormFactor, state: AddHostUiState = AddHostUiState()) {
        AddHostScreen(state, formFactor, onAddressChanged = {}, onSubmit = {}, onCancel = {})
    }

    @Composable
    private fun Pairing(formFactor: FormFactor, state: PairingUiState) {
        PairingScreen(state, formFactor, onGetNewPin = {}, onCancel = {}, hostName = "Studio")
    }

    private companion object {
        const val INVALID = "Unable to resolve this address"
    }
}
