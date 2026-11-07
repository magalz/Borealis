package io.github.magalz.borealis.library.apps

import android.view.View
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.testing.TV
import io.github.magalz.borealis.library.testing.resetWindowFocus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Where focus goes on a TV when the windows over the Apps screen close: the app has its card back
 * (Sprint 14 UAT, U11). The opener is the second app, so the first tile, where Compose puts the
 * focus after a window took it, cannot make a test pass by itself.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class AppActionsSheetFocusTest {
    @get:Rule val composeRule = createComposeRule()

    private val desktop = AppItemUiState(1, "Desktop", true, false, false)
    private val steam = AppItemUiState(2, "Steam Big Picture", false, false, false)
    private val hades = AppItemUiState(3, "Hades II", false, false, true)
    private val state =
        mutableStateOf(
            AppsUiState(
                loading = false,
                hostName = "Desk PC",
                runningAppId = desktop.id,
                runningAppName = desktop.name,
                items = listOf(desktop, steam, hades),
            )
        )
    private val chosen = mutableListOf<AppAction>()
    /**
     * True when the sheets of the apps that no tile asked for offer Hide, as the ViewModel does.
     */
    private var offerHide = false
    /** True when the app leaves the list in the callback of Hide, false when a test removes it. */
    private var removeOnHide = false
    private var window: View? = null

    @Test
    fun `Back from the actions gives the focus back to the app that opened them`() {
        show()
        tile(steam).requestFocus()
        openActions(steam)
        composeRule.resetWindowFocus(window)

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(QUIT_AND_START).assertDoesNotExist()
        tile(steam).assertIsFocused()
    }

    @Test
    fun `Back from the actions that OK opened gives the focus back to the app`() {
        show()
        tile(steam).requestFocus()
        // Desktop runs and Steam does not: a tap or OK is a choice, so it opens the actions.
        tile(steam).performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(QUIT_AND_START).assertIsDisplayed()
        composeRule.resetWindowFocus(window)

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(QUIT_AND_START).assertDoesNotExist()
        tile(steam).assertIsFocused()
    }

    @Test
    fun `actions that no tile asked for give the focus back to their app`() {
        show()
        tile(hades).requestFocus()
        // A launch request opens the actions of an app: no tile callback runs. The window keeps
        // the focus of the screen here, so what is focused after Back is the opener or Hades.
        composeRule.runOnUiThread { state.value = state.value.copy(actions = sheetFor(desktop)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Resume").assertDoesNotExist()
        tile(desktop).assertIsFocused()
    }

    @Test
    fun `an action that keeps the screen gives the focus back to the app that opened the actions`() {
        show()
        tile(steam).requestFocus()
        openActions(steam)
        composeRule.resetWindowFocus(window)

        composeRule.onNodeWithText("Add to home screen").performClick()
        composeRule.waitForIdle()

        assertThat(chosen).containsExactly(AppAction.ADD_TO_HOME_SCREEN)
        composeRule.onNodeWithText(QUIT_AND_START).assertDoesNotExist()
        tile(steam).assertIsFocused()
    }

    @Test
    fun `closing the details gives the focus back to the app that opened the actions`() {
        show()
        tile(steam).requestFocus()
        openActions(steam)
        composeRule.onNodeWithText("Details").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
        composeRule.resetWindowFocus(window)

        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Close").assertDoesNotExist()
        tile(steam).assertIsFocused()
    }

    @Test
    fun `confirming Quit and start gives the focus back to the app that opened the actions`() {
        show()
        tile(steam).requestFocus()
        openActions(steam)
        composeRule.onNodeWithText(QUIT_AND_START).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").assertIsFocused()
        composeRule.resetWindowFocus(window)

        composeRule.onNodeWithText("Quit").performClick()
        composeRule.waitForIdle()

        assertThat(chosen).containsExactly(AppAction.QUIT_AND_START)
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        tile(steam).assertIsFocused()
    }

    @Test
    fun `after Hide the focus goes to the next tile when the app leaves after the sheet closed`() {
        offerHide = true
        show()

        hide(steam)
        // The ViewModel launches the removal: the app is still in the grid when the sheet closes.
        tile(steam).assertIsDisplayed()
        removeApp(steam)
        composeRule.waitForIdle()

        assertThat(chosen).containsExactly(AppAction.HIDE)
        tile(steam).assertDoesNotExist()
        tile(hades).assertIsFocused()
    }

    @Test
    fun `after Hide the focus goes to the next tile when the app leaves with the choice`() {
        offerHide = true
        removeOnHide = true
        show()

        hide(steam)

        assertThat(chosen).containsExactly(AppAction.HIDE)
        tile(steam).assertDoesNotExist()
        tile(hades).assertIsFocused()
    }

    @Test
    fun `after Hide of the last tile the focus goes to the previous tile`() {
        offerHide = true
        show()

        hide(hades)
        removeApp(hades)
        composeRule.waitForIdle()

        assertThat(chosen).containsExactly(AppAction.HIDE)
        tile(hades).assertDoesNotExist()
        tile(steam).assertIsFocused()
    }

    @Test
    fun `after Hide of the only app the screen has no tile left and stays usable`() {
        offerHide = true
        state.value = state.value.copy(items = listOf(steam))
        show()

        hide(steam)
        removeApp(steam)
        composeRule.waitForIdle()

        assertThat(chosen).containsExactly(AppAction.HIDE)
        tile(steam).assertDoesNotExist()
        composeRule.onNodeWithText(HIDE).assertDoesNotExist()
    }

    @Test
    fun `after Hide with the hidden apps shown the focus stays on the tile that is still there`() {
        offerHide = true
        state.value = state.value.copy(showHidden = true)
        show()

        hide(steam)
        // The app stays in the grid, dimmed: showing the hidden apps keeps what Hide hides.
        state.value =
            state.value.copy(
                items = state.value.items.map { if (it == steam) it.copy(hidden = true) else it }
            )
        composeRule.waitForIdle()

        assertThat(chosen).containsExactly(AppAction.HIDE)
        tile(steam).assertIsFocused()
    }

    private fun tile(app: AppItemUiState): SemanticsNodeInteraction =
        composeRule.onNodeWithContentDescription(app.name)

    /** X on a focused tile opens the actions of its app; [shown] is a text of its sheet. */
    private fun openActions(app: AppItemUiState, shown: String = QUIT_AND_START) {
        tile(app).performKeyInput { pressKey(Key.ButtonX) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(shown).assertIsDisplayed()
    }

    /**
     * The actions of [app], Hide, and the sheet closed, as on the device: the window that opens
     * over the screen takes the focus.
     */
    private fun hide(app: AppItemUiState) {
        tile(app).requestFocus()
        openActions(app, HIDE)
        composeRule.resetWindowFocus(window)

        composeRule.onNodeWithText(HIDE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(HIDE).assertDoesNotExist()
    }

    private fun removeApp(app: AppItemUiState) {
        state.value = state.value.copy(items = state.value.items.filter { it.id != app.id })
    }

    /** The screen over a state that the callbacks change as the ViewModel does. */
    private fun show() {
        composeRule.setContentInKeyboardMode {
            window = LocalView.current
            Screen()
        }
    }

    @Composable
    private fun Screen() {
        AppsScreen(
            state = state.value,
            formFactor = FormFactor.Tv,
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onAppSelected = { id ->
                val app = state.value.items.first { it.id == id }
                if (!app.running) state.value = state.value.copy(actions = sheetFor(app))
            },
            onActions = { id ->
                val app = state.value.items.first { it.id == id }
                state.value = state.value.copy(actions = sheetFor(app))
            },
            onActionChosen = { action ->
                chosen += action
                val app = checkNotNull(state.value.actions).app
                state.value =
                    state.value.copy(
                        actions = null,
                        details = app.takeIf { action == AppAction.DETAILS },
                    )
                if (action == AppAction.HIDE && removeOnHide) removeApp(app)
            },
            onActionsDismissed = { state.value = state.value.copy(actions = null) },
            onDetailsDismissed = { state.value = state.value.copy(details = null) },
            onToggleShowHidden = {},
        )
    }

    /** What the ViewModel offers for an app while Desktop runs. */
    private fun sheetFor(app: AppItemUiState) =
        AppActionsUiState(
            app,
            desktop.name,
            if (app.running) {
                listOf(AppAction.RESUME, AppAction.QUIT, AppAction.DETAILS)
            } else {
                listOf(AppAction.QUIT_AND_START, AppAction.DETAILS, AppAction.ADD_TO_HOME_SCREEN) +
                    listOfNotNull(AppAction.HIDE.takeIf { offerHide })
            },
            runningAppIsDesktop = true,
        )

    private companion object {
        const val QUIT_AND_START = "Quit Desktop and start Steam Big Picture"
        const val HIDE = "Hide from this list"
    }
}
