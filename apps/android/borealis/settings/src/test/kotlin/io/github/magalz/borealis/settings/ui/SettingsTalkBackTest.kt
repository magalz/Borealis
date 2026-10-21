package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.headings
import io.github.magalz.borealis.designsystem.testing.paneTitles
import io.github.magalz.borealis.designsystem.testing.readingOrder
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader is told on Settings (WCAG 1.3.1, 2.4.3, 2.4.6 and 4.1.2): the screen is read
 * from the title and the list of groups to the rows of the open group, in the order that it is
 * drawn, the title and the sections of a phone are headings, and a sheet says what it is when it
 * opens.
 */
@RunWith(AndroidJUnit4::class)
class SettingsTalkBackTest : SettingsScreenTestBase() {
    private val groups =
        listOf(
            "Stream",
            "Video",
            "Input",
            "Gamepad",
            "On-screen controls",
            "Audio",
            "Advanced",
            "Appearance",
            "Accessibility",
            "Privacy and diagnostics",
            "About",
        )

    private fun showSettings(formFactor: FormFactor, group: SettingsGroupId) {
        if (formFactor == FormFactor.Tv) asTelevision()
        show(keyboard = formFactor != FormFactor.Phone, initialGroup = group) { content ->
            InGlassHost(formFactor, style = BackgroundStyle.Off, content = content)
        }
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the title is read first, then the groups top to bottom, then the rows`() {
        showSettings(FormFactor.Tv, SettingsGroupId.ACCESSIBILITY)

        val order = composeRule.readingOrder()
        val places =
            (listOf("Settings") + groups + "Reduce motion").map { marker ->
                order.indexOfFirst { it.startsWith(marker) }
            }
        assertWithMessage(order.joinToString(" // "))
            .that(places.all { it >= 0 } && places.zipWithNext().all { (a, b) -> a < b })
            .isTrue()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the title of the screen is a heading`() {
        showSettings(FormFactor.Tv, SettingsGroupId.STREAM)

        assertThat(composeRule.headings()).containsExactly("Settings")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the title and every section are headings`() {
        showSettings(FormFactor.Phone, SettingsGroupId.STREAM)

        assertThat(composeRule.headings())
            .containsAtLeast("Settings", "STREAM", "APPEARANCE", "ACCESSIBILITY", "ABOUT")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the sections are read in the order that they are drawn`() {
        showSettings(FormFactor.Phone, SettingsGroupId.STREAM)
        composeRule.onAllNodesWithText("STREAM")[0].performScrollTo().performClick()
        composeRule.waitForIdle()

        val order = composeRule.readingOrder()
        val places =
            listOf("Settings", "STREAM", "Resolution", "VIDEO", "INPUT").map { marker ->
                order.indexOfFirst { it.startsWith(marker) }
            }
        assertWithMessage(order.joinToString(" // "))
            .that(places.all { it >= 0 } && places.zipWithNext().all { (a, b) -> a < b })
            .isTrue()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a choice sheet says what it is when it opens`() {
        showSettings(FormFactor.Tv, SettingsGroupId.STREAM)
        composeRule.onNodeWithText("Frame pacing").performClick()
        composeRule.waitForIdle()

        assertThat(composeRule.paneTitles()).contains("Frame pacing")
        assertThat(composeRule.headings()).contains("Frame pacing")
    }
}
