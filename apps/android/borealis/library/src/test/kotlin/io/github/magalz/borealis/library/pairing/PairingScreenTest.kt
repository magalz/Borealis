package io.github.magalz.borealis.library.pairing

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Pairing screen (UC-001): what each state shows, and what the buttons call. */
@RunWith(AndroidJUnit4::class)
class PairingScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private var newPinRequests = 0
    private var cancels = 0

    private fun show(
        state: PairingUiState,
        formFactor: FormFactor = FormFactor.Phone,
        hostName: String? = "Studio",
    ) {
        composeRule.setContent {
            PairingScreen(
                state = state,
                formFactor = formFactor,
                onGetNewPin = { newPinRequests++ },
                onCancel = { cancels++ },
                hostName = hostName,
            )
        }
    }

    private fun waiting(webUiUrl: String? = WEB_UI, restarted: Boolean = false) =
        PairingUiState.Waiting("4817", webUiUrl, "Galaxy S22", restarted)

    @Test
    @Config(qualifiers = PHONE)
    fun `waiting shows the four digits of the PIN`() {
        show(waiting())

        composeRule.onNodeWithContentDescription("4, 8, 1, 7").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `waiting shows the Web UI address of the host`() {
        show(waiting())

        composeRule.onNodeWithText(WEB_UI).assertIsDisplayed()
        composeRule.onNodeWithText("Open the LuminalShine Web UI at this address.").assertExists()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `waiting without a Web UI address says on your PC`() {
        show(waiting(webUiUrl = null))

        composeRule.onNodeWithText("Open the LuminalShine Web UI on your PC.").assertExists()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `waiting tells the user to go to the Clients page and name the device`() {
        show(waiting())

        composeRule.onNodeWithText("Go to the Clients page.").assertExists()
        composeRule
            .onNodeWithText("Enter this PIN and a device name, for example “Galaxy S22”.")
            .assertExists()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `waiting tells the user to avoid xbox and webos in the device name`() {
        show(waiting())

        composeRule
            .onNodeWithText(
                "Avoid “xbox” or “webos” in the name. The host treats those devices differently."
            )
            .assertExists()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `waiting says the PIN stays valid for 5 minutes`() {
        show(waiting())

        composeRule.onNodeWithText("The PIN stays valid for 5 minutes.").assertExists()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the title names the host`() {
        show(waiting(), hostName = "Studio")

        composeRule.onNodeWithText("Pair with Studio").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the title says this PC when the host name is not known`() {
        show(waiting(), hostName = null)

        composeRule.onNodeWithText("Pair with this PC").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the note about a restart shows with the new PIN`() {
        show(waiting(restarted = true))

        composeRule
            .onNodeWithText("The PC cleared an unfinished pairing. Use this new PIN.")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("4, 8, 1, 7").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the note about a restart does not show for the first PIN`() {
        show(waiting(restarted = false))

        composeRule
            .onNodeWithText("The PC cleared an unfinished pairing. Use this new PIN.")
            .assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Cancel while waiting cancels and nothing else`() {
        show(waiting())

        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(cancels).isEqualTo(1)
        assertThat(newPinRequests).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the back arrow of a phone cancels`() {
        show(waiting())

        composeRule.onNodeWithContentDescription("Cancel pairing").performClick()

        assertThat(cancels).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV Cancel has focus and Back cancels`() {
        show(waiting(), FormFactor.Tv)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel").assertIsFocused()
        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(cancels).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the PIN, the steps and Cancel show together`() {
        show(waiting(), FormFactor.Tv)

        composeRule.onNodeWithContentDescription("4, 8, 1, 7").assertIsDisplayed()
        composeRule.onNodeWithText("Go to the Clients page.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `preparing shows the aurora loader`() {
        show(PairingUiState.Preparing)

        composeRule.onNodeWithContentDescription("Preparing the PIN").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Preparing the PIN")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `paired says Paired`() {
        show(PairingUiState.Paired)

        composeRule.onNodeWithText("Paired").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `expired offers a new PIN`() {
        show(PairingUiState.Expired)

        composeRule.onNodeWithText("The PIN expired.").assertIsDisplayed()
        composeRule.onNodeWithText("Get a new PIN").performClick()

        assertThat(newPinRequests).isEqualTo(1)
        assertThat(cancels).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `paired has no back arrow to cancel with`() {
        show(PairingUiState.Paired)

        composeRule.onNodeWithContentDescription("Cancel pairing").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `expired can be cancelled`() {
        show(PairingUiState.Expired)

        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(cancels).isEqualTo(1)
        assertThat(newPinRequests).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a wrong PIN says so and offers Try again`() {
        show(PairingUiState.Failed(PairingFailure.PIN_WRONG, null))

        composeRule.onNodeWithText("The PIN was wrong.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertThat(newPinRequests).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a pairing already in progress says so`() {
        show(PairingUiState.Failed(PairingFailure.ALREADY_IN_PROGRESS, null))

        composeRule
            .onNodeWithText("Another pairing is already in progress on the PC.")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an offline host says so`() {
        show(PairingUiState.Failed(PairingFailure.HOST_OFFLINE, null))

        composeRule.onNodeWithText("The PC went offline.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `another failure shows the message of the adapter`() {
        show(PairingUiState.Failed(PairingFailure.OTHER, "The host cleared the attempt."))

        composeRule.onNodeWithText("The host cleared the attempt.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `another failure without a message says Pairing failed`() {
        show(PairingUiState.Failed(PairingFailure.OTHER, null))

        composeRule.onNodeWithText("Pairing failed.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a failure can be cancelled`() {
        show(PairingUiState.Failed(PairingFailure.PIN_WRONG, null))

        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(cancels).isEqualTo(1)
        assertThat(newPinRequests).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an unknown host says so and closes`() {
        show(PairingUiState.HostUnknown)

        composeRule.onNodeWithText("This PC is no longer in your list.").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()

        assertThat(cancels).isEqualTo(1)
    }

    private fun DpRect.centreX() = (left.value + right.value) / 2

    private fun DpRect.centreY() = (top.value + bottom.value) / 2

    private fun bounds(description: String) =
        composeRule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the PIN is centred on the screen with its line right below it`() {
        show(waiting())

        val pin = bounds("4, 8, 1, 7")
        val line = composeRule.onNodeWithText("The PIN stays valid for 5 minutes.")
        val screen = composeRule.onRoot().getUnclippedBoundsInRoot()

        assertThat(pin.centreX()).isWithin(1f).of(screen.centreX())
        val lineBounds = line.getUnclippedBoundsInRoot()
        assertThat(lineBounds.centreX()).isWithin(1f).of(pin.centreX())
        assertThat(lineBounds.top.value - pin.bottom.value).isAtLeast(0f)
        assertThat(lineBounds.top.value - pin.bottom.value).isAtMost(24f)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the line under the PIN is centred on it`() {
        show(waiting(), FormFactor.Tv)

        val pin = bounds("4, 8, 1, 7")
        val line =
            composeRule
                .onNodeWithText("The PIN stays valid for 5 minutes.")
                .getUnclippedBoundsInRoot()

        assertThat(line.centreX()).isWithin(1f).of(pin.centreX())
        assertThat(line.top.value - pin.bottom.value).isAtLeast(0f)
        assertThat(line.top.value - pin.bottom.value).isAtMost(24f)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `paired is in the middle of the screen with the check above it`() {
        show(PairingUiState.Paired)

        val screen = composeRule.onRoot().getUnclippedBoundsInRoot()
        val paired = composeRule.onNodeWithTag("paired").getUnclippedBoundsInRoot()
        val text = composeRule.onNodeWithText("Paired").getUnclippedBoundsInRoot()
        val check = composeRule.onNodeWithTag("paired-check").getUnclippedBoundsInRoot()

        assertThat(paired.centreX()).isWithin(1f).of(screen.centreX())
        assertThat(paired.centreY()).isWithin(1f).of(screen.centreY())
        assertThat(text.centreX()).isWithin(1f).of(screen.centreX())
        assertThat(check.centreX()).isWithin(1f).of(screen.centreX())
        assertThat(check.bottom.value).isAtMost(text.top.value)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `paired is announced politely when it appears`() {
        show(PairingUiState.Paired)

        composeRule
            .onNodeWithTag("paired")
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld the PIN cells are not squeezed by the steps`() {
        composeRule.setContent {
            LibraryScaffold(
                FormFactor.Handheld,
                RailSelection.Hosts,
                RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
            ) {
                PairingScreen(
                    state = waiting(),
                    formFactor = FormFactor.Handheld,
                    onGetNewPin = {},
                    onCancel = {},
                    hostName = "Studio",
                )
            }
        }

        val pin = bounds("4, 8, 1, 7")

        // Four cells of 56dp with three gaps of 10dp.
        assertThat(pin.right.value - pin.left.value).isAtLeast(PIN_WIDTH_DP)
    }

    @Test
    @Config(qualifiers = TV)
    fun `paired is in the middle of a TV screen too`() {
        show(PairingUiState.Paired, FormFactor.Tv)

        val screen = composeRule.onRoot().getUnclippedBoundsInRoot()
        val paired = composeRule.onNodeWithTag("paired").getUnclippedBoundsInRoot()

        assertThat(paired.centreX()).isWithin(1f).of(screen.centreX())
        assertThat(paired.centreY()).isWithin(1f).of(screen.centreY())
    }

    private companion object {
        const val WEB_UI = "https://192.0.2.10:47990"
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        const val HANDHELD = "w800dp-h480dp-land-hdpi"
        const val PIN_WIDTH_DP = 254f
    }
}
