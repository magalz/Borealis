package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of every component in the states of the Phase 2 proposal; the references live in
 * `src/test/screenshots` and are named `<component>_<device>_<state>.png`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class ComponentScreenshotTest {
    @get:Rule val composeRule = createComposeRule()
    private val focus = FocusRequester()

    @Test
    @Config(qualifiers = PHONE)
    fun hostCardPhoneOnlinePaired() {
        composeRule.setContent {
            Page(FormFactor.Phone) {
                HostCard(
                    name = "Desk PC",
                    status = DotStatus.Online,
                    statusText = "Online",
                    // The detail lines of these specimens are the prototype copy, to show the
                    // layout:
                    // they are not data a host reports.
                    detail = "LuminalShine 26.09.1 · 192.168.1.20",
                    runningApp = "Desktop running",
                    onClick = {},
                )
            }
        }

        shoot("hostcard_phone_online-paired")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun hostCardPhoneTags() {
        composeRule.setContent {
            Page(FormFactor.Phone) {
                HostCard(
                    name = "Desk PC",
                    status = DotStatus.Online,
                    statusText = "Online",
                    detail = "192.168.1.20",
                    tags = listOf("HEVC 10-bit", "HDR", "4:4:4"),
                    onClick = {},
                )
            }
        }

        shoot("hostcard_phone_tags")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun hostCardPhoneOnlineUnpaired() {
        composeRule.setContent {
            Page(FormFactor.Phone) {
                HostCard(
                    name = "Studio",
                    status = DotStatus.Attention,
                    statusText = "Not paired",
                    detail = "LuminalShine 26.09.1 · found on this network",
                    onClick = {},
                )
            }
        }

        shoot("hostcard_phone_online-unpaired")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun hostCardPhoneOfflineWake() {
        composeRule.setContent {
            Page(FormFactor.Phone) {
                HostCard(
                    name = "Living Room PC",
                    status = DotStatus.Offline,
                    statusText = "Offline",
                    detail = "Last seen 2 days ago · 192.168.1.31",
                    onClick = {},
                )
            }
        }

        shoot("hostcard_phone_offline-wake")
    }

    @Test
    @Config(qualifiers = TV)
    fun hostCardTvFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HostCard(
                        name = "Desk PC",
                        status = DotStatus.Online,
                        statusText = "Online",
                        detail = "LuminalShine 26.09.1 · 192.168.1.20",
                        runningApp = "Desktop running",
                        onClick = {},
                        modifier = Modifier.width(220.dp).focusRequester(focus),
                    )
                    HostCard(
                        name = "Living Room PC",
                        status = DotStatus.Offline,
                        statusText = "Offline",
                        detail = "Last seen 2 days ago",
                        onClick = {},
                        modifier = Modifier.width(220.dp),
                    )
                }
            }
        }

        focusAndShoot("hostcard_tv_focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun continueCardTvFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) {
                ContinueCard(
                    appName = "Desktop",
                    detail = "Running on Desk PC · started 14 min ago",
                    resumeLabel = "Resume",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }

        focusAndShoot("continuecard_tv_focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun coverTileTvFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CoverTile(
                        "Desktop",
                        onClick = {},
                        modifier = Modifier.width(TILE_WIDTH).focusRequester(focus),
                    )
                    CoverTile(
                        "Steam Big Picture",
                        onClick = {},
                        modifier = Modifier.width(TILE_WIDTH),
                    )
                    CoverTile(
                        "Hades II",
                        onClick = {},
                        hidden = true,
                        modifier = Modifier.width(TILE_WIDTH),
                    )
                }
            }
        }

        focusAndShoot("covertile_tv_focused")
    }

    @Test
    @Config(qualifiers = TV)
    fun coverTileTvRunning() {
        composeRule.setContent {
            Page(FormFactor.Tv) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CoverTile(
                        "Desktop",
                        onClick = {},
                        runningLabel = "Running",
                        modifier = Modifier.width(TILE_WIDTH),
                    )
                    CoverTile("Playnite", onClick = {}, modifier = Modifier.width(TILE_WIDTH))
                }
            }
        }

        shoot("covertile_tv_running")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun actionSheetPhone() {
        composeRule.setContent { Page(FormFactor.Phone) { Sheet() } }
        composeRule.waitForIdle()

        captureScreenRoboImage("src/test/screenshots/actionsheet_phone.png")
    }

    @Test
    @Config(qualifiers = TV)
    fun actionSheetTv() {
        composeRule.setContent { Page(FormFactor.Tv) { Sheet() } }
        composeRule.waitForIdle()

        captureScreenRoboImage("src/test/screenshots/actionsheet_tv.png")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun pinPhone() {
        composeRule.setContent { Page(FormFactor.Phone) { PinDisplay("4817") } }

        shoot("pin_phone")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun textFieldPhoneDefault() {
        composeRule.setContent {
            Page(FormFactor.Phone) {
                BorealisTextField(value = "", onValueChange = {}, label = "Host address")
            }
        }

        shoot("textfield_phone_default")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun textFieldPhoneError() {
        composeRule.setContent {
            Page(FormFactor.Phone) {
                BorealisTextField(
                    value = "desk pc",
                    onValueChange = {},
                    label = "Host address",
                    error = "Enter an IP address or a host name",
                )
            }
        }

        shoot("textfield_phone_error")
    }

    @Test
    @Config(qualifiers = TV)
    fun railTv() {
        composeRule.setContentInKeyboardMode {
            BorealisTheme(FormFactor.Tv) {
                Box(Modifier.fillMaxSize().background(LocalBorealisColors.current.page)) {
                    NavigationRail(
                        items = listOf(RailItem("Home", ICON), RailItem("Settings", ICON)),
                        selectedIndex = 0,
                        onSelect = {},
                    )
                }
            }
        }

        // Keyboard mode focuses Home, the selected item; one step down shows a focused item that
        // is not selected.
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        shoot("rail_tv")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun buttonsPhone() {
        composeRule.setContent {
            Page(FormFactor.Phone) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BorealisButton("Resume", {}, style = BorealisButtonStyle.Primary)
                    BorealisButton("Wake", {}, style = BorealisButtonStyle.Secondary)
                    BorealisButton("Forget host", {}, style = BorealisButtonStyle.Danger)
                    BorealisButton("Cancel", {}, style = BorealisButtonStyle.Text)
                    BorealisButton("Disabled", {}, enabled = false)
                }
            }
        }

        shoot("buttons_phone")
    }

    @Test
    @Config(qualifiers = TV)
    fun buttonsTvFocused() {
        composeRule.setContentInKeyboardMode {
            Page(FormFactor.Tv) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    BorealisButton(
                        "Resume",
                        {},
                        modifier = Modifier.focusRequester(focus),
                        style = BorealisButtonStyle.Primary,
                    )
                    BorealisButton("Wake", {}, style = BorealisButtonStyle.Secondary)
                    BorealisButton("Forget host", {}, style = BorealisButtonStyle.Danger)
                    BorealisButton("Cancel", {}, style = BorealisButtonStyle.Text)
                }
            }
        }

        focusAndShoot("buttons_tv_focused")
    }

    @Composable
    private fun Sheet() {
        ActionSheet(
            title = "Desktop",
            actions =
                listOf(
                    ActionSheetAction("resume", "Resume", "Go back to the running desktop"),
                    ActionSheetAction("details", "Details", "Show codec, resolution and bitrate"),
                    ActionSheetAction(
                        "quit",
                        "Quit",
                        "Closes the app on the host and loses unsaved work",
                        ActionStyle.Danger,
                    ),
                ),
            onAction = {},
            onDismiss = {},
        )
    }

    @Composable
    private fun Page(formFactor: FormFactor, content: @Composable () -> Unit) {
        BorealisTheme(formFactor) {
            Box(
                Modifier.fillMaxSize()
                    .background(LocalBorealisColors.current.page)
                    .padding(if (formFactor == FormFactor.Phone) 16.dp else 34.dp)
            ) {
                content()
            }
        }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun focusAndShoot(name: String) {
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        val TILE_WIDTH = 128.dp
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        val ICON: ImageVector =
            ImageVector.Builder(
                    defaultWidth = 20.dp,
                    defaultHeight = 20.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                .path(fill = SolidColor(Color.White)) {
                    moveTo(4f, 11f)
                    lineTo(12f, 3f)
                    lineTo(20f, 11f)
                    lineTo(20f, 21f)
                    lineTo(4f, 21f)
                    close()
                }
                .build()
    }
}
