package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.FitCase
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.testing.fitCases
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.library.addhost.AddHostError
import io.github.magalz.borealis.library.addhost.AddHostScreen
import io.github.magalz.borealis.library.addhost.AddHostUiState
import io.github.magalz.borealis.library.apps.AppItemUiState
import io.github.magalz.borealis.library.apps.AppsScreen
import io.github.magalz.borealis.library.apps.AppsUiState
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.pairing.PairingScreen
import io.github.magalz.borealis.library.pairing.PairingUiState
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Library screens at a system font scale of 2.0, the largest that Android offers (WCAG 1.4.4:
 * text resizes to 200 percent without loss of content). Every text must fit where it is, or the
 * screen scrolls: none runs past its width, is cut off at the bottom or ends in an ellipsis. The
 * Text spacing of the runs is Normal, Wide or Wider, and the look draws the level that the font
 * scale lets it (ADR 0058, decision 4), so none fails. The Apps screen of a host with a long name
 * also fits at the Text size Largest over the system font of 2.0, a combined font scale of 2.6, at
 * Normal.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class LargeTextFitTest(private val case: FitCase) {
    @get:Rule val composeRule = createComposeRule()

    private val look = BorealisLook(font = case.font, textSpacing = case.spacing)
    private val largest = look.copy(textSize = TextSize.Largest)

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `Hosts on a phone fit`() = assertFits(FormFactor.Phone) { Hosts(it) }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `Hosts on a TV fit`() = assertFits(FormFactor.Tv) { Hosts(it) }

    @Test
    @Config(qualifiers = HANDHELD, fontScale = SCALE)
    fun `Hosts on a handheld fit`() = assertFits(FormFactor.Handheld) { Hosts(it) }

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `Apps on a phone fit`() = assertFits(FormFactor.Phone) { Apps(it) }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `Apps on a TV fit`() = assertFits(FormFactor.Tv) { Apps(it) }

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `Apps on a phone fit with a long host name`() =
        assertFits(FormFactor.Phone) { Apps(it, LONG_HOST_NAME) }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `Apps on a TV fit with a long host name`() =
        assertFits(FormFactor.Tv) { Apps(it, LONG_HOST_NAME) }

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `Apps on a phone fit with a long host name at the largest text size`() {
        assumeNormalSpacing()
        assertFits(FormFactor.Phone, look = largest) { Apps(it, LONG_HOST_NAME) }
    }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `Apps on a TV fit with a long host name at the largest text size`() {
        assumeNormalSpacing()
        assertFits(FormFactor.Tv, look = largest) { Apps(it, LONG_HOST_NAME) }
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `Add host on a phone fits`() =
        assertFits(FormFactor.Phone, RailSelection.AddHost) {
            AddHost(it, AddHostUiState("abc", error = AddHostError.UNREACHABLE))
        }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `Add host on a TV fits`() =
        assertFits(FormFactor.Tv, RailSelection.AddHost) {
            AddHost(it, AddHostUiState("abc", error = AddHostError.UNREACHABLE))
        }

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `Pairing on a phone fits`() = assertFits(FormFactor.Phone) { Pairing(it) }

    @Test
    @Config(qualifiers = TV, fontScale = SCALE)
    fun `Pairing on a TV fits`() = assertFits(FormFactor.Tv) { Pairing(it) }

    @Test
    @Config(qualifiers = PHONE, fontScale = SCALE)
    fun `the options of a host on a phone fit`() {
        show(FormFactor.Phone, RailSelection.Hosts) { Hosts(it) }
        // A font that is larger than IBM Plex pushes the second host below the fold of the list.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("host:living"))
        composeRule.onNodeWithTag("host:living").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.assertNoClippedText()
    }

    /**
     * The combined font scale of 2.6 is held at Normal, the spacing that was measured there (Wider
     * is drawn as Wide from 2.0, and Wide above a combined 2.0 has not been measured).
     */
    private fun assumeNormalSpacing() = assumeTrue(case.spacing == TextSpacing.Normal)

    private fun assertFits(
        formFactor: FormFactor,
        selected: RailSelection = RailSelection.Hosts,
        look: BorealisLook = this.look,
        screen: @Composable (FormFactor) -> Unit,
    ) {
        show(formFactor, selected, look, screen)

        composeRule.assertNoClippedText()
    }

    private fun show(
        formFactor: FormFactor,
        selected: RailSelection,
        look: BorealisLook = this.look,
        screen: @Composable (FormFactor) -> Unit,
    ) {
        composeRule.setContent {
            LibraryScaffold(
                formFactor,
                selected,
                RailActions({}, {}, {}),
                look = look,
                backgroundStyle = BackgroundStyle.Off,
            ) {
                screen(formFactor)
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Hosts(formFactor: FormFactor) {
        HostsScreen(
            state = withHosts,
            formFactor = formFactor,
            snackbarHostState = SnackbarHostState(),
            onHostSelected = {},
            onContinue = {},
            onWake = {},
            onForget = {},
            onDetails = {},
            onDetailsDismissed = {},
            onAddHost = {},
            onSettings = {},
        )
    }

    @Composable
    private fun Apps(formFactor: FormFactor, hostName: String = "Desk PC") {
        val names = listOf("Desktop", "Steam Big Picture", "Playnite", "Hades II", "Elden Ring")
        AppsScreen(
            state =
                AppsUiState(
                    loading = false,
                    hostName = hostName,
                    runningAppId = 1,
                    runningAppName = "Desktop",
                    items =
                        names.mapIndexed { index, name ->
                            AppItemUiState(
                                id = index + 1,
                                name = name,
                                running = index == 0,
                                hidden = false,
                                hdrSupported = index % 2 == 0,
                            )
                        },
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
    private fun AddHost(formFactor: FormFactor, state: AddHostUiState) {
        AddHostScreen(state, formFactor, onAddressChanged = {}, onSubmit = {}, onCancel = {})
    }

    @Composable
    private fun Pairing(formFactor: FormFactor) {
        PairingScreen(
            PairingUiState.Waiting("4817", "https://192.168.1.40:47990", "Galaxy S22", true),
            formFactor,
            onGetNewPin = {},
            onCancel = {},
            hostName = "Studio",
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<FitCase> = fitCases()

        private const val SCALE = 2f
        private const val LONG_HOST_NAME =
            "Living Room Gaming PC in the Basement Next to the Router"
    }
}
