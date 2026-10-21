package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.components.BackdropTextHalo
import io.github.magalz.borealis.designsystem.testing.assertHalo
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The title of the Settings screen stands straight on the background, outside any glass, so it is
 * drawn with the halo (issue #33): on a TV above the two glass panels, on a phone in the top bar.
 * The groups in the panels are glass and have none. With no background there is no halo.
 */
@RunWith(AndroidJUnit4::class)
class SettingsBackdropTextTest : SettingsScreenTestBase() {
    @Test
    @Config(qualifiers = TV)
    fun `the title of the TV settings is on the background`() {
        asTelevision()
        show { content -> InGlassHost(FormFactor.Tv, content = content) }

        composeRule.assertHalo("Settings", drawn = true)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the groups in the glass panel of the TV settings have no halo`() {
        asTelevision()
        show { content -> InGlassHost(FormFactor.Tv, content = content) }

        composeRule
            .onAllNodes(
                hasText("Stream") and SemanticsMatcher.keyNotDefined(BackdropTextHalo),
                useUnmergedTree = true,
            )
            .onFirst()
            .assertExists()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the title in the bar of the phone settings is on the background`() {
        show { content -> InGlassHost(FormFactor.Phone, content = content) }

        composeRule.assertHalo("Settings", drawn = true)
    }

    @Test
    @Config(qualifiers = TV)
    fun `with no background the title has no halo`() {
        asTelevision()
        show()

        composeRule.assertHalo("Settings", drawn = false)
    }
}
