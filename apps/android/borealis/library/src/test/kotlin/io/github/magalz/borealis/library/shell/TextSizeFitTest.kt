package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.FitCase
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.testing.cardFitCases
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.library.apps.AppItemUiState
import io.github.magalz.borealis.library.apps.AppsScreen
import io.github.magalz.borealis.library.apps.AppsUiState
import io.github.magalz.borealis.library.hosts.HostsScreen
import io.github.magalz.borealis.library.hosts.withHosts
import io.github.magalz.borealis.library.testing.HANDHELD
import io.github.magalz.borealis.library.testing.PHONE
import io.github.magalz.borealis.library.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Library screens at the largest text: the Text size setting at 130 percent over a system font
 * scale of 1.3, which is 1.69 of the text that the screens are laid out for (ADR 0057, decision
 * 11). Every text must fit where it is: none runs past its width, is cut off at the bottom or ends
 * in an ellipsis, where the screen has the room for it or scrolls. The cards of Hosts and Apps are
 * also at each Card density, in IBM Plex and in Andika (ADR 0058, decision 7).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class TextSizeFitTest(case: FitCase) {
    @get:Rule val composeRule = createComposeRule()

    private val largest =
        BorealisLook(
            textSize = TextSize.Largest,
            font = case.font,
            textSpacing = case.spacing,
            cardDensity = case.density,
        )

    @Test
    @Config(qualifiers = PHONE, fontScale = SYSTEM_SCALE)
    fun `Hosts on a phone fit`() = assertFits(FormFactor.Phone) { Hosts(it) }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Hosts on a TV fit`() = assertFits(FormFactor.Tv) { Hosts(it) }

    @Test
    @Config(qualifiers = HANDHELD, fontScale = SYSTEM_SCALE)
    fun `Hosts on a handheld fit`() = assertFits(FormFactor.Handheld) { Hosts(it) }

    @Test
    @Config(qualifiers = PHONE, fontScale = SYSTEM_SCALE)
    fun `Apps on a phone fit`() = assertFits(FormFactor.Phone) { Apps(it) }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Apps on a TV fit`() = assertFits(FormFactor.Tv) { Apps(it) }

    @Test
    @Config(qualifiers = HANDHELD, fontScale = SYSTEM_SCALE)
    fun `Apps on a handheld fit`() = assertFits(FormFactor.Handheld) { Apps(it) }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `the options of a host on a TV fit`() =
        assertFits(
            FormFactor.Tv,
            keyboard = true,
            before = {
                composeRule.onNodeWithTag("host:living").requestFocus()
                composeRule.onNodeWithTag("host:living").performKeyInput { pressKey(Key.ButtonX) }
                composeRule.waitForIdle()
            },
        ) {
            Hosts(it)
        }

    private fun assertFits(
        formFactor: FormFactor,
        keyboard: Boolean = false,
        before: () -> Unit = {},
        screen: @Composable (FormFactor) -> Unit,
    ) {
        val content: @Composable () -> Unit = {
            LibraryScaffold(
                formFactor,
                selected = RailSelection.Hosts,
                actions = RailActions({}, {}, {}),
                backgroundStyle = BackgroundStyle.Off,
                look = largest,
            ) {
                screen(formFactor)
            }
        }
        if (keyboard) composeRule.setContentInKeyboardMode(content)
        else composeRule.setContent(content)
        composeRule.waitForIdle()
        before()

        composeRule.assertNoClippedText()
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
    private fun Apps(formFactor: FormFactor) {
        val names = listOf("Desktop", "Steam Big Picture", "Playnite", "Hades II", "Elden Ring")
        AppsScreen(
            state =
                AppsUiState(
                    loading = false,
                    hostName = "Desk PC",
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

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<FitCase> = cardFitCases()

        private const val SYSTEM_SCALE = 1.3f
    }
}
