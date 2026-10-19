package io.github.magalz.borealis.library.apps

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.testing.settleWindows
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.library.shell.LibraryScaffold
import io.github.magalz.borealis.library.shell.RailActions
import io.github.magalz.borealis.library.shell.RailSelection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the Apps screen and its actions sheet in the states of the Phase 2 proposal; the
 * references live in `src/test/screenshots`, named `<screen>_<device>_<state>.png`. The apps are
 * the ones of the proposal captures. The TV and handheld screens are shown with the navigation rail
 * that the shell draws beside them.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class AppsScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val names =
        listOf(
            "Desktop",
            "Steam Big Picture",
            "Playnite",
            "Hades II",
            "Forza Horizon 5",
            "Elden Ring",
            "Balatro",
            "Cities: Skylines",
            "Stardew Valley",
            "Remote Desktop",
        )

    private fun apps(runningFirst: Boolean, hiddenName: String? = null) =
        names.mapIndexed { index, name ->
            AppItemUiState(
                id = index + 1,
                name = name,
                running = runningFirst && index == 0,
                hidden = name == hiddenName,
                hdrSupported = index % 2 == 0,
            )
        }

    private fun state(
        runningFirst: Boolean,
        hiddenName: String? = null,
        actions: AppActionsUiState? = null,
    ) =
        AppsUiState(
            loading = false,
            hostName = "Desk PC",
            runningAppId = 1.takeIf { runningFirst },
            runningAppName = "Desktop".takeIf { runningFirst },
            showHidden = hiddenName != null,
            items = apps(runningFirst, hiddenName),
            actions = actions,
        )

    @Test
    @Config(qualifiers = PHONE)
    fun appsPhoneDesktopRunning() =
        shoot("apps_phone_desktop-running", FormFactor.Phone, state(runningFirst = true))

    @Test
    @Config(qualifiers = PHONE)
    fun appsPhoneNothingRunning() =
        shoot(
            "apps_phone_nothing-running",
            FormFactor.Phone,
            state(runningFirst = false, hiddenName = "Balatro"),
        )

    @Test
    @Config(qualifiers = TV)
    fun appsTvDesktopRunning() =
        shoot("apps_tv_desktop-running", FormFactor.Tv, state(runningFirst = true))

    @Test
    @Config(qualifiers = TV)
    fun appsTvNothingRunning() =
        shoot(
            "apps_tv_nothing-running",
            FormFactor.Tv,
            state(runningFirst = false, hiddenName = "Balatro"),
        )

    @Test
    @Config(qualifiers = HANDHELD)
    fun appsHandheldDesktopRunning() =
        shoot("apps_handheld_desktop-running", FormFactor.Handheld, state(runningFirst = true))

    @Test
    @Config(qualifiers = PHONE)
    fun appActionsPhoneRunning() =
        shoot("appactions_phone_running", FormFactor.Phone, runningSheet())

    @Test
    @Config(qualifiers = PHONE)
    fun appActionsPhoneOtherRunning() =
        shoot("appactions_phone_other-running", FormFactor.Phone, otherRunningSheet())

    @Test
    @Config(qualifiers = TV)
    fun appActionsTvRunning() = shoot("appactions_tv_running", FormFactor.Tv, runningSheet())

    @Test
    @Config(qualifiers = TV)
    fun appActionsTvOtherRunning() =
        shoot("appactions_tv_other-running", FormFactor.Tv, otherRunningSheet())

    @Test
    @Config(qualifiers = TV)
    fun appActionsTvDetails() =
        shoot(
            "appactions_tv_details",
            FormFactor.Tv,
            state(runningFirst = true).copy(details = apps(runningFirst = true).first()),
        )

    /** The confirmation of Quit for Desktop: the question, then what it costs. */
    @Test
    @Config(qualifiers = PHONE)
    fun appActionsPhoneQuitConfirm() =
        shoot("appactions_phone_quit-confirm", FormFactor.Phone, runningSheet()) {
            composeRule.onNodeWithText("Quit Desktop").performClick()
        }

    /** The same for a game: it closes and its unsaved progress is lost. */
    @Test
    @Config(qualifiers = PHONE)
    fun appActionsPhoneQuitConfirmGame() =
        shoot("appactions_phone_quit-confirm-game", FormFactor.Phone, gameRunningSheet()) {
            composeRule.onNodeWithText("Quit Steam Big Picture").performClick()
        }

    private fun gameRunningSheet(): AppsUiState {
        val steam = apps(runningFirst = false)[1].copy(running = true)
        return AppsUiState(
            loading = false,
            hostName = "Desk PC",
            runningAppId = steam.id,
            runningAppName = steam.name,
            items = apps(runningFirst = false).map { if (it.id == steam.id) steam else it },
            actions =
                AppActionsUiState(
                    steam,
                    steam.name,
                    listOf(AppAction.RESUME, AppAction.QUIT, AppAction.DETAILS),
                ),
        )
    }

    private fun runningSheet(): AppsUiState {
        val desktop = apps(runningFirst = true).first()
        return state(
            runningFirst = true,
            actions =
                AppActionsUiState(
                    desktop,
                    "Desktop",
                    listOf(AppAction.RESUME, AppAction.QUIT, AppAction.DETAILS),
                    runningAppIsDesktop = true,
                ),
        )
    }

    private fun otherRunningSheet(): AppsUiState {
        val hades = apps(runningFirst = true)[3]
        return state(
            runningFirst = true,
            actions =
                AppActionsUiState(
                    hades,
                    "Desktop",
                    listOf(AppAction.QUIT_AND_START, AppAction.DETAILS, AppAction.HIDE),
                    runningAppIsDesktop = true,
                ),
        )
    }

    /** [beforeShot] drives the screen, for instance to open the confirmation, once it is shown. */
    private fun shoot(
        name: String,
        formFactor: FormFactor,
        state: AppsUiState,
        beforeShot: () -> Unit = {},
    ) {
        composeRule.setContent {
            // The shell draws the rail beside Apps on a TV and a handheld.
            LibraryScaffold(
                formFactor,
                selected = RailSelection.Hosts,
                actions = RailActions({}, {}, {}),
            ) {
                AppsScreen(
                    state = state,
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
        }
        composeRule.waitForIdle()
        beforeShot()
        composeRule.waitForIdle()
        // The whole screen, not the root node: the sheet is a window of its own.
        composeRule.settleWindows()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        const val HANDHELD = "w800dp-h480dp-land-hdpi"
    }
}
