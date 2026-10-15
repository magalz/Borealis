package io.github.magalz.borealis.library.apps

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Apps screen and its app actions sheet (UC-003, ADR 0052 D6). */
@RunWith(AndroidJUnit4::class)
class AppsScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val selected = mutableListOf<Int>()
    private val optionsFor = mutableListOf<Int>()
    private val chosen = mutableListOf<AppAction>()
    private var actionsDismissed = 0
    private var detailsDismissed = 0
    private var toggles = 0
    private var backs = 0

    private val desktop = item(1, "Desktop", running = true)
    private val steam = item(2, "Steam Big Picture")
    private val hades = item(3, "Hades II", hdrSupported = true)
    private val secret = item(4, "Secret", hidden = true)

    private fun item(
        id: Int,
        name: String,
        running: Boolean = false,
        hidden: Boolean = false,
        hdrSupported: Boolean = false,
    ) = AppItemUiState(id, name, running, hidden, hdrSupported)

    private fun state(
        vararg items: AppItemUiState,
        loading: Boolean = false,
        showHidden: Boolean = false,
        actions: AppActionsUiState? = null,
        details: AppItemUiState? = null,
    ) =
        AppsUiState(
            loading = loading,
            hostName = "Desk PC",
            runningAppId = items.firstOrNull { it.running }?.id,
            showHidden = showHidden,
            items = items.toList(),
            actions = actions,
            details = details,
        )

    private fun show(state: AppsUiState, formFactor: FormFactor = FormFactor.Phone) {
        composeRule.setContent {
            AppsScreen(
                state = state,
                formFactor = formFactor,
                snackbarHostState = SnackbarHostState(),
                onBack = { backs++ },
                onAppSelected = { selected += it },
                onActions = { optionsFor += it },
                onActionChosen = { chosen += it },
                onActionsDismissed = { actionsDismissed++ },
                onDetailsDismissed = { detailsDismissed++ },
                onToggleShowHidden = { toggles++ },
            )
        }
        composeRule.waitForIdle()
    }

    private fun tile(name: String): SemanticsNodeInteraction =
        composeRule.onNodeWithContentDescription(name)

    private fun sheetFor(app: AppItemUiState, running: String?, vararg choices: AppAction) =
        AppActionsUiState(app, running, choices.toList())

    @Test
    @Config(qualifiers = PHONE)
    fun `the host name is the title`() {
        show(state(desktop, steam))

        composeRule.onNodeWithText("Desk PC").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the running app comes first with the Running badge`() {
        show(state(desktop, steam, hades))

        val first = tile("Desktop").fetchSemanticsNode().boundsInRoot
        for (other in listOf("Steam Big Picture", "Hades II")) {
            val bounds = tile(other).fetchSemanticsNode().boundsInRoot
            assertThat(bounds.top).isAtLeast(first.top)
            if (bounds.top == first.top) assertThat(bounds.left).isGreaterThan(first.left)
        }
        tile("Desktop").assert(hasStateDescription("Running"))
        tile("Steam Big Picture").assert(SemanticsMatcher.keyNotDefined(STATE))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a hidden app is announced as hidden`() {
        show(state(desktop, secret, showHidden = true))

        tile("Secret").assert(hasStateDescription("Hidden"))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the grid has three columns on a phone`() {
        show(state(desktop, steam, hades, secret))

        val tops = listOf("Desktop", "Steam Big Picture", "Hades II", "Secret").map {
            tile(it).fetchSemanticsNode().boundsInRoot.top
        }
        assertThat(tops.take(3).toSet()).hasSize(1)
        assertThat(tops[3]).isGreaterThan(tops[0])
    }

    @Test
    @Config(qualifiers = TV)
    fun `the grid has five columns on a TV`() {
        val names = (1..6).map { item(it, "App $it") }
        show(state(*names.toTypedArray()), FormFactor.Tv)

        val tops = names.map { tile(it.name).fetchSemanticsNode().boundsInRoot.top }
        assertThat(tops.take(5).toSet()).hasSize(1)
        assertThat(tops[5]).isGreaterThan(tops[0])
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `the grid has four columns on a handheld`() {
        val names = (1..5).map { item(it, "App $it") }
        show(state(*names.toTypedArray()), FormFactor.Handheld)

        val tops = names.map { tile(it.name).fetchSemanticsNode().boundsInRoot.top }
        assertThat(tops.take(4).toSet()).hasSize(1)
        assertThat(tops[4]).isGreaterThan(tops[0])
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap selects the app`() {
        show(state(desktop, steam))

        tile("Steam Big Picture").performClick()

        assertThat(selected).containsExactly(2)
        assertThat(optionsFor).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a long press opens the actions and does not select the app`() {
        show(state(desktop, steam))

        tile("Steam Big Picture").performTouchInput { longClick() }

        assertThat(optionsFor).containsExactly(2)
        assertThat(selected).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `X on a gamepad opens the actions`() {
        show(state(desktop, steam), FormFactor.Tv)

        tile("Steam Big Picture").requestFocus()
        tile("Steam Big Picture").performKeyInput { pressKey(Key.ButtonX) }

        assertThat(optionsFor).containsExactly(2)
        assertThat(selected).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the first tile has focus`() {
        show(state(desktop, steam, hades), FormFactor.Tv)

        tile("Desktop").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the first tile has focus once the apps arrive`() {
        val loaded = mutableStateOf(state(loading = true))
        composeRule.setContent {
            AppsScreen(
                state = loaded.value,
                formFactor = FormFactor.Tv,
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

        loaded.value = state(desktop, steam)
        composeRule.waitForIdle()

        tile("Desktop").assertIsFocused()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the toggle on a phone shows the hidden apps`() {
        show(state(desktop))

        composeRule.onNodeWithContentDescription("Show hidden apps").performClick()

        assertThat(toggles).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the toggle on a phone offers to hide them again once they show`() {
        show(state(desktop, secret, showHidden = true))

        composeRule.onNodeWithContentDescription("Hide hidden apps").performClick()

        assertThat(toggles).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the toggle on a TV is a button that shows the hidden apps`() {
        show(state(desktop), FormFactor.Tv)

        composeRule.onNodeWithText("Show hidden apps").performClick()

        assertThat(toggles).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the toggle on a TV offers to hide them again once they show`() {
        show(state(desktop, secret, showHidden = true), FormFactor.Tv)

        composeRule.onNodeWithText("Hide hidden apps").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the back arrow of a phone goes back`() {
        show(state(desktop))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a progress indicator shows while the apps load`() {
        show(state(loading = true))

        composeRule.onNodeWithContentDescription("Loading apps").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `no progress indicator shows once the apps are there`() {
        show(state(desktop))

        composeRule.onNodeWithContentDescription("Loading apps").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a host without apps says so`() {
        show(state())

        composeRule.onNodeWithText("No apps to show").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the details dialog shows the app id and HDR support`() {
        show(state(desktop, hades, details = item(42, "Hades II", hdrSupported = true)))

        composeRule.onNodeWithText("App ID").assertIsDisplayed()
        composeRule.onNodeWithText("42").assertIsDisplayed()
        composeRule.onNodeWithText("Supported").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the details dialog says when HDR is not supported`() {
        show(state(desktop, details = item(42, "Desktop")))

        composeRule.onNodeWithText("Not supported").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Close dismisses the details dialog`() {
        show(state(desktop, details = item(42, "Desktop")))

        composeRule.onNodeWithText("Close").performClick()

        assertThat(detailsDismissed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Back dismisses the details dialog`() {
        show(state(desktop, details = item(42, "Desktop")))

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(detailsDismissed).isEqualTo(1)
        assertThat(backs).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the sheet of a running app is titled with the app and shows each consequence`() {
        show(
            state(
                desktop,
                steam,
                actions =
                    sheetFor(desktop, "Desktop", AppAction.RESUME, AppAction.QUIT, AppAction.DETAILS),
            ),
            FormFactor.Tv,
        )

        composeRule.onNodeWithText("Resume").assertIsDisplayed()
        composeRule.onNodeWithText("Go back to Desktop where you left it.").assertIsDisplayed()
        composeRule.onNodeWithText("Quit Desktop").assertIsDisplayed()
        composeRule
            .onNodeWithText("Ends Desktop on the PC. Unsaved work is lost.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Details").assertIsDisplayed()
        composeRule.onNodeWithText("App ID and HDR support.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the sheet names the running app that a start would quit`() {
        show(
            state(
                desktop,
                hades,
                actions =
                    sheetFor(
                        hades,
                        "Desktop",
                        AppAction.QUIT_AND_START,
                        AppAction.DETAILS,
                        AppAction.HIDE,
                    ),
            ),
            FormFactor.Tv,
        )

        composeRule.onNodeWithText("Quit Desktop and start Hades II").assertIsDisplayed()
        composeRule
            .onNodeWithText("Ends Desktop on the PC first. Unsaved work in it is lost.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Hide from this list").assertIsDisplayed()
        composeRule
            .onNodeWithText("You can show it again with Show hidden apps.")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the sheet of an app when nothing runs offers Start`() {
        show(
            state(
                hades,
                actions = sheetFor(hades, null, AppAction.START, AppAction.DETAILS, AppAction.HIDE),
            ),
            FormFactor.Tv,
        )

        composeRule.onNodeWithText("Start").assertIsDisplayed()
        composeRule.onNodeWithText("Start Hades II on the PC.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the sheet of a hidden app offers to show it`() {
        show(
            state(
                secret,
                showHidden = true,
                actions = sheetFor(secret, null, AppAction.START, AppAction.UNHIDE),
            ),
            FormFactor.Tv,
        )

        composeRule.onNodeWithText("Show in this list").assertExists()
    }

    @Test
    @Config(qualifiers = TV)
    fun `choosing an action that loses nothing reports it at once`() {
        show(
            state(
                desktop,
                actions = sheetFor(desktop, "Desktop", AppAction.RESUME, AppAction.QUIT),
            ),
            FormFactor.Tv,
        )

        composeRule.onNodeWithText("Resume").performClick()

        assertThat(chosen).containsExactly(AppAction.RESUME)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Quit asks for confirmation before it is reported`() {
        show(
            state(
                desktop,
                actions = sheetFor(desktop, "Desktop", AppAction.RESUME, AppAction.QUIT),
            ),
            FormFactor.Tv,
        )

        composeRule.onNodeWithText("Quit Desktop").performClick()

        composeRule.onNodeWithText("Quit Desktop? Unsaved work is lost.").assertIsDisplayed()
        assertThat(chosen).isEmpty()

        composeRule.onNodeWithText("Quit").performClick()

        assertThat(chosen).containsExactly(AppAction.QUIT)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Quit and start asks for confirmation naming the running app`() {
        show(
            state(
                desktop,
                hades,
                actions = sheetFor(hades, "Desktop", AppAction.QUIT_AND_START, AppAction.DETAILS),
            ),
            FormFactor.Tv,
        )

        composeRule.onNodeWithText("Quit Desktop and start Hades II").performClick()

        composeRule.onNodeWithText("Quit Desktop? Unsaved work is lost.").assertIsDisplayed()
        assertThat(chosen).isEmpty()

        composeRule.onNodeWithText("Quit").performClick()

        assertThat(chosen).containsExactly(AppAction.QUIT_AND_START)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Cancel in the confirmation reports nothing and leaves the sheet open`() {
        show(
            state(
                desktop,
                actions = sheetFor(desktop, "Desktop", AppAction.RESUME, AppAction.QUIT),
            ),
            FormFactor.Tv,
        )
        composeRule.onNodeWithText("Quit Desktop").performClick()

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Quit Desktop? Unsaved work is lost.").assertDoesNotExist()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()
        assertThat(chosen).isEmpty()
        assertThat(actionsDismissed).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back closes the confirmation before the sheet`() {
        show(
            state(
                desktop,
                actions = sheetFor(desktop, "Desktop", AppAction.RESUME, AppAction.QUIT),
            ),
            FormFactor.Tv,
        )
        composeRule.onNodeWithText("Quit Desktop").performClick()

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Quit Desktop? Unsaved work is lost.").assertDoesNotExist()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()
        assertThat(actionsDismissed).isEqualTo(0)
        assertThat(chosen).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back closes the sheet and does not leave the screen`() {
        show(
            state(
                desktop,
                actions = sheetFor(desktop, "Desktop", AppAction.RESUME, AppAction.QUIT),
            ),
            FormFactor.Tv,
        )

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(actionsDismissed).isEqualTo(1)
        assertThat(backs).isEqualTo(0)
        assertThat(chosen).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Back closes the bottom sheet on a phone`() {
        show(
            state(
                desktop,
                actions = sheetFor(desktop, "Desktop", AppAction.RESUME, AppAction.QUIT),
            )
        )

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(actionsDismissed).isEqualTo(1)
    }

    private fun hasStateDescription(text: String) =
        SemanticsMatcher.expectValue(STATE, text)

    private companion object {
        val STATE = SemanticsProperties.StateDescription
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        const val HANDHELD = "w800dp-h480dp-land-hdpi"
    }
}
