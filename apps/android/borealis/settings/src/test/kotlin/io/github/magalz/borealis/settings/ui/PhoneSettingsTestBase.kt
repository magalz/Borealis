package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.robolectric.RuntimeEnvironment

/**
 * The titles of the sections of the phone list, as the headers show them: every group in capitals.
 * Privacy and diagnostics is a row of the list, not a section.
 */
internal val PHONE_SECTIONS: List<String> by lazy {
    val resources = RuntimeEnvironment.getApplication().resources
    SettingsGroupId.entries
        .filter { it != SettingsGroupId.PRIVACY }
        .map { resources.getString(it.titleRes).uppercase() }
}

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
    protected fun header(title: String): SemanticsNodeInteraction = row(title)

    protected fun state(value: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
}
