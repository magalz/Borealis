package io.github.magalz.borealis.settings.ui

import android.content.pm.PackageInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.options.DisabledReason
import io.github.magalz.borealis.settings.options.EntryOrientation
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.ResolutionKind
import io.github.magalz.borealis.settings.options.RowState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The settings screen on a phone: the rows of its sections (UC-009). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class SettingsPhoneTest : PhoneSettingsTestBase() {
    @Test
    fun `the list has the groups with their rows in UC-009 order`() {
        showOpen()

        val titles =
            listOf(
                "STREAM",
                "Resolution",
                "Frame rate",
                "Bitrate",
                "Frame pacing",
                "VIDEO",
                "Codec",
                "HDR",
                "Full range",
                "Performance overlay",
                "Stretch video",
                "INPUT",
                "Touch",
                "Deadzone",
                "GAMEPAD",
                "Multiple controllers",
                "Vibration strength",
                "ON-SCREEN CONTROLS",
                "Show on-screen controls",
                "Reset layout",
                "AUDIO",
                "Channels",
                "ADVANCED",
                "Optimise game settings",
                "Picture-in-picture",
                "APPEARANCE",
                "Font",
                "Accent colour",
                "Background",
                "Background intensity",
                "Card density",
                "Glass tint",
                "Glass blur",
                "ACCESSIBILITY",
                "Reduce motion",
                "Transparency",
                "Thicker focus outline",
                "Text size",
                "Text spacing",
                "High contrast",
                "Colour-blind palette",
                "Privacy and diagnostics",
                "ABOUT",
                "Version",
                "Source code",
                "Open-source licences",
                "Privacy policy",
            )
        val tops = titles.map { top(it) }
        assertThat(tops).isInStrictOrder()
    }

    @Test
    fun `a row that the device cannot use does not appear`() {
        options.rowStates[Settings.ENABLE_HDR] = RowState.Hidden

        showOpen()

        composeRule.onNodeWithText("Full range").assertExists()
        composeRule.onNodeWithText("HDR").assertDoesNotExist()
    }

    @Test
    fun `without a touchscreen the On-screen controls group is gone`() {
        options.onScreenControlsGroup = RowState.Hidden
        options.resetLayout = RowState.Hidden

        showOpen()

        composeRule.onNodeWithText("ON-SCREEN CONTROLS").assertDoesNotExist()
        composeRule.onNodeWithText("Show on-screen controls").assertDoesNotExist()
        composeRule.onNodeWithText("Reset layout").assertDoesNotExist()
        composeRule.onNodeWithText("AUDIO").assertExists()
    }

    @Test
    fun `Privacy and diagnostics is a link row that opens its screen`() {
        show()

        row("Privacy and diagnostics").performClick()

        assertThat(privacyOpens).isEqualTo(1)
        composeRule.onNodeWithText("Privacy pane placeholder").assertDoesNotExist()
    }

    @Test
    fun `each row shows its current value`() {
        stored(
            Settings.FRAME_PACING to "balanced",
            Settings.DEADZONE to 9,
            Settings.OSC_OPACITY to 60,
            Settings.VIBRATE_FALLBACK_STRENGTH to 120,
        )

        showOpen()

        row("Resolution")
        composeRule.onNodeWithText("720p (1280x720)").assertExists()
        composeRule.onNodeWithText("60 fps").assertExists()
        composeRule.onNodeWithText("Balanced").assertExists()
        composeRule.onNodeWithText("Automatic").assertExists()
        composeRule.onNodeWithText("Trackpad").assertExists()
        // The Performance overlay and the Colour-blind palette are both Off.
        composeRule.onAllNodesWithText("Off").assertCountEquals(2)
        composeRule.onNodeWithText("Stereo (2.0)").assertExists()
        composeRule.onNodeWithText("9 %").assertExists()
        composeRule.onNodeWithText("60 %").assertExists()
        composeRule.onNodeWithText("120 %").assertExists()
    }

    @Test
    fun `the Appearance group has the Background row with the seven styles`() {
        showOpen()

        row("Background").performClick()
        composeRule.waitForIdle()

        BACKGROUND_NAMES.forEach { sheetOption(it).assertExists() }
    }

    @Test
    fun `choosing a style stores it and the row shows it`() {
        showOpen()
        composeRule.onNodeWithText("Aurora curtains").assertExists()

        row("Background").performClick()
        composeRule.waitForIdle()
        sheetOption("Light mist").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.BACKGROUND]).isEqualTo("light_mist")
        composeRule.onNodeWithText("Light mist").assertExists()
        composeRule.onNodeWithText("Aurora curtains").assertDoesNotExist()
    }

    @Test
    fun `a stored style that is unknown shows the default as the chosen one`() {
        stored(Settings.BACKGROUND to "bogus")

        showOpen()

        composeRule.onNodeWithText("Aurora curtains").assertExists()
        composeRule.onNodeWithText("bogus").assertDoesNotExist()
    }

    @Test
    fun `an automatic bitrate shows the value computed from the resolution and the frame rate`() {
        showOpen()

        composeRule.onNodeWithText("Automatic (10 Mbps)").assertExists()
    }

    @Test
    fun `a stored bitrate shows in megabits`() {
        stored(Settings.BITRATE_KBPS to 12_500)

        showOpen()

        composeRule.onNodeWithText("12.5 Mbps").assertExists()
        composeRule.onNodeWithText("Automatic (10 Mbps)").assertDoesNotExist()
    }

    @Test
    fun `a stored bitrate in whole megabits has no decimals`() {
        stored(Settings.BITRATE_KBPS to 20_000)

        showOpen()

        composeRule.onNodeWithText("20 Mbps").assertExists()
    }

    @Test
    fun `a switch toggles through the repository and shows it`() {
        showOpen()

        row("Stretch video").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.STRETCH_VIDEO]).isTrue()
        row("Stretch video")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.On,
                )
            )

        row("Stretch video").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.STRETCH_VIDEO]).isFalse()
    }

    @Test
    fun `a row disabled by another setting shows why and cannot be changed`() {
        options.rowStates[Settings.ANALOG_SCROLLING] =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.MOUSE_EMULATION)
        options.rowStates[Settings.VIBRATE_FALLBACK_STRENGTH] =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.VIBRATE_FALLBACK)

        showOpen()

        row("Analog scrolling").assertIsNotEnabled()
        composeRule.onNodeWithText("Turn on Gamepad mouse first").assertExists()
        composeRule.onNodeWithText("Turn on Vibrate fallback first").assertExists()
        row("Analog scrolling").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Left stick").assertDoesNotExist()
        assertThat(repository.writes).isEmpty()
    }

    @Test
    fun `HDR on the broken firmware shows the firmware note`() {
        options.rowStates[Settings.ENABLE_HDR] =
            RowState.Disabled(DisabledReason.HDR_BROKEN_ON_FIRMWARE)

        showOpen()

        row("HDR").assertIsNotEnabled()
        composeRule
            .onNodeWithText(
                "HDR crashes on the firmware of this device. Update the firmware to use it."
            )
            .assertExists()
    }

    @Test
    fun `a list opens a choice sheet with its entries and the chosen one is stored`() {
        showOpen()

        row("Frame pacing").performClick()
        composeRule.waitForIdle()

        sheetOption("Lowest latency").assertIsDisplayed()
        sheetOption("Balanced").assertIsDisplayed()
        sheetOption("Balanced with fps limit").assertIsDisplayed()
        sheetOption("Smoothest video").assertIsDisplayed()

        sheetOption("Smoothest video").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.FRAME_PACING]).isEqualTo("smoothness")
        sheetOption("Lowest latency").assertDoesNotExist()
        composeRule.onNodeWithText("Smoothest video").assertExists()
    }

    @Test
    fun `the codec list stores its choice`() {
        showOpen()

        row("Codec").performClick()
        composeRule.waitForIdle()
        sheetOption("AV1").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.VIDEO_FORMAT]).isEqualTo("forceav1")
    }

    @Test
    fun `the performance overlay list has Off, Compact and Detailed`() {
        showOpen()

        row("Performance overlay").performClick()
        composeRule.waitForIdle()
        sheetOption("Off").assertIsDisplayed()
        sheetOption("Compact").assertIsDisplayed()
        sheetOption("Detailed").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.PERF_OVERLAY]).isEqualTo(Settings.PERF_OVERLAY_DETAILED)
    }

    @Test
    fun `Touch is a list of Trackpad and Direct`() {
        showOpen()

        row("Touch").performClick()
        composeRule.waitForIdle()
        sheetOption("Direct").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.TOUCHSCREEN_TRACKPAD]).isFalse()
        composeRule.onNodeWithText("Direct").assertExists()
    }

    @Test
    fun `Analog scrolling and Channels store their choices`() {
        showOpen()

        row("Analog scrolling").performClick()
        composeRule.waitForIdle()
        sheetOption("Left stick").performClick()
        composeRule.waitForIdle()
        row("Channels").performClick()
        composeRule.waitForIdle()
        sheetOption("5.1 surround").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.ANALOG_SCROLLING]).isEqualTo("left")
        assertThat(snapshot[Settings.AUDIO_CONFIG]).isEqualTo("51")
    }

    @Test
    fun `the Resolution list has the offered entries and choosing one returns the bitrate to automatic`() {
        stored(Settings.BITRATE_KBPS to 30_000)
        showOpen()
        composeRule.onNodeWithText("30 Mbps").assertExists()

        row("Resolution").performClick()
        composeRule.waitForIdle()
        sheetOption("720p (1280x720)").assertIsDisplayed()
        sheetOption("1440p (2560x1440)").assertIsDisplayed()
        sheetOption("4K (3840x2160)").assertIsDisplayed()
        sheetOption("1080p (1920x1080)").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.RESOLUTION]).isEqualTo("1920x1080")
        assertThat(snapshot[Settings.BITRATE_KBPS]).isNull()
        composeRule.onNodeWithText("Automatic (20 Mbps)").assertExists()
    }

    @Test
    fun `the Frame rate list has the offered entries and choosing one returns the bitrate to automatic`() {
        stored(Settings.BITRATE_KBPS to 30_000)
        showOpen()

        row("Frame rate").performClick()
        composeRule.waitForIdle()
        sheetOption("30 fps").performClick()
        composeRule.waitForIdle()

        assertThat(snapshot[Settings.FRAME_RATE]).isEqualTo("30")
        assertThat(snapshot[Settings.BITRATE_KBPS]).isNull()
        composeRule.onNodeWithText("Automatic (5 Mbps)").assertExists()
    }

    @Test
    fun `native resolutions are labelled by their kind and orientation`() {
        options.resolutions =
            STANDARD_RESOLUTIONS.take(1) +
                listOf(
                    ResolutionChoice("2400x1080", 2400, 1080, ResolutionKind.NATIVE),
                    ResolutionChoice(
                        "1080x1080",
                        1080,
                        1080,
                        ResolutionKind.NATIVE,
                        EntryOrientation.PORTRAIT,
                    ),
                    ResolutionChoice(
                        "2400x1200",
                        2400,
                        1200,
                        ResolutionKind.NATIVE_FULLSCREEN,
                        EntryOrientation.LANDSCAPE,
                    ),
                    ResolutionChoice("2560x1200", 2560, 1200, ResolutionKind.NATIVE_FULLSCREEN),
                )
        showOpen()

        row("Resolution").performClick()
        composeRule.waitForIdle()

        sheetOption("Native (2400x1080)").assertExists()
        sheetOption("Native (portrait) (1080x1080)").assertExists()
        sheetOption("Native fullscreen (landscape) (2400x1200)").assertExists()
        sheetOption("Native fullscreen (2560x1200)").assertExists()
    }

    @Test
    fun `choosing a native resolution warns that the PC must support it`() {
        options.resolutions =
            STANDARD_RESOLUTIONS.take(1) +
                ResolutionChoice("2400x1080", 2400, 1080, ResolutionKind.NATIVE)
        showOpen()

        row("Resolution").performClick()
        composeRule.waitForIdle()
        sheetOption("Native (2400x1080)").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Native resolution").assertIsDisplayed()
        composeRule
            .onNodeWithText("The PC must support this mode.", substring = true)
            .assertIsDisplayed()
        assertThat(snapshot[Settings.RESOLUTION]).isEqualTo("2400x1080")

        composeRule.onNodeWithText("Keep").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Native resolution").assertDoesNotExist()
        assertThat(snapshot[Settings.RESOLUTION]).isEqualTo("2400x1080")
    }

    @Test
    fun `Change in the native warning opens the list again`() {
        options.resolutions =
            STANDARD_RESOLUTIONS.take(1) +
                ResolutionChoice("2400x1080", 2400, 1080, ResolutionKind.NATIVE)
        showOpen()
        row("Resolution").performClick()
        composeRule.waitForIdle()
        sheetOption("Native (2400x1080)").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Change").performClick()
        composeRule.waitForIdle()

        sheetOption("720p (1280x720)").assertIsDisplayed()
        composeRule.onNodeWithText("Native resolution").assertDoesNotExist()
    }

    @Test
    fun `choosing a standard resolution shows no warning`() {
        showOpen()

        row("Resolution").performClick()
        composeRule.waitForIdle()
        sheetOption("1080p (1920x1080)").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Native resolution").assertDoesNotExist()
    }

    @Test
    fun `choosing the native frame rate warns that the PC must support it`() {
        options.frameRates = options.frameRates + FrameRateChoice(144, isNative = true)
        showOpen()

        row("Frame rate").performClick()
        composeRule.waitForIdle()
        sheetOption("Native (144 fps)").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Native frame rate").assertIsDisplayed()
        assertThat(snapshot[Settings.FRAME_RATE]).isEqualTo("144")
    }

    @Test
    fun `Back from a choice sheet changes nothing`() {
        showOpen()
        row("Frame pacing").performClick()
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(repository.writes).isEmpty()
        assertThat(backs).isEqualTo(0)
        sheetOption("Smoothest video").assertDoesNotExist()
    }

    @Test
    fun `turning on Unlock all frame rates adds 90 and 120 to the Frame rate list at once`() {
        showOpen()
        row("Frame rate").performClick()
        composeRule.waitForIdle()
        sheetOption("90 fps").assertDoesNotExist()
        Espresso.pressBack()
        composeRule.waitForIdle()

        row("Unlock all frame rates").performClick()
        composeRule.waitForIdle()
        row("Frame rate").performClick()
        composeRule.waitForIdle()

        sheetOption("90 fps").assertIsDisplayed()
        sheetOption("120 fps").assertIsDisplayed()
    }

    @Test
    fun `the sliders store whole numbers in their stored unit`() {
        showOpen()

        setProgress("Bitrate", 20_000f)
        setProgress("Deadzone", 12f)
        setProgress("Vibration strength", 150f)
        setProgress("Opacity", 40f)
        setProgress("Gamepad mouse speed", 160f)

        assertThat(snapshot[Settings.BITRATE_KBPS]).isEqualTo(20_000)
        assertThat(snapshot[Settings.DEADZONE]).isEqualTo(12)
        assertThat(snapshot[Settings.VIBRATE_FALLBACK_STRENGTH]).isEqualTo(150)
        assertThat(snapshot[Settings.OSC_OPACITY]).isEqualTo(40)
        assertThat(snapshot[Settings.GAMEPAD_MOUSE_SPEED]).isEqualTo(160)
        composeRule.onNodeWithText("20 Mbps").assertExists()
        composeRule.onNodeWithText("12 %").assertExists()
        composeRule.onNodeWithText("160 %").assertExists()
    }

    @Test
    fun `a bitrate set by touch moves in steps of half a megabit`() {
        showOpen()

        setProgress("Bitrate", 12_300f)

        assertThat(snapshot[Settings.BITRATE_KBPS]).isEqualTo(12_500)
    }

    @Test
    fun `the gamepad mouse speed starts at 50 percent`() {
        showOpen()

        row("Gamepad mouse speed").assertExists()
        composeRule.onNodeWithText("50 %").assertExists()
    }

    @Test
    fun `a slider that is disabled ignores input and shows why`() {
        options.rowStates[Settings.OSC_OPACITY] =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.SHOW_ONSCREEN_CONTROLS)

        showOpen()

        row("Opacity").assertIsNotEnabled()
        composeRule.onNodeWithText("Turn on Show on-screen controls first").assertExists()
    }

    @Test
    fun `Reset layout asks first, with Cancel leaving the layout alone`() {
        showOpen()

        row("Reset layout").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Reset the layout?").assertIsDisplayed()
        composeRule.onNodeWithText("Cannot be undone", substring = true).assertIsDisplayed()
        assertThat(resets).isEqualTo(0)

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Reset the layout?").assertDoesNotExist()
        assertThat(resets).isEqualTo(0)
    }

    @Test
    fun `confirming Reset layout resets it once`() {
        showOpen()

        row("Reset layout").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Reset").performClick()
        composeRule.waitForIdle()

        assertThat(resets).isEqualTo(1)
        composeRule.onNodeWithText("Reset the layout?").assertDoesNotExist()
    }

    @Test
    fun `Back from the Reset layout question cancels it`() {
        showOpen()
        row("Reset layout").performClick()
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(resets).isEqualTo(0)
        assertThat(backs).isEqualTo(0)
    }

    @Test
    fun `the Version row shows the version name and the commit`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app.packageManager)
            .installPackage(
                PackageInfo().apply {
                    packageName = app.packageName
                    versionName = "9.8.7"
                }
            )

        showOpen(about = null)

        composeRule.onNodeWithText("9.8.7 (commit unknown)").assertExists()
    }

    @Test
    fun `Source code opens its link`() {
        showOpen()

        row("Source code").performClick()

        assertThat(openedLinks).containsExactly("https://github.com/magalz/Borealis")
    }

    @Test
    fun `Privacy policy opens its link`() {
        showOpen()

        row("Privacy policy").performClick()

        assertThat(openedLinks)
            .containsExactly("https://github.com/magalz/Borealis/blob/main/PRIVACY.md")
    }

    @Test
    fun `with no browser a link shows its address in a dialog`() {
        browserAvailable = false
        showOpen()

        row("Source code").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No app can open this link").assertIsDisplayed()
        composeRule
            .onNode(hasText("https://github.com/magalz/Borealis") and hasAnyAncestor(isDialog()))
            .assertIsDisplayed()

        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No app can open this link").assertDoesNotExist()
    }

    @Test
    fun `Open-source licences shows the licence texts`() {
        showOpen()

        row("Open-source licences").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNode(hasText("The licence texts of the test", substring = true))
            .assertIsDisplayed()
    }

    @Test
    fun `a swipe scrolls the licence texts`() {
        licenses = (1..200).joinToString("\n") { "Licence line $it" }
        showOpen()
        row("Open-source licences").performClick()
        composeRule.waitForIdle()
        val text =
            composeRule.onNode(
                hasText("Licence line 1", substring = true) and hasAnyAncestor(isDialog())
            )

        text.performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        val scrolled =
            text.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertThat(scrolled).isGreaterThan(0f)
    }

    @Test
    fun `without licence texts the dialog says they are not in this build`() {
        licenses = null
        showOpen()

        row("Open-source licences").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("The licence texts are not included in this build.")
            .assertIsDisplayed()
    }

    @Test
    fun `Back leaves the screen`() {
        show()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun `the arrow beside the title leaves the screen, as Privacy has`() {
        show()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun `the arrow is before the title`() {
        show()

        val arrow = composeRule.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("Settings").getUnclippedBoundsInRoot()

        assertThat(arrow.right).isAtMost(title.left)
    }
}
