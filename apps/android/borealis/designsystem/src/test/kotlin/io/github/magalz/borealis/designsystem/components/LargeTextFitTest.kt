package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.settings.SettingsInfoRow
import io.github.magalz.borealis.designsystem.components.settings.SettingsSliderRow
import io.github.magalz.borealis.designsystem.components.settings.SettingsSwitchRow
import io.github.magalz.borealis.designsystem.components.settings.SettingsValueRow
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The components at a system font scale of 2.0, the largest that Android offers (WCAG 1.4.4: text
 * resizes to 200 percent without loss of content). Every text must fit where it is, or the
 * component grows: none runs past its width, is cut off at the bottom or ends in an ellipsis.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(fontScale = SCALE)
class LargeTextFitTest(font: FontChoice) {
    @get:Rule val composeRule = createComposeRule()

    private val look = BorealisLook(font = font)

    @Test
    @Config(qualifiers = TV)
    fun `a host card on a TV fits`() = assertFits(FormFactor.Tv) { Cards() }

    @Test
    @Config(qualifiers = PHONE)
    fun `a host card on a phone fits`() = assertFits(FormFactor.Phone) { Cards() }

    @Test
    @Config(qualifiers = PHONE)
    fun `the rows of a menu on a phone fit`() = assertFits(FormFactor.Phone) { MenuRows() }

    @Test
    @Config(qualifiers = TV)
    fun `the rows of a menu on a TV fit`() = assertFits(FormFactor.Tv) { MenuRows() }

    @Test
    @Config(qualifiers = PHONE)
    fun `the rows of the settings on a phone fit`() =
        assertFits(FormFactor.Phone) { SettingsRows() }

    @Test
    @Config(qualifiers = TV)
    fun `the rows of the settings on a TV fit`() = assertFits(FormFactor.Tv) { SettingsRows() }

    @Test
    @Config(qualifiers = PHONE)
    fun `a wrap of chips and buttons on a phone fit`() = assertFits(FormFactor.Phone) { Chips() }

    @Test
    @Config(qualifiers = PHONE)
    fun `a bottom sheet fits`() {
        composeRule.setContent { Sheet(FormFactor.Phone) }
        composeRule.waitForIdle()

        composeRule.assertNoClippedText()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a sheet that is a dialog fits`() {
        composeRule.setContent { Sheet(FormFactor.Tv) }
        composeRule.waitForIdle()

        composeRule.assertNoClippedText()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a dialog fits`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, look) {
                BorealisDialog(
                    title = "Forget this host?",
                    onDismissRequest = {},
                    confirm = DialogAction("Forget", BorealisButtonStyle.Danger) {},
                    dismiss = DialogAction("Cancel") {},
                ) {
                    androidx.compose.material3.Text(
                        "Its pairing is removed from this device. The PC may keep trusting it."
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.assertNoClippedText()
    }

    private fun assertFits(formFactor: FormFactor, content: @Composable () -> Unit) {
        composeRule.setContent { BorealisTheme(formFactor, look) { OnPage { content() } } }
        composeRule.waitForIdle()

        composeRule.assertNoClippedText()
    }

    @Composable
    private fun Cards() {
        // The two cards are in a list that scrolls, as they are on Hosts: stacked in a window of
        // 540dp that does not scroll, a TV at 200 percent runs out of height for the second card.
        Column(
            Modifier.width(360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HostCard(
                name = "Living Room PC",
                status = DotStatus.Online,
                statusText = "Online",
                onClick = {},
                detail = "192.168.1.31",
                tags = listOf("HEVC 10-bit", "HDR"),
                runningApp = "Steam Big Picture",
            )
            ContinueCard(
                appName = "Steam Big Picture",
                detail = "Running on Living Room PC",
                resumeLabel = "Resume",
                onClick = {},
            )
        }
    }

    @Composable
    private fun MenuRows() {
        Column(Modifier.width(333.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuRow("Keyboard", {}, consequence = "Opens the keyboard over the stream")
            MenuRow(
                "Gamepad mouse",
                {},
                value = "On",
                trailing = {
                    SegmentedControl(listOf(true, false), true, { if (it) "On" else "Off" }, {})
                },
            )
            MenuRow("Disconnect", {}, style = MenuRowStyle.Danger)
        }
    }

    @Composable
    private fun SettingsRows() {
        Column(Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsValueRow("Frame pacing", "Lowest latency", {}, summary = "Trades smoothness")
            SettingsSwitchRow("Thicker focus outline", false, {}, summary = "Draws an outline")
            SettingsSliderRow("Bitrate", 10f, 1f..150f, 1f, "10 Mbps", {})
            SettingsInfoRow("Version", "1.0.0 (abc1234)")
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Chips() {
        FlowRow(
            Modifier.width(333.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (key in listOf("Alt+Tab", "Ctrl+Shift+Esc", "Win+D")) Chip(key, onClick = {})
            BorealisButton("Add host", {})
            BorealisButton("Cancel", {}, style = BorealisButtonStyle.Secondary)
        }
    }

    @Composable
    private fun Sheet(formFactor: FormFactor) {
        BorealisTheme(formFactor, look) {
            ActionSheet(
                title = "Living Room PC",
                actions =
                    listOf(
                        ActionSheetAction(
                            "a",
                            "Details",
                            "Show the name, addresses and capabilities.",
                        ),
                        ActionSheetAction(
                            "f",
                            "Forget",
                            "Remove it from this device.",
                            ActionStyle.Danger,
                        ),
                    ),
                onAction = {},
                onDismiss = {},
            )
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fonts(): List<FontChoice> = FontChoice.entries

        private const val PHONE = "w360dp-h640dp-xhdpi"
        private const val TV = "w960dp-h540dp-land-television-xhdpi"
    }
}

private const val SCALE = 2f
