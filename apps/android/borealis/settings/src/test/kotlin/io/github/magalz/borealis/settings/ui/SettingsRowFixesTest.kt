package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.options.DisabledReason
import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.RowState
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** How a row shows what is stored, and what the screen asks of the device when it opens. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class SettingsRowFixesTest : SettingsScreenTestBase() {
    @Test
    fun `HDR on a broken firmware shows off even when on is stored`() {
        stored(Settings.ENABLE_HDR to true)
        options.rowStates[Settings.ENABLE_HDR] =
            RowState.Disabled(DisabledReason.HDR_BROKEN_ON_FIRMWARE)

        show()

        row("HDR")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.Off,
                )
            )
        assertThat(snapshot[Settings.ENABLE_HDR]).isTrue()
    }

    @Test
    fun `HDR that another setting disables still shows what is stored`() {
        stored(Settings.ENABLE_HDR to true)
        options.rowStates[Settings.ENABLE_HDR] =
            RowState.Disabled(DisabledReason.DEPENDS_ON_SETTING, Settings.FULL_RANGE)

        show()

        row("HDR")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.On,
                )
            )
    }

    @Test
    fun `a stored frame rate that is not in the list shows as a number of fps`() {
        val viewModel = viewModel()
        val state =
            viewModel.uiState.value.let {
                it.copy(
                    snapshot = SettingsSnapshot.DEFAULTS.with(Settings.FRAME_RATE, "144"),
                    frameRateChoices =
                        listOf(FrameRateChoice(30, false), FrameRateChoice(60, false)),
                )
            }

        composeRule.setContent {
            SettingsScreen(
                state = state,
                formFactor = FormFactor.Phone,
                actions = SettingsActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, {}),
                onBack = {},
                onOpenPrivacy = {},
                privacyPane = {},
                about = testAbout(),
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("144 fps").assertExists()
        composeRule.onNodeWithText("144").assertDoesNotExist()
    }

    @Test
    fun `the device facts are asked once when the screen opens`() {
        show()

        assertThat(facts.calls).isEqualTo(1)
    }

    @Test
    fun `the Version row cannot be clicked`() {
        show()

        row("Version").assertHasNoClickAction()
        composeRule.onNodeWithText("9.8.7 (commit abc1234)").assertExists()
    }
}

/** The screen keeps the selected group across a recreation of its host. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class SettingsRestorationTest : SettingsScreenTestBase() {
    @Before fun theDeviceIsATelevision() = asTelevision()

    @Test
    fun `the selected group survives a recreation`() {
        val restoration = StateRestorationTester(composeRule)
        val viewModel = viewModel()
        restoration.setContent {
            SettingsRoute(
                onBack = {},
                onOpenPrivacy = {},
                privacyPane = {},
                about = testAbout(),
                viewModel = viewModel,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("About").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Source code").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Source code").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }
}
