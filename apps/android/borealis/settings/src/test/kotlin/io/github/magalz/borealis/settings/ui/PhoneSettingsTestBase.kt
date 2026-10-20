package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/** The titles of the sections of the phone list, as the headers show them. */
internal val PHONE_SECTIONS =
    listOf(
        "STREAM",
        "VIDEO",
        "INPUT",
        "GAMEPAD",
        "ON-SCREEN CONTROLS",
        "AUDIO",
        "ADVANCED",
        "APPEARANCE",
        "ACCESSIBILITY",
        "ABOUT",
    )

/** What the tests of the phone list share: its sections start collapsed and open by a tap. */
open class PhoneSettingsTestBase : SettingsScreenTestBase() {
    protected fun top(title: String): Float =
        composeRule.onNodeWithText(title).getUnclippedBoundsInRoot().top.value

    /** Shows the screen with every section open, for the tests of what the rows show and do. */
    protected fun showOpen(about: AboutSource? = testAbout()) {
        show(about)
        PHONE_SECTIONS.filter {
                composeRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
            }
            .forEach {
                header(it).performClick()
                composeRule.waitForIdle()
            }
    }

    /** The header of a section, scrolled into view. */
    protected fun header(title: String): SemanticsNodeInteraction =
        composeRule.onNodeWithText(title).also { it.performScrollTo() }

    protected fun state(value: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
}
